/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.fasterxml.jackson.module.kotlin.readValue
import net.postchain.rell.performance.chr.LocalChr
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlin.io.path.*

// 30 min covers install + build + test for the heaviest typical project (ft4-lib has 8+ test
// modules spread across two test chains). Individual projects can override this via
// `ProjectSpec.timeoutMinutes` when their suites legitimately need more — see mna-blockchain.
private val DEFAULT_PER_PROJECT_TIMEOUT: Duration = Duration.ofMinutes(30)

private fun ProjectSpec.stepTimeout(): Duration =
    timeoutMinutes?.let { Duration.ofMinutes(it.toLong()) } ?: DEFAULT_PER_PROJECT_TIMEOUT

// Pinned to the digest the .gitlab-ci.yml setup script uses for its DIND Postgres, so the
// regression toolkit exercises chr against the same server version CI mainline tests use.
private val POSTGRES_IMAGE = DockerImageName.parse(
    "postgres:16.14-alpine3.23" +
        "@sha256:20edbde7749f822887a1a022ad526fde0a47d6b2be9a8364433605cf65099416",
)

private const val POSTGRES_USER = "postchain"
private const val POSTGRES_PASSWORD = "postchain"
private const val POSTGRES_DB = "postchain"
private const val POSTGRES_PORT = 5432

// ─── Per-project pipeline ──────────────────────────────────────────────────────────────────────
//
// `runOneProject` runs the full install/build/test pipeline for ONE (project, backend); each
// invocation owns its throw-away Testcontainers Postgres so concurrent projects never share a
// schema. The chr binary is built upstream by `:performance:buildLocalChr`. The driver is the
// JUnit @TestFactory in test/regression/RegressionTest.kt — one DynamicTest per project, both
// backends looped sequentially inside the test body to avoid the chr-install race in the shared
// `src/lib/<name>` clone tree.

// ─── Per-backend environment ────────────────────────────────────────────────────────────────────

// chr's start script reads $JAVA_ARGS for JVM args; RellApiInterpreterBackend picks up the
// system property and reflectively loads the Truffle backend at run-time. Compilation is identical
// between backends, but the env is set uniformly so build and test logs reflect the same runtime.
private fun ExecutionBackend.extraEnv(): Map<String, String> = when (this) {
    ExecutionBackend.INTERPRETER -> emptyMap()
    ExecutionBackend.TRUFFLE -> mapOf("JAVA_ARGS" to "-Drell.execution.backend=truffle")
}

// ─── Filesystem layout for fragments and logs ─────────────────────────────────────────────────

internal fun partsDir(reportsDir: Path): Path = reportsDir / "parts"

private fun fragmentPath(reportsDir: Path, backend: ExecutionBackend, name: String): Path =
    partsDir(reportsDir) / "${backend.name.lowercase()}-$name.json"

private fun logFileFor(reportsDir: Path, backend: ExecutionBackend, name: String): Path =
    reportsDir / "logs" / backend.name.lowercase() / "$name.log"

private fun writeFragment(reportsDir: Path, backend: ExecutionBackend, result: CompileResult) {
    val path = fragmentPath(reportsDir, backend, result.name)
    path.parent.createDirectories()
    regressionWriter.writeValue(path.toFile(), result)
}

/**
 * Read every result fragment under reports/parts/ and assemble the combined [ResultsFile] the
 * report renders from. Replaces the single results.json that the old monolithic `compile` wrote;
 * with the fan-out each (project, backend) lands its own fragment and this stitches them back.
 */
internal fun mergeFragments(reportsDir: Path): ResultsFile {
    val dir = partsDir(reportsDir)
    val results = if (dir.isDirectory()) {
        dir.listDirectoryEntries("*.json")
            .sorted()
            .map { regressionMapper.readValue<CompileResult>(it.inputStream()) }
    } else {
        emptyList()
    }
    return ResultsFile(
        generatedAtEpochMs = System.currentTimeMillis(),
        rellVersion = readRellVersion(),
        results = results,
    )
}

// ─── Single-unit pipeline ─────────────────────────────────────────────────────────────────────

/** Mutable accumulator threaded through one project's chr-command pipeline. */
private class PipelineState {
    var nonTestDurationMs = 0L
    var testDurationMs = 0L
    var lastExit = 0
    var lastTimedOut = false
    var failedStep: List<String>? = null

    val totalDurationMs: Long get() = nonTestDurationMs + testDurationMs

    /** The pipeline keeps running only while the previous step exited cleanly. */
    val ok: Boolean get() = lastExit == 0 && !lastTimedOut
}

/** A chr step is "test" iff the first arg is `test` — covers `["test"]` and `["test", "--filter", ...]`. */
private fun List<String>.isTestStep(): Boolean = firstOrNull() == "test"

/** Outcome of locating + sanity-checking a project's clone before any chr step runs. */
private sealed interface Located

/** The clone is missing/unusable — [result] is terminal and there is nothing to build or test. */
private class Unusable(val result: CompileResult) : Located

/** The clone is present and has a chromia.yml — proceed with build/test from [rellDir]. */
private class Usable(val sha: String, val rellDir: Path) : Located

/**
 * Resolve a project's clone to a usable Rell directory, or a terminal [CompileResult] explaining
 * why not. Pure with respect to the filesystem apart from reading — safe to call from both the
 * build and test phases (the test phase re-derives the same context the build phase saw).
 */
private fun locate(project: ProjectSpec, workdir: Path, backend: ExecutionBackend): Located {
    val target = workdir / project.name
    val sha = readGitSha(target)
        ?: return Unusable(baseResult(project, Status.CLONE_FAILED).copy(executionBackend = backend))

    val rellDir = (target / project.rellPath).normalize()
    if (!rellDir.exists()) {
        return Unusable(baseResult(project, Status.CLONE_FAILED).copy(sha = sha, executionBackend = backend))
    }

    // Pre-check: chr install / chr build both fail mechanically with "library not found in null"
    // when there's no chromia.yml in the rell dir. That's a tool-incompatibility, not a Rell
    // compiler regression — short-circuit so the report shows a clean note instead of a wall of
    // chr usage text. Projects that pre-date the chromia.yml era live here.
    val chromiaYml = rellDir / "chromia.yml"
    if (!chromiaYml.exists()) {
        return Unusable(
            baseResult(project, Status.EXPECTED_FAIL).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "no chromia.yml at $rellDir; project pre-dates chromia.yml era " +
                    "and is incompatible with the current chr CLI — not a Rell compiler regression",
                executionBackend = backend,
            ),
        )
    }

    return Usable(sha, rellDir)
}

/**
 * Run one (project, backend) end-to-end: validate the clone, apply patches, spin up a throw-away
 * Postgres for the project's lifetime, then run every chr command in order and write the result
 * fragment. Safe to run concurrently with other projects — each invocation owns its own Postgres
 * container, log file, and fragment. The Postgres is recreated per project so suites never see
 * leftover state from a sibling run.
 */
fun runOneProject(project: ProjectSpec, workdir: Path, reportsDir: Path, backend: ExecutionBackend) {
    partsDir(reportsDir).createDirectories()
    val tag = "[${backend.label()}] ${project.name}"

    val chrBin = LocalChr.chrExecutable(repoRoot())
    val located = locate(project, workdir, backend)
    if (located is Unusable) {
        log("run-one", "$tag — ${located.result.status} (clone not usable; see notes)")
        writeFragment(reportsDir, backend, located.result)
        return
    }
    val (sha, rellDir) = (located as Usable).let { it.sha to it.rellDir }

    // Apply declared patches (e.g. rewrite stricter-than-allowed chromia.yml strings). Applied
    // before every chr step so they survive `regressionClone`'s git reset on the next sweep.
    val patchFailure = applyPatches(project, workdir / project.name)
    if (patchFailure != null) {
        log("run-one", "$tag — FAILED (patch error: $patchFailure)")
        writeFragment(
            reportsDir, backend,
            baseResult(project, Status.FAILED).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "patch failed before chr: $patchFailure",
                executionBackend = backend,
            ),
        )
        return
    }

    val logFile = logFileFor(reportsDir, backend, project.name)
    logFile.parent.createDirectories()
    logFile.deleteIfExists()

    val state = PipelineState()

    withProjectPostgres(tag) { dbEnv ->
        appendingWriter(logFile).use { w ->
            writeLogHeader(w, project, sha, rellDir)
            runSteps(
                w,
                project.commands,
                chrBin,
                rellDir,
                logFile,
                tag,
                state,
                backend.extraEnv() + dbEnv,
                project.stepTimeout(),
            )
        }
    }

    writeFragment(reportsDir, backend, finalize(project, backend, tag, sha, logFile, state))
}

/**
 * Spin a fresh PostgreSQL container for the lifetime of [block] and hand the chr-readable
 * connection env (`CHR_DB_URL`/`CHR_DB_USER`/`CHR_DB_PASSWORD`) to it. Stopped in a finally so a
 * thrown exception or process kill doesn't leak the container. PGDATA on tmpfs (capped at 512 MB
 * per container, well under the suites' actual fill) keeps startup at a few seconds and avoids
 * disk-IO contention when many of these run in parallel under Gradle's worker pool.
 *
 * Uses [GenericContainer] rather than the dedicated `PostgreSQLContainer`: the project's pinned
 * testcontainers (2.0.2, via postchain-bom) has no matching `:postgresql` submodule on Maven
 * Central — that helper still ships only on the 1.x branch. The half-dozen lines of env + port
 * wiring below are equivalent for our purposes.
 */
private fun withProjectPostgres(tag: String, block: (Map<String, String>) -> Unit) {
    val container = GenericContainer(POSTGRES_IMAGE)
        .withEnv("POSTGRES_USER", POSTGRES_USER)
        .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
        .withEnv("POSTGRES_DB", POSTGRES_DB)
        .withExposedPorts(POSTGRES_PORT)
        // tmpfs cap: heavy suites (directory-chain, ft4-lib, mna-blockchain, postchain-eif) run
        // thousands of test cases against a fresh `chr test` schema each, and the WAL + table
        // churn outgrew the previous 512m cap ("could not extend file ... No space left on
        // device" → backend EOF → "connection has been closed" surfaced as the DB-connectivity
        // failures in reports/results.json). 1g leaves ~25–50% headroom over observed peaks once
        // the WAL is bounded by `max_wal_size=128MB` below; the cap is lazy (RAM is consumed
        // only as the tmpfs fills) so light suites cost what they actually use.
        // CI budget: saas-linux-xlarge-amd64 (64 GB / 16 vCPU) with `--max-workers=8` →
        // 8 × 1 GB worst case = 8 GB tmpfs, comfortably inside the runner envelope (see the
        // budget comment on `pages:regression` in .gitlab/ci/pages.yml).
        .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw,size=1g"))
        // Ephemeral test database — no durability requirement. Tuning trades fsync safety for
        // disk-write volume so WAL stays small and we never refill the tmpfs cap:
        //   fsync=off, synchronous_commit=off, full_page_writes=off — skip durability work.
        //   max_wal_size=128MB — keep WAL footprint bounded; default checkpoint cadence is fine.
        // (autovacuum stays ON — heavy suites churn dead rows fast enough that bloat outweighs
        // the I/O cost of the worker, and the freed space helps the tmpfs cap.)
        .withCommand(
            "postgres",
            "-c", "fsync=off",
            "-c", "synchronous_commit=off",
            "-c", "full_page_writes=off",
            "-c", "max_wal_size=128MB",
        )
        // Postgres logs the "ready to accept connections" line twice on boot (once for the bootstrap
        // run, once for the real server); waiting for both avoids the JDBC race where chr connects
        // during the brief bootstrap window and gets refused.
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
    try {
        container.start()
        val jdbcUrl = "jdbc:postgresql://${container.host}:${container.getMappedPort(POSTGRES_PORT)}/$POSTGRES_DB"
        val dbEnv = mapOf(
            "CHR_DB_URL" to jdbcUrl,
            "CHR_DB_USER" to POSTGRES_USER,
            "CHR_DB_PASSWORD" to POSTGRES_PASSWORD,
        )
        log("run-one", "$tag — postgres @ $jdbcUrl")
        block(dbEnv)
    } finally {
        try {
            container.stop()
        } catch (e: Exception) {
            log("run-one", "$tag — postgres stop failed: ${e.message}")
        }
    }
}

// One BufferedWriter spans a phase's work: header, per-step markers, and any start-failure note.
// Opened with APPEND so each flush writes at the file's current EOF, which interleaves correctly
// with chr subprocesses that redirectOutput.appendTo() the same file between flushes.
private fun appendingWriter(logFile: Path): BufferedWriter = logFile.bufferedWriter(
    Charsets.UTF_8,
    DEFAULT_BUFFER_SIZE,
    StandardOpenOption.CREATE,
    StandardOpenOption.WRITE,
    StandardOpenOption.APPEND,
)

/** Write the per-project log header: identity, ref/sha, rell path, and the full command pipeline. */
private fun writeLogHeader(w: BufferedWriter, project: ProjectSpec, sha: String, rellDir: Path) {
    w.appendLine("# regression :: ${project.name}")
    w.appendLine("# url       :  ${project.url}")
    w.appendLine("# ref       :  ${project.ref ?: "(default branch)"}")
    w.appendLine("# sha       :  $sha")
    w.appendLine("# rell path :  $rellDir")
    w.appendLine("# commands  :  ${project.commands.joinToString(" && ") { "chr ${it.joinToString(" ")}" }}")
    w.newLine()
}

/**
 * Run `steps` in order against `chrBin`, short-circuiting on the first non-zero exit or timeout
 * and recording the offending step in `state`. `w` must be a writer opened on `logFile` in APPEND
 * mode: its `# step ::` markers are flushed so they interleave correctly with the chr subprocesses
 * that append their combined output straight to the same file.
 */
private fun runSteps(
    w: BufferedWriter,
    steps: List<List<String>>,
    chrBin: Path,
    rellDir: Path,
    logFile: Path,
    tag: String,
    state: PipelineState,
    extraEnv: Map<String, String>,
    stepTimeout: Duration,
) {
    for (step in steps) {
        val cmd = listOf(chrBin.absolutePathString()) + step
        log("compile", "$tag — chr ${step.joinToString(" ")}  (cwd=${rellDir.fileName})")
        w.newLine()
        w.appendLine("# step :: chr ${step.joinToString(" ")}")
        w.flush()

        val outcome = try {
            runToLog(cmd, rellDir, logFile, stepTimeout, extraEnv)
        } catch (e: IOException) {
            w.newLine()
            w.appendLine("# regression :: failed to start chr: ${e.message}")
            ProcessOutcome(exitCode = -1, durationMs = 0, timedOut = false)
        }

        if (step.isTestStep()) {
            state.testDurationMs += outcome.durationMs
        } else {
            state.nonTestDurationMs += outcome.durationMs
        }
        state.lastExit = outcome.exitCode
        state.lastTimedOut = outcome.timedOut
        if (outcome.exitCode != 0 || outcome.timedOut) {
            state.failedStep = step
            break
        }
    }
}

/** Turn a finished [PipelineState] into the project's [CompileResult] and log a one-line summary. */
private fun finalize(
    project: ProjectSpec,
    backend: ExecutionBackend,
    tag: String,
    sha: String,
    logFile: Path,
    state: PipelineState,
): CompileResult {
    val passed = state.ok

    val status = when {
        passed -> Status.PASSED
        project.expectedFailure -> Status.EXPECTED_FAIL
        else -> Status.FAILED
    }

    val summary = if (passed) null else tailLog(logFile, maxLines = 12)

    log(
        "compile",
        "$tag — ${status.name} in ${state.totalDurationMs / 1000.0}s " +
            "(exit ${state.lastExit}${if (state.lastTimedOut) ", TIMED OUT" else ""})",
    )

    return baseResult(project, status).copy(
        sha = sha,
        durationMs = state.totalDurationMs,
        nonTestDurationMs = state.nonTestDurationMs,
        testDurationMs = state.testDurationMs,
        exitCode = state.lastExit,
        timedOut = state.lastTimedOut,
        errorSummary = summary,
        logRelPath = "logs/${backend.name.lowercase()}/${project.name}.log",
        failedStep = state.failedStep,
        executionBackend = backend,
    )
}

/**
 * Apply every patch in `project.patches` to files under `clonedRoot`. Returns null on success
 * (or no patches), or a human-readable error string on the first failure. Each patch is a
 * literal find-and-replace — applied with `replace(...)`, not regex — so the user gets
 * predictable behaviour and no escaping surprises.
 *
 * Idempotent: if the `replace` literal is absent **and** the `with` literal is already present,
 * the patch is treated as already-applied (e.g. upstream merged the change, or a prior run
 * applied the patch on a workdir that the clone phase couldn't reset because of a network
 * error). This matches the user's intent without papering over a real mismatch — failure
 * still fires when neither string is in the file.
 */
private fun applyPatches(project: ProjectSpec, clonedRoot: Path): String? {
    if (project.patches.isEmpty()) return null
    for ((idx, patch) in project.patches.withIndex()) {
        val file = clonedRoot / patch.file
        if (!file.exists()) {
            return "patch #${idx + 1}: file ${patch.file} not found at ${file.toAbsolutePath()}"
        }
        val original = file.readText()
        if (!original.contains(patch.replace)) {
            if (original.contains(patch.with)) {
                log("compile", "  patch: ${patch.file}: `${patch.replace}` already replaced by `${patch.with}` — skip")
                continue
            }
            return "patch #${idx + 1}: neither `${patch.replace}` nor `${patch.with}` found in ${patch.file}"
        }
        val patched = original.replace(patch.replace, patch.with)
        file.writeText(patched)
        log("compile", "  patch: ${patch.file}: `${patch.replace}` → `${patch.with}`")
    }
    return null
}

private fun baseResult(project: ProjectSpec, status: Status): CompileResult = CompileResult(
    name = project.name,
    url = project.url,
    cohort = project.cohort,
    ref = project.ref,
    rellPath = project.rellPath,
    commands = project.commands,
    notes = project.notes,
    expectedFailure = project.expectedFailure,
    status = status,
)
