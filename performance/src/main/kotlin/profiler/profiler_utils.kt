/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.profiler

import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.div
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.useLines
import kotlin.math.abs
import kotlin.system.exitProcess

/** Resolve `<repo>/performance` whether invoked from the module dir (Gradle) or repo root. */
internal fun perfDir(): Path {
    val cwd = Path.of(".").toAbsolutePath().normalize()
    return if (cwd.fileName?.toString() == "performance") cwd else cwd.resolve("performance")
}

internal fun repoRoot(): Path = perfDir().parent
    ?: error("Could not resolve repo root from cwd=${Path.of(".").toAbsolutePath()}")

private val LOG_TS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)

internal fun log(component: String, msg: String) {
    println("[$component] ${LOG_TS.format(Instant.now())} $msg")
}

internal fun die(component: String, msg: String): Nothing {
    log(component, "FATAL: $msg")
    exitProcess(1)
}

internal fun loadLocalProperties(): Map<String, String> {
    val file = perfDir() / "local.properties"
    if (!file.isRegularFile()) return emptyMap()

    return buildMap {
        file.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val idx = line.indexOf('=')
                if (idx > 0) this[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        }
    }
}

internal fun which(name: String): Path? {
    val pathEnv = System.getenv("PATH") ?: return null
    for (dir in pathEnv.split(File.pathSeparator)) {
        if (dir.isEmpty()) continue
        val candidate = Path.of(dir, name)
        if (candidate.isExecutable())
            return candidate
    }
    return null
}

internal fun humanSize(path: Path): String {
    if (!path.exists()) return "0 B"
    var bytes = path.fileSize().toDouble()
    for (unit in arrayOf("B", "KB", "MB", "GB")) {
        if (abs(bytes) < 1024) return "%.1f %s".format(Locale.ROOT, bytes, unit)
        bytes /= 1024.0
    }
    return "%.1f TB".format(Locale.ROOT, bytes)
}
