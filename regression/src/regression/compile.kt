/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.github.ajalt.clikt.core.Context
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
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
    val results = ArrayList<CompileResult>(total)

    for ((idx, project) in projects.withIndex()) {
        val tag = "[${idx + 1}/$total] ${project.name}"

        val target = workdir / project.name
        val sha = readGitSha(target)
        if (sha == null) {
            log("compile", "$tag — CLONE_FAILED (no .git at $target — run :regressionClone)")
            results += baseResult(project, Status.CLONE_FAILED)
            continue
        }

        val rellDir = (target / project.rellPath).normalize()
        if (!rellDir.exists()) {
            log("compile", "$tag — CLONE_FAILED (rellPath=$rellDir does not exist after clone)")
            results += baseResult(project, Status.CLONE_FAILED).copy(sha = sha)
            continue
        }

        // Pre-check: chr install / chr build both fail mechanically with "library not found in null"
        // when there's no chromia.yml in the rell dir. That's a tool-incompatibility, not a Rell
        // compiler regression — short-circuit before invoking chr so the report shows a clean note
        // instead of a wall of chr usage text. Projects pre-date the chromia.yml era live here.
        val chromiaYml = rellDir / "chromia.yml"
        if (!chromiaYml.exists()) {
            log("compile", "$tag — EXPECTED_FAIL (no chromia.yml at $rellDir — pre-chromia.yml layout)")
            results += baseResult(project, Status.EXPECTED_FAIL).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "no chromia.yml at $rellDir; project pre-dates chromia.yml era " +
                    "and is incompatible with the current chr CLI — not a Rell compiler regression",
            )
            continue
        }

        // Apply declared patches (e.g. rewrite stricter-than-allowed chromia.yml strings).
        // Applied per-compile so they survive `regressionClone`'s git reset on the next run.
        val patchFailure = applyPatches(project, target)
        if (patchFailure != null) {
            log("compile", "$tag — FAILED (patch error: $patchFailure)")
            results += baseResult(project, Status.FAILED).copy(
                sha = sha,
                durationMs = 0L,
                errorSummary = "patch failed before chr: $patchFailure",
            )
            continue
        }

        val logFile = logsDir / "${project.name}.log"
        logFile.deleteIfExists()

        // Run the chr-command pipeline. Any non-zero exit short-circuits the rest; failedStep records
        // which step gave up so the report can highlight `install` vs `build` failures separately.
        var totalDuration = 0L
        var lastExit = 0
        var lastTimedOut = false
        var failedStep: List<String>? = null

        // One BufferedWriter spans the whole project: header, per-step markers, and any
        // start-failure note. Opened with APPEND so each flush writes at the file's current EOF,
        // which interleaves correctly with chr subprocesses that redirectOutput.appendTo() the
        // same file between our flushes.
        logFile.bufferedWriter(
            Charsets.UTF_8,
            DEFAULT_BUFFER_SIZE,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        ).use { w ->
            w.appendLine("# regression :: ${project.name}")
            w.appendLine("# url       :  ${project.url}")
            w.appendLine("# ref       :  ${project.ref ?: "(default branch)"}")
            w.appendLine("# sha       :  $sha")
            w.appendLine("# rell path :  $rellDir")
            w.appendLine("# commands  :  ${project.commands.joinToString(" && ") { "chr ${it.joinToString(" ")}" }}")
            w.newLine()

            for (step in project.commands) {
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

                totalDuration += outcome.durationMs
                lastExit = outcome.exitCode
                lastTimedOut = outcome.timedOut
                if (outcome.exitCode != 0 || outcome.timedOut) {
                    failedStep = step
                    break
                }
            }
        }

        val passed = lastExit == 0 && !lastTimedOut

        val status = when {
            passed -> Status.PASSED
            project.expectedFailure -> Status.EXPECTED_FAIL
            else -> Status.FAILED
        }

        val summary = if (passed) null else tailLog(logFile, maxLines = 12)

        log(
            "compile",
            "$tag — ${status.name} in ${totalDuration / 1000.0}s (exit $lastExit${if (lastTimedOut) ", TIMED OUT" else ""})",
        )

        results += baseResult(project, status).copy(
            sha = sha,
            durationMs = totalDuration,
            exitCode = lastExit,
            timedOut = lastTimedOut,
            errorSummary = summary,
            logRelPath = "logs/${project.name}.log",
            failedStep = failedStep,
        )
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

/**
 * Apply every patch in `project.patches` to files under `clonedRoot`. Returns null on success
 * (or no patches), or a human-readable error string on the first failure. Each patch is a
 * literal find-and-replace — applied with `replace(...)`, not regex — so the user gets
 * predictable behaviour and no escaping surprises.
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
            return "patch #${idx + 1}: literal `${patch.replace}` not found in ${patch.file}"
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

    if (chrBin.exists()) {
        log("compile", "chr binary exists at $chrBin — skipping bootstrap (delete chromia-cli-local/ to force rebuild)")
        return chrBin
    }

    log("compile", "chr not found — running ./work/local-chr.sh --version (this is slow on first run)")

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
