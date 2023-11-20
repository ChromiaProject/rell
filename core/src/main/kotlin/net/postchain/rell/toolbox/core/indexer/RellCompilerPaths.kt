package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import java.io.File
import java.net.URI

class RellCompilerPaths(private val workspaceUri: URI) {

    fun createCompilerSourcePath(uri: URI): C_SourcePath {
        // TODO: Make sure line below works. Old line: val relativePath = uri.toString().substring(workspaceURI.toString().length)
        val relativePath = File(uri).relativeTo(File(workspaceUri)).toString()
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
