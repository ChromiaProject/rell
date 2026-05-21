/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.fasterxml.jackson.module.kotlin.readValue
import net.postchain.rell.performance.chr.LocalChr
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

// `chr install` (dependency resolution) and `chr build` (compilation) operate on each project's
// own clone tree and never touch the database, so the build phase fans out cleanly across
// projects — Gradle's worker pool (org.gradle.workers.max / --max-workers) drives that fan-out
// via the per-project `build-one` invocation. `chr test` does not fan out: every project's suite
// runs against the same local PostgreSQL instance, so the Gradle task runs every `test-one`
// strictly serially. The split is keyed on this step set; any step that is neither `install` nor
// `build` is treated as a serial (test-phase) step, so unrecognised commands err on the safe side.
private val PARALLELISABLE_STEPS = setOf("install", "build")

// ─── CLI subcommands ──────────────────────────────────────────────────────────────────────────
//
// The compile pipeline is sliced into single-unit invocations so Gradle owns all the threading:
//   build-one   — run the install/build prefix for ONE project under ONE backend (parallel-safe)
//   test-one    — run the deferred test steps for ONE project under ONE backend (serial)
// Each invocation reads/writes one result fragment under reports/parts/; `report` merges them.
// The chr binary they drive is built upstream by the shared `:performance:buildLocalChr` task.

/** Shared `--name`/`--backend` plumbing for the single-project build/test subcommands. */
abstract class SingleProjectSubcommand(name: String) : RegressionSubcommand(name) {
    val projectName: String by option("--name", help = "Project name as listed in the JSON config.").required()
    val backend: ExecutionBackend by option("--backend", help = "Execution backend for this run.")
        .enum<ExecutionBackend>().required()

    protected fun resolveProject(): ProjectSpec {
        val projects = loadProjects(configFiles, configOptionalFiles)
        return projects.firstOrNull { it.name == projectName }
            ?: die(commandName, "No project named '$projectName' in the supplied config(s)")
    }
}

class BuildOneCommand : SingleProjectSubcommand("build-one") {
    override fun help(context: Context) =
        "Run the install/build prefix for a single project under one backend; writes its result fragment."

    override fun run() = buildOneProject(resolveProject(), workdir, reportsDir, backend)
}

class TestOneCommand : SingleProjectSubcommand("test-one") {
    override fun help(context: Context) =
        "Run the deferred test steps for a single project under one backend; updates its result fragment."

    override fun run() = testOneProject(resolveProject(), workdir, reportsDir, backend)
}

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

private fun readFragment(reportsDir: Path, backend: ExecutionBackend, name: String): CompileResult? {
    val path = fragmentPath(reportsDir, backend, name)
    if (!path.exists()) return null
    return regressionMapper.readValue<CompileResult>(path.inputStream())
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

/** Mutable accumulator threaded through one project's chr-command pipeline across build + test. */
private class PipelineState {
    var totalDurationMs = 0L
    var lastExit = 0
    var lastTimedOut = false
    var failedStep: List<String>? = null

    /** The pipeline keeps running only while the previous step exited cleanly. */
    val ok: Boolean get() = lastExit == 0 && !lastTimedOut
}

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
 * Build phase for one (project, backend): validate the clone, apply patches, then run the leading
 * `install`/`build` prefix of the project's command list and write the result fragment. Backend-
 * independent compilation-wise, but each backend gets its own fragment + log so the report stays a
 * self-contained per-backend record. Safe to run concurrently with other projects — every project
 * touches only its own clone tree, log file, and fragment.
 */
fun buildOneProject(project: ProjectSpec, workdir: Path, reportsDir: Path, backend: ExecutionBackend) {
    partsDir(reportsDir).createDirectories()
    val tag = "[${backend.label()}] ${project.name}"

    val chrBin = LocalChr.chrExecutable(repoRoot())
    val located = locate(project, workdir, backend)
    if (located is Unusable) {
        log("build-one", "$tag — ${located.result.status} (clone not usable; see notes)")
        writeFragment(reportsDir, backend, located.result)
        return
    }
    val (sha, rellDir) = (located as Usable).let { it.sha to it.rellDir }

    // Apply declared patches (e.g. rewrite stricter-than-allowed chromia.yml strings). Applied
    // per build so they survive `regressionClone`'s git reset on the next run. Idempotent, so the
    // test phase re-running `locate` without patches is fine.
    val patchFailure = applyPatches(project, workdir / project.name)
    if (patchFailure != null) {
        log("build-one", "$tag — FAILED (patch error: $patchFailure)")
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

    // The leading run of install/build steps is the parallel-safe build phase; `test` (and anything
    // past it) is deferred to test-one. takeWhile keeps within-project order intact and treats any
    // unrecognised step as serial — see PARALLELISABLE_STEPS.
    val parallelSteps = project.commands.takeWhile { it.firstOrNull() in PARALLELISABLE_STEPS }

    val logFile = logFileFor(reportsDir, backend, project.name)
    logFile.parent.createDirectories()
    logFile.deleteIfExists()

    val state = PipelineState()
    appendingWriter(logFile).use { w ->
        writeLogHeader(w, project, sha, rellDir)
        runSteps(w, parallelSteps, chrBin, rellDir, logFile, tag, state, backend.extraEnv(), project.stepTimeout())
    }

    writeFragment(reportsDir, backend, finalize(project, backend, tag, sha, logFile, state))
}

/**
 * Test phase for one (project, backend): pick up the build phase's fragment, run the deferred
 * `test` steps if the build passed, and overwrite the fragment with the final result. Run strictly
 * serially across projects by the Gradle task — every suite shares one PostgreSQL instance.
 */
fun testOneProject(project: ProjectSpec, workdir: Path, reportsDir: Path, backend: ExecutionBackend) {
    val tag = "[${backend.label()}] ${project.name}"

    val located = locate(project, workdir, backend)
    if (located is Unusable) {
        // build-one already wrote the terminal fragment for the same reason; nothing to test.
        return
    }
    val rellDir = (located as Usable).rellDir

    val built = readFragment(reportsDir, backend, project.name)
    if (built == null) {
        log("test-one", "$tag — no build fragment found; skipping (did build-one run?)")
        return
    }

    val serialSteps = project.commands.drop(
        project.commands.takeWhile { it.firstOrNull() in PARALLELISABLE_STEPS }.size,
    )

    // Build failed (or was a terminal status), or there is nothing to test — the build fragment is
    // already final.
    if (built.status != Status.PASSED || serialSteps.isEmpty()) return

    val chrBin = LocalChr.chrExecutable(repoRoot())
    val logFile = logFileFor(reportsDir, backend, project.name)

    // Resume the accumulator from the build phase so durations and the final exit reflect the whole
    // pipeline, not just the test steps.
    val state = PipelineState().apply {
        totalDurationMs = built.durationMs ?: 0L
        lastExit = built.exitCode ?: 0
        lastTimedOut = built.timedOut
    }

    appendingWriter(logFile).use { w ->
        runSteps(w, serialSteps, chrBin, rellDir, logFile, tag, state, backend.extraEnv(), project.stepTimeout())
    }

    writeFragment(reportsDir, backend, finalize(project, backend, tag, built.sha ?: "", logFile, state))
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

        state.totalDurationMs += outcome.durationMs
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
