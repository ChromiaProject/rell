/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.useLines
import kotlin.system.exitProcess

private val LOG_TS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)

internal fun log(component: String, msg: String) {
    println("[$component] ${LOG_TS.format(Instant.now())} $msg")
}

internal fun die(component: String, msg: String): Nothing {
    log(component, "FATAL: $msg")
    exitProcess(1)
}

// All regression tasks are launched from the Rell repo root via build.gradle.kts
// (`workingDir = project.rootDir`). This sanity-checks the assumption — failing fast here
// is far easier to debug than a confused `local-chr.sh: must be run from the repository
// root` deep in the bootstrap.
internal fun repoRoot(): Path {
    val cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val marker = cwd / "work" / "local-chr.sh"
    require(marker.isRegularFile()) {
        "Expected to be invoked from the Rell repo root (cwd=$cwd); $marker not found. " +
            "The Gradle tasks in :regression set workingDir = rootDir — running the Clikt CLI " +
            "directly? cd into the repo root first."
    }
    return cwd
}

internal data class ProcessOutcome(val exitCode: Int, val durationMs: Long, val timedOut: Boolean)

/**
 * Run `command` in `cwd`, redirect combined stdout+stderr to `logFile`, return exit + timing.
 *
 * Sets `GIT_TERMINAL_PROMPT=0` so an unauthenticated clone fails fast instead of hanging on
 * a credentials prompt in CI. Also sets the same for SSH (`GIT_SSH_COMMAND`) — not strictly
 * needed for HTTPS URLs but cheap insurance.
 */
internal fun runToLog(
    command: List<String>,
    cwd: Path,
    logFile: Path,
    timeout: java.time.Duration,
    extraEnv: Map<String, String> = emptyMap(),
): ProcessOutcome {
    val start = System.nanoTime()

    val pb = ProcessBuilder(command)
        .directory(cwd.toFile())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))

    pb.environment().apply {
        put("GIT_TERMINAL_PROMPT", "0")
        put("GIT_SSH_COMMAND", "ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new")
        // Run chr under the same JDK the toolkit itself runs on (Gradle's toolchain JVM), not the
        // ambient shell's java — keeps the Rell the projects compile against on a known JDK.
        put("JAVA_HOME", System.getProperty("java.home"))
        putAll(extraEnv)
    }

    val proc = pb.start()
    val finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)

    if (!finished) {
        proc.destroyForcibly()
        return ProcessOutcome(
            exitCode = -1,
            durationMs = (System.nanoTime() - start) / 1_000_000,
            timedOut = true,
        )
    }

    return ProcessOutcome(
        exitCode = proc.exitValue(),
        durationMs = (System.nanoTime() - start) / 1_000_000,
        timedOut = false,
    )
}

private val RELL_VERSION = Regex("""version\s*=\s*"([^"]+)"""")

/** Read `version = "..."` out of the root build.gradle.kts so the report can pin which Rell built the projects. */
internal fun readRellVersion(): String {
    val root = repoRoot()
    val buildFile = root / "build.gradle.kts"
    if (!buildFile.exists()) return "unknown"

    buildFile.useLines { lines ->
        for (line in lines)
            RELL_VERSION.find(line)?.let { return@readRellVersion it.groupValues[1] }
    }

    return "unknown"
}

/** Tail the last `maxLines` non-blank lines of a log file — the chr error usually lives near the end. */
internal fun tailLog(logFile: Path, maxLines: Int = 25): String {
    if (!logFile.exists()) return ""
    val lines = ArrayDeque<String>(maxLines)

    logFile.useLines { seq ->
        for (line in seq) {
            if (line.isBlank()) continue
            if (lines.size == maxLines) lines.removeFirst()
            lines.addLast(line)
        }
    }

    return lines.joinToString("\n")
}
