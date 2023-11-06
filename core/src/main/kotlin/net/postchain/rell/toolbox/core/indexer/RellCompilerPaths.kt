package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import java.net.URI

class RellCompilerPaths(private val workspaceURI: URI) {

    fun createCompilerSourcePath(uri: URI): C_SourcePath {
        val relativePath = uri.toString().substring(workspaceURI.toString().length)
        val compilerSourcePath = IdeDirApi.parseSourcePath(relativePath)
        if (compilerSourcePath != null) {
            return compilerSourcePath
        } else {
            throw Exception("Could not create source path for file: $uri")
        }
    }

    fun createRellCompilerFilePath(compilerSourcePath: C_SourcePath): RellcFilePath {
        val idePath = IdeSourcePathFilePath(compilerSourcePath)
        return RellcFilePath(compilerSourcePath, idePath)
    }

}
