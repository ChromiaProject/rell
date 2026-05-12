/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.*

// The templates live in chromia-cli-tools (public GitLab project 64941451). We pull a versioned
// tarball/zip at runtime instead of vendoring ~3 MB of resources here. Bump CHROMIA_TEMPLATES_REF
// when a new template release lands and CI/clients are ready to consume it.
internal const val CHROMIA_TEMPLATES_PROJECT_ID = "64941451"
internal const val CHROMIA_TEMPLATES_REF = "0.11.4"
internal const val CHROMIA_TEMPLATES_PATH =
    "chromia-build-tools/src/main/resources/com/chromia/build/tools/template"

interface TemplateRepository {
    /** Directory containing the per-template subdirectories (`plain/`, `minimal/`, ...) plus the
     * root-level `.gitignore`, `.rell_format`, `.rell_lint`, `.devcontainer/`. */
    fun templatesRoot(): Path
}

class LocalDirTemplateRepository(private val root: Path): TemplateRepository {
    override fun templatesRoot(): Path = root
}

class RemoteTemplateRepository(
    private val cacheDir: Path = defaultCacheDir(),
    private val projectId: String = CHROMIA_TEMPLATES_PROJECT_ID,
    private val ref: String = CHROMIA_TEMPLATES_REF,
    private val subPath: String = CHROMIA_TEMPLATES_PATH,
): TemplateRepository {

    override fun templatesRoot(): Path {
        val target = cacheDir / ref
        val readyMarker = target / READY_MARKER_FILE
        if (target.isDirectory() && readyMarker.isRegularFile()) {
            return target
        }
        downloadAndExtract(target)
        return target
    }

    @OptIn(ExperimentalPathApi::class)
    private fun downloadAndExtract(target: Path) {
        val url = URI(
            "https://gitlab.com/api/v4/projects/$projectId/repository/archive.zip" +
                    "?path=$subPath&sha=$ref",
        ).toURL()

        cacheDir.createDirectories()
        val tmp = createTempDirectory(cacheDir, "rell-templates-")

        try {
            val marker = "$subPath/"
            url.openStream().use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val idx = entry.name.indexOf(marker)
                            if (idx >= 0) {
                                val rel = entry.name.substring(idx + marker.length)
                                if (rel.isNotEmpty()) {
                                    val outFile = tmp / rel
                                    outFile.parent?.createDirectories()
                                    outFile.outputStream().use { zis.copyTo(it) }
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            (tmp / READY_MARKER_FILE).writeText(ref)
            if (target.exists()) target.toFile().deleteRecursively()

            try {
                tmp.moveTo(target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                tmp.moveTo(target)
            }
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }

    companion object {
        private const val READY_MARKER_FILE = ".ready"

        fun defaultCacheDir(): Path = System.getenv("CHROMIA_HOME")?.let { Path.of(it, "template-cache") }
            ?: Path.of(System.getProperty("user.home"), ".chromia", "template-cache")
    }
}
