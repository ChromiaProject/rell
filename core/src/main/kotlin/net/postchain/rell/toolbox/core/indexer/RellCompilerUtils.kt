package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import java.io.File
import java.net.URI

class RellCompilerUtils {

    fun createCompilerSourcePath(uri: URI, workspaceUri: URI): C_SourcePath {
        var relativePath = File(uri).relativeTo(File(workspaceUri)).toString()
        if (relativePath.isEmpty()) {
            relativePath = File(uri).name
        }

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

    fun createInvalidFileCompilationResult(compilerSrcPath: C_SourcePath): IdeCompilationResult {
        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        return IdeCompilationResult(
            listOf(
                C_Message(
                    type = C_MessageType.ERROR,
                    pos = S_BasicPos(C_ParserFilePath(compilerSrcPath, idePath), 0, 0, 0),
                    code = "",
                    text = "Relative workspace path contains '-', cannot compile."
                )
            ),
            mapOf()
        )
    }
}
