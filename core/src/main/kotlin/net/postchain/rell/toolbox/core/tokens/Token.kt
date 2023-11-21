package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind
import org.antlr.v4.runtime.tree.TerminalNode

class Token(symbKind: IdeSymbolKind, node: TerminalNode) : Comparable<Token> {
    val line =  node.symbol.line - 1
    val col = node.symbol.charPositionInLine
    val len = node.symbol.text.length
    val tokenType = RellSymbolKind.forIdeKind(symbKind).numId

    override operator fun compareTo(other: Token): Int {
        var diff = line.compareTo(other.line)
        if (diff == 0) diff = col.compareTo(other.col)
        return diff
    }
}