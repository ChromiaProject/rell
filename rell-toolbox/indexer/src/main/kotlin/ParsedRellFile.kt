/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.toolbox.compiler.AstSourceFile

/**
 * A file's ANTLR parse together with the AST built from it.
 *
 * Indexing needs both the source map entry and the per-file resource for the same file, and
 * previously built each from its own parse. The AST carries no compilation state, so a single
 * parse can serve both.
 */
internal class ParsedRellFile(
    val sourcePath: C_SourcePath,
    val parseResult: ParsingResult,
    val ast: S_RellFile,
) {
    fun toSourceFile(fileContent: String): C_SourceFile =
        AstSourceFile.make(ast, IdeSourcePathFilePath(sourcePath), fileContent)
}
