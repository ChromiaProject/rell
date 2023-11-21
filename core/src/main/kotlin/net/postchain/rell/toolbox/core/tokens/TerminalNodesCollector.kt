package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.toolbox.core.parser.RellBaseListener
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode

class TerminalNodesCollector(val tree: ParseTree?) : RellBaseListener() {
    private val terminalNodes = mutableListOf<TerminalNode>()
    override fun visitTerminal(node: TerminalNode) {
        terminalNodes.add(node)
    }

    fun getTokens(): List<TerminalNode> {
        val walker = ParseTreeWalker()
        walker.walk(this, tree)
        return terminalNodes
    }
}