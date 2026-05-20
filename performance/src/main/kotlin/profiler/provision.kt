/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:OptIn(ExperimentalPathApi::class)
@file:JvmName("ProvisionKt")

package net.postchain.rell.performance.profiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipInputStream
import kotlin.io.path.*

private const val DEFAULT_VERSION = "4.3"

/** Idempotent: downloads async-profiler into `<performance>/async-profiler/` if missing. */
fun provisionAsprof(version: String = System.getenv("PROFILER_VERSION") ?: DEFAULT_VERSION): Path {
    val installDir = perfDir() / "async-profiler"
    val asprof = installDir / "bin" / "asprof"

    if (asprof.isRegularFile() && asprof.isExecutable()) {
        log("provision", "async-profiler already provisioned at $asprof")
        runVersion(asprof)
        return asprof
    }

    val plat = detectPlatform()
    val ext = if (plat == "macos") "zip" else "tar.gz"
    val archiveName = "async-profiler-$version-$plat"
    val url = "https://github.com/async-profiler/async-profiler/releases/download/v$version/$archiveName.$ext"

    installDir.createDirectories()
    val tmpDir = createTempDirectory("asprof-")
    try {
        log("provision", "Downloading async-profiler $version for $plat")
        log("provision", "  $url")
        val archive = tmpDir.resolve("archive.$ext")
        URI(url).toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }

        log("provision", "Extracting...")
        val extractDir = tmpDir.resolve("extracted")
        extractDir.createDirectories()

        if (ext == "zip") extractZip(archive, extractDir) else extractTarGz(archive, extractDir)

        val sub = extractDir.useDirectoryEntries { s -> s.find { it.isDirectory() } }
            ?: error("Extracted archive did not contain any top-level directory")

        sub.useDirectoryEntries { s ->
            for (item in s) {
                val dest = installDir / item.name
                if (dest.exists()) dest.deleteRecursively()
                // Use copyToRecursively rather than moveTo: tmp may be on a different
                // filesystem (e.g. tmpfs in CI), and Files.move can't copy-and-delete
                // a non-empty directory across devices.
                item.copyToRecursively(dest, followLinks = false)
            }
        }

        try {
            val perms = asprof.getPosixFilePermissions().toMutableSet()
            perms += setOf(
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE,
            )
            asprof.setPosixFilePermissions(perms)
        } catch (_: UnsupportedOperationException) {}
    } finally {
        tmpDir.deleteRecursively()
    }

    log("provision", "async-profiler $version installed to $installDir")
    runVersion(asprof)
    return asprof
}

class ProvisionCommand: CliktCommand(name = "provision-asprof") {
    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "Download async-profiler for the current OS/architecture into performance/async-profiler/."

    private val version by option("--version", help = "async-profiler version (default $DEFAULT_VERSION)")
        .default(System.getenv("PROFILER_VERSION") ?: DEFAULT_VERSION)

    override fun run() {
        provisionAsprof(version)
    }
}

private fun detectPlatform(): String {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("linux") -> {
            val a = when (arch) {
                "x86_64", "amd64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                else -> error("Unsupported Linux architecture: $arch")
            }
            "linux-$a"
        }

        else -> error("Unsupported OS: $osName")
    }
}

private fun runVersion(asprof: Path) {
    try {
        val out = ProcessBuilder(asprof.toString(), "--version")
            .redirectErrorStream(true).start()
        out.inputStream.bufferedReader().useLines { lines ->
            lines.firstOrNull()?.let { println(it) }
        }
        out.waitFor()
    } catch (_: Exception) {}
}

private fun extractZip(archive: Path, dest: Path) = archive.inputStream().use { stream ->
    ZipInputStream(stream).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            val target = dest.resolve(entry.name).normalize()
            require(target.startsWith(dest)) { "Zip entry escapes archive root: ${entry.name}" }
            if (entry.isDirectory) {
                target.createDirectories()
            } else {
                target.parent.createDirectories()
                target.outputStream().use { zis.copyTo(it) }
            }
        }
    }
}

// Defer to system `tar` instead of pulling in Commons Compress for the Linux-only .tar.gz.
private fun extractTarGz(archive: Path, dest: Path) {
    val tar = which("tar") ?: error("`tar` binary not found on PATH (required to unpack ${archive.name})")

    val proc = ProcessBuilder(tar.toString(), "-xzf", archive.toString(), "-C", dest.toString())
        .redirectErrorStream(true)
        .start()

    val output = proc.inputStream.bufferedReader().readText()
    val rc = proc.waitFor()
    require(rc == 0) { "tar -xzf failed (exit $rc): $output" }
}

fun main(args: Array<String>) = ProvisionCommand().main(args)
