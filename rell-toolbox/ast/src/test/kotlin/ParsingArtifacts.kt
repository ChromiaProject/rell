/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath

data class ParsingArtifacts(
    val sourcePath: C_SourcePath,
    val idePath: IdeSourcePathFilePath,
    val transformedAst: S_RellFile,
    val sourceCode: String,
)
