/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser.antlr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeFilePath
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class AntlrPos(
    val node: ParserRuleContext,
    val path: C_SourcePath,
    val idePath: IdeFilePath,
    private val tokenOverride: Token? = null,
) : S_Pos() {
    override fun path(): C_SourcePath = path
    override fun idePath(): IdeFilePath = idePath
    override fun line(): Int = (tokenOverride ?: node.start).line
    override fun offset(): Int = (tokenOverride ?: node.start).startIndex
    override fun column(): Int = (tokenOverride ?: node.start).charPositionInLine + 1

    override fun equals(other: Any?): Boolean =
        other is AntlrPos && line() == other.line() && column() == other.column() && idePath == other.idePath

    override fun hashCode(): Int = arrayOf(line(), column(), idePath, offset()).contentHashCode()
}
