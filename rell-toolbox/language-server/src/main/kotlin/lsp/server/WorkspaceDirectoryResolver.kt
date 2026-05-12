/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.toPath

object WorkspaceDirectoryResolver {

    private const val MAX_DEPTH = 5

    fun findSourceDirURI(workspaceUri: URI): URI {
        val workspaceFolder = File(workspaceUri)

        val rellSrcFolder = workspaceFolder.resolve("rell/src")
        val rellFolder = workspaceFolder.resolve("rell")
        val srcFolder = workspaceFolder.resolve("src")
        val parentSrcFolder = findSrcParentDirectory(workspaceUri)

        val sourceFolder = when {
            rellSrcFolder.exists() && rellFolder.isDirectory -> rellSrcFolder
            rellFolder.exists() && rellFolder.isDirectory -> rellFolder
            srcFolder.exists() && srcFolder.isDirectory -> srcFolder
            parentSrcFolder != null -> parentSrcFolder
            else -> null
        }

        return sourceFolder?.toURI() ?: workspaceFolder.toURI()
    }

    fun findSourceDirPathFromConfig(chromiaModelPath: Path): Path {
        val configSourcePath = ChromiaModelProvider.loadChromiaModelFromFile(chromiaModelPath)?.compile?.source

        return if (configSourcePath != null && configSourcePath.exists()) {
            configSourcePath.normalize()
        } else {
            findSourceDirURI(chromiaModelPath.parent.toUri()).toPath()
        }
    }

    fun findProjectRootUriFromChild(sourceDirUri: URI): URI? {
        val parentDir = File(sourceDirUri).parentFile ?: return null
        val grandParentDir = parentDir.parentFile ?: return null

        return when {
            parentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME).exists() -> parentDir.toURI()
            grandParentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME)
                .exists() -> grandParentDir.toURI()
            else -> null
        }
    }

    private fun findSrcParentDirectory(uri: URI): File? {
        if (!uri.path.endsWith(".rell")) return null

        val path = uri.toPath()
        var depth = 0
        var currentPath = path
        while (currentPath.parent != null && depth < MAX_DEPTH) {
            depth++
            val srcDirectory = currentPath.resolveSibling("src")
            if (srcDirectory.isValid(path)) {
                return srcDirectory.toFile()
            }
            currentPath = currentPath.parent
        }
        return null
    }

    private fun Path.isValid(other: Path) = this.exists() && this.isDirectory() && other.startsWith(this)
}
