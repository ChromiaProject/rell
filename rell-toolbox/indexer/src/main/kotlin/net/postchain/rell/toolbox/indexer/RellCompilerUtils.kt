/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.toolbox.compiler.RellCompilerFilePath
import java.io.File
import java.net.URI

class RellCompilerUtils {

    fun createCompilerSourcePath(uri: URI, workspaceUri: URI): C_SourcePath {
        var relativePath = File(uri).relativeTo(File(workspaceUri)).toString()
        if (relativePath.isEmpty()) {
            relativePath = File(uri).name
        }

        val compilerSourcePath = IdeDirApi.parseSourcePath(relativePath)
        check(compilerSourcePath != null) { "Could not create source path for file: $uri" }
        return compilerSourcePath
    }

    fun createRellCompilerFilePath(compilerSourcePath: C_SourcePath): RellCompilerFilePath {
        val idePath = IdeSourcePathFilePath(compilerSourcePath)
        return RellCompilerFilePath(compilerSourcePath, idePath)
    }

    fun createInvalidFileCompilationResult(compilerSrcPath: C_SourcePath): IdeCompilationResult {
        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        return IdeCompilationResult(
            immListOf(
                C_Message(
                    type = C_MessageType.ERROR,
                    pos = S_BasicPos(C_ParserFilePath(compilerSrcPath, idePath), 0, 0, 0),
                    code = "",
                    text = "Relative workspace path contains '-', cannot compile."
                )
            ),
            immMapOf()
        )
    }
}
