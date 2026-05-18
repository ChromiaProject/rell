/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.github.ajalt.clikt.core.Context
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

// 30 min covers install + build + test for the heaviest project (ft4-lib has 8+ test modules
// spread across two test chains). Bump if a new entry routinely times out.
private val PER_PROJECT_TIMEOUT: Duration = Duration.ofMinutes(30)
private val BOOTSTRAP_TIMEOUT: Duration = Duration.ofMinutes(60)

// `chr install` (dependency resolution) and `chr build` (compilation) operate on each project's
// own clone tree and never touch the database, so they fan out cleanly across projects. `chr test`
// does not: every project's suite runs against the same local PostgreSQL instance, and concurrent
// runs would race on shared schemas. compileAll therefore splits the pipeline in two — install/
// build across a worker pool, then test strictly serially. Any step that is neither `install` nor
// `build` is treated as serial too, so the split errs on the safe side for unrecognised commands.
private val PARALLELISABLE_STEPS = setOf("install", "build")

// Worker count for the install/build phase. Defaults to the CPU count; each `chr` invocation is a
// heavyweight JVM, so REGRESSION_COMPILE_JOBS lets a memory-constrained CI runner cap the fan-out.
private val COMPILE_PARALLELISM: Int =
    (System.getenv("REGRESSION_COMPILE_JOBS")?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors())
        .coerceAtLeast(1)

class CompileCommand : RegressionSubcommand("compile") {
    override fun help(context: Context) =
        "Bootstrap the local chr binary, then run `chr <command>` against every cloned project; writes results.json."

    override fun run() {
        val projects = loadProjects(configFiles, configOptionalFiles)
        compileAll(projects, workdir, reportsDir)
    }
}

fun compileAll(projects: List<ProjectSpec>, workdir: Path, reportsDir: Path): ResultsFile {
    reportsDir.createDirectories()
    val logsDir = (reportsDir / "logs").also { it.createDirectories() }

    val chrBin = bootstrapChr()
    log("compile", "chr binary: $chrBin")

    val total = projects.size

    // ── Phase 1: install + build, fanned out across a worker pool ────────────────────────────
    val workers = COMPILE_PARALLELISM.coerceAtMost(total.coerceAtLeast(1))
    log("compile", "Phase 1/2 — install + build for $total project(s) across $workers worker(s)")

    val pool = Executors.newFixedThreadPool(workers) { r ->
        Thread(r, "regression-compile").apply { isDaemon = true }
    }
    val phase1: List<Phase1Outcome> = try {
        projects
            .mapIndexed { idx, project ->
                pool.submit(Callable { compilePhase1(project, idx, total, workdir, logsDir, chrBin) })
            }
            .map { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    // A worker hit something the per-step IOException guard doesn't cover (e.g. an
                    // IOException while applying patches). Abort with the real cause, as the old
                    // sequential loop did — don't mask a toolkit bug as a project failure.
                    throw e.cause ?: e
                }
            }
    } finally {
        pool.shutdown()
    }

    // ── Phase 2: test, strictly serial — every project's suite shares one PostgreSQL instance ─
    val pending = phase1.filterIsInstance<Pending>()
    if (pending.isNotEmpty()) {
        log("compile", "Phase 2/2 — test for ${pending.size} project(s), serially")
    }
    val testResults = pending.associate { p -> p.project.name to compilePhase2(p, chrBin) }

    // Stitch results back into the original project order: phase-1 finishers verbatim, pending
    // projects swapped for their post-test result.
    val results = phase1.map { outcome ->
        when (outcome) {
            is Finished -> outcome.result
            is Pending -> testResults.getValue(outcome.project.name)
        }
    }

    val resultsFile = ResultsFile(
        generatedAtEpochMs = System.currentTimeMillis(),
        rellVersion = readRellVersion(),
        results = results,
    )

    val resultsPath = reportsDir / "results.json"
    regressionWriter.writeValue(resultsPath.toFile(), resultsFile)
    log("compile", "Wrote $resultsPath (${results.size} project(s))")

    val summary = results.groupingBy { it.status }.eachCount()
    log("compile", "Summary: $summary")
    return resultsFile
}

/** Mutable accumulator threaded through one project's chr-command pipeline across both phases. */
private class PipelineState {
    var totalDurationMs = 0L
    var lastExit = 0
    var lastTimedOut = false
    var failedStep: List<String>? = null

    /** The pipeline keeps running only while the previous step exited cleanly. */
    val ok: Boolean get() = lastExit == 0 && !lastTimedOut
}

/** Phase-1 result for one project. */
private sealed interface Phase1Outcome

/** Project is done — validation/patch failure, install/build failure, or no `test` step deferred. */
private class Finished(val result: CompileResult) : Phase1Outcome

/** install/build passed; the project still has `test` (and any later) steps to run serially. */
private class Pending(
    val project: ProjectSpec,
    val tag: String,
    val sha: String,
    val rellDir: Path,
    val logFile: Path,
    val serialSteps: List<List<String>>,
    val state: PipelineState,
) : Phase1Outcome

/**
 * Validate the clone, apply patches, then run the project's parallelisable steps (the leading
 * `install`/`build` prefix). Returns [Finished] when there is nothing left to do, or [Pending]
 * when `test` steps remain for the serial phase. Safe to call from a worker thread: every project
 * touches only its own clone tree and its own log file.
 */
private fun compilePhase1(
    project: ProjectSpec,
    idx: Int,
    total: Int,
    workdir: Path,
    logsDir: Path,
    chrBin: Path,
): Phase1Outcome {
    val tag = "[${idx + 1}/$total] ${project.name}"

    val target = workdir / project.name
    val sha = readGitSha(target)
    if (sha == null) {
        log("compile", "$tag — CLONE_FAILED (no .git at $target — run :regressionClone)")
        return Finished(baseResult(project, Status.CLONE_FAILED))
    }

    val rellDir = (target / project.rellPath).normalize()
    if (!rellDir.exists()) {
        log("compile", "$tag — CLONE_FAILED (rellPath=$rellDir does not exist after clone)")
        return Finished(baseResult(project, Status.CLONE_FAILED).copy(sha = sha))
    }

    // Pre-check: chr install / chr build both fail mechanically with "library not found in null"
    // when there's no chromia.yml in the rell dir. That's a tool-incompatibility, not a Rell
    // compiler regression — short-circuit before invoking chr so the report shows a clean note
    // instead of a wall of chr usage text. Projects pre-date the chromia.yml era live here.
    val chromiaYml = rellDir / "chromia.yml"
    if (!chromiaYml.exists()) {
        log("compile", "$tag — EXPECTED_FAIL (no chromia.yml at $rellDir — pre-chromia.yml layout)")
        return Finished(
            baseResult(project, Status.EXPECTED_FAIL).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "no chromia.yml at $rellDir; project pre-dates chromia.yml era " +
                    "and is incompatible with the current chr CLI — not a Rell compiler regression",
            ),
        )
    }

    // Apply declared patches (e.g. rewrite stricter-than-allowed chromia.yml strings).
    // Applied per-compile so they survive `regressionClone`'s git reset on the next run.
    val patchFailure = applyPatches(project, target)
    if (patchFailure != null) {
        log("compile", "$tag — FAILED (patch error: $patchFailure)")
        return Finished(
            baseResult(project, Status.FAILED).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "patch failed before chr: $patchFailure",
            ),
        )
    }

    val logFile = logsDir / "${project.name}.log"
    logFile.deleteIfExists()

    // Split the pipeline: the leading run of install/build steps is parallelisable; `test` (and
    // anything past it) is held back for the serial phase. takeWhile keeps within-project order
    // intact and treats any unrecognised step as serial — see PARALLELISABLE_STEPS.
    val parallelSteps = project.commands.takeWhile { it.firstOrNull() in PARALLELISABLE_STEPS }
    val serialSteps = project.commands.drop(parallelSteps.size)

    val state = PipelineState()

    // One BufferedWriter spans phase 1's work: header, per-step markers, and any start-failure
    // note. Opened with APPEND so each flush writes at the file's current EOF, which interleaves
    // correctly with chr subprocesses that redirectOutput.appendTo() the same file between flushes.
    logFile.bufferedWriter(
        Charsets.UTF_8,
        DEFAULT_BUFFER_SIZE,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND,
    ).use { w ->
        writeLogHeader(w, project, sha, rellDir)
        runSteps(w, parallelSteps, chrBin, rellDir, logFile, tag, state)
    }

    // Finalise here when install/build failed (test would be short-circuited anyway) or when the
    // project has no test step — so the serial phase only ever handles real test suites.
    return if (!state.ok || serialSteps.isEmpty()) {
        Finished(finalize(project, tag, sha, logFile, state))
    } else {
        Pending(project, tag, sha, rellDir, logFile, serialSteps, state)
    }
}

/** Run the deferred serial steps (`chr test`) for one project, then finalise its result. */
private fun compilePhase2(pending: Pending, chrBin: Path): CompileResult {
    pending.logFile.bufferedWriter(
        Charsets.UTF_8,
        DEFAULT_BUFFER_SIZE,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND,
    ).use { w ->
        runSteps(w, pending.serialSteps, chrBin, pending.rellDir, pending.logFile, pending.tag, pending.state)
    }
    return finalize(pending.project, pending.tag, pending.sha, pending.logFile, pending.state)
}

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
) {
    for (step in steps) {
        val cmd = listOf(chrBin.absolutePathString()) + step
        log("compile", "$tag — chr ${step.joinToString(" ")}  (cwd=${rellDir.fileName})")
        w.newLine()
        w.appendLine("# step :: chr ${step.joinToString(" ")}")
        w.flush()

        val outcome = try {
            runToLog(cmd, rellDir, logFile, PER_PROJECT_TIMEOUT)
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
        logRelPath = "logs/${project.name}.log",
        failedStep = state.failedStep,
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

private fun bootstrapChr(): Path {
    val root = repoRoot()

    val chrBin = root / "chromia-cli-local" / "chromia-cli" / "target" /
        "chromia-cli-dev-dist" / "bin" / "chr"

    // Always run local-chr.sh, even when the chr binary already exists from a previous run
    // or a restored CI cache. local-chr.sh is itself incremental: it re-publishes the current
    // Rell snapshot to ~/.m2, rebuilds chromia-cli-tools against it, *skips* the expensive
    // chromia-cli Maven build when the binary is already present, and re-syncs the freshly
    // built Rell jars into the distribution's lib/. Returning early on `chrBin.exists()` here
    // skipped that jar refresh, so a cached chromia-cli-local/ pinned the run to a stale Rell —
    // the suite then passed locally (fresh build) but failed on CI (frozen jars).
    log(
        "compile",
        if (chrBin.exists()) {
            "chr binary present at $chrBin — running ./work/local-chr.sh to refresh Rell jars " +
                "(the chromia-cli build itself is reused)"
        } else {
            "chr not found — running ./work/local-chr.sh --version (this is slow on first run)"
        },
    )

    val pb = ProcessBuilder("bash", (root / "work" / "local-chr.sh").toString(), "--version")
        .directory(root.toFile())
        .inheritIO()

    val proc = pb.start()
    val finished = proc.waitFor(BOOTSTRAP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
    if (!finished) {
        proc.destroyForcibly()
        die("compile", "local-chr.sh did not finish within $BOOTSTRAP_TIMEOUT — aborted")
    }
    val rc = proc.exitValue()
    if (rc != 0) die("compile", "local-chr.sh exited with code $rc")
    if (!chrBin.exists()) die("compile", "Expected chr at $chrBin after bootstrap, but it is missing")
    return chrBin
}
