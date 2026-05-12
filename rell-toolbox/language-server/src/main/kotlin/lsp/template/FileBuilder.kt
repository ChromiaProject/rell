/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import net.postchain.rell.toolbox.chromia.model.DEFAULT_CHROMIA_MODEL_RELL_VERSION
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

@PublishedApi
internal fun snakeCaseName(string: String): String = string.replace("-", "_")

class FileBuilder(
    @PublishedApi internal val targetDir: Path,
    sourceFolderName: String,
    @PublishedApi internal val templatesRoot: Path,
) {
    @PublishedApi
    internal val sourceDir: Path = templatesRoot / sourceFolderName

    inline fun createFile(
        sourceName: String,
        targetName: String = sourceName,
        transform: (String) -> String = { it },
    ) {
        val srcFile = sourceDir / sourceName
        check(srcFile.isRegularFile()) { "Template resource not found: $srcFile" }
        val outFile = targetDir / targetName
        outFile.parent?.createDirectories()
        outFile.writeText(transform(srcFile.readText()))
    }

    fun moveFolder(sourceName: String) {
        val src = if (sourceName.isEmpty()) sourceDir else sourceDir / sourceName
        check(src.isDirectory()) { "Template folder not found: $src" }
        targetDir.createDirectories()

        src.walk().filter { it.isRegularFile() }.forEach { sourceFile ->
            val relPath = src.relativize(sourceFile)
            val outFile = targetDir / relPath.toString()
            outFile.parent?.createDirectories()
            sourceFile.copyTo(outFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    inline fun createChromiaConfig(projectName: String, crossinline transform: (String) -> String = { it }) {
        createFile("chromia.yml") {
            it.replace("PROJECT_NAME", snakeCaseName(projectName))
                .replace("RELL_VERSION", DEFAULT_CHROMIA_MODEL_RELL_VERSION)
                .replace("RELL_SCHEMA", "schema_${snakeCaseName(projectName)}")
                .let(transform)
        }
    }

    inline fun createGitIgnore(init: () -> String = { "" }) {
        copyRootFile(".gitignore", init())
    }

    inline fun createLinterConfig(init: () -> String = { "" }) {
        copyRootFile(".rell_lint", init())
    }

    inline fun createFormatterConfig(init: () -> String = { "" }) {
        copyRootFile(".rell_format", init())
    }

    @PublishedApi
    internal fun copyRootFile(name: String, extra: String) {
        val srcFile = templatesRoot / name
        check(srcFile.isRegularFile()) { "Template root file not found: $srcFile" }
        val outFile = targetDir / name
        outFile.parent?.createDirectories()
        outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            srcFile.bufferedReader(Charsets.UTF_8).use { reader -> reader.copyTo(writer) }
            if (extra.isNotEmpty()) writer.write(extra)
        }
    }
}
