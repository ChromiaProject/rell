/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath
import kotlin.io.path.walk

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
        fun findIndexRoots(workspaceFolderUri: URI): List<IndexRoot> =
            findChromiaConfigFiles(workspacePath = workspaceFolderUri.toPath())
                .map(::fromChromiaConfig)
                .toList()

        fun fromChromiaConfig(chromiaConfigPath: Path): IndexRoot =
            IndexRoot(chromiaConfigPath, WorkspaceDirectoryResolver.findSourceDirPathFromConfig(chromiaConfigPath))

        private fun findChromiaConfigFiles(workspacePath: Path) = workspacePath
            .walk()
            .filter { it.fileName.toString() == ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME }
    }
}
