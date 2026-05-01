/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IndexRoot(val chromiaConfigPath: Path, val sourceRootPath: Path) {
    val sourceRootUri: URI by lazy {
        checkNotNull(parseFileUri(sourceRootPath.toUri().toString())) { "Failed to parse source path URI" }
    }

    val chromiaConfigDirUri: URI by lazy {
        checkNotNull(parseFileUri(chromiaConfigPath.parent.toUri().toString())) {
            "Failed to parse chromia model parent URI"
        }
    }

    companion object {
        fun findIndexRoots(workspaceFolderUri: URI): List<IndexRoot> {
            val workspacePath = Paths.get(workspaceFolderUri)

            val chromiaConfigFiles = findChromiaConfigFiles(workspacePath)
            return chromiaConfigFiles.map {
                fromChromiaConfig(it)
            }
        }

        fun fromChromiaConfig(chromiaConfigPath: Path): IndexRoot =
            IndexRoot(chromiaConfigPath, WorkspaceDirectoryResolver.findSourceDirPathFromConfig(chromiaConfigPath))

        private fun findChromiaConfigFiles(workspacePath: Path): List<Path> {
            return Files.walk(workspacePath)
                .filter { path ->
                    val fileName = path.fileName.toString()
                    fileName == ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME
                }
                .toList()
        }
    }
}
