package net.postchain.rell.toolbox.core.compiler

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeFilePath
import org.antlr.v4.runtime.ParserRuleContext

class AntlrPos(val node: ParserRuleContext, val path: C_SourcePath, val idePath: IdeFilePath) : S_Pos() {

    override fun path(): C_SourcePath {
        return path
    }

    override fun idePath(): IdeFilePath {
        return idePath
    }

    override fun line(): Int {
        return node.start.line
    }

    override fun column(): Int {
        return node.start.charPositionInLine + 1
    }
}
