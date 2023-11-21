package net.postchain.rell.toolbox.core.tokens


import com.google.common.collect.ImmutableList
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.core.indexer.Resource
import java.util.*


class RellSemanticTokensManager {

    fun getRelativeSemanticTokens(resource: Resource): List<Int> {
        val tokens = getSemanticTokens(resource)
        return relativeTokens(tokens)
    }

    fun getSemanticTokens(resource: Resource): MutableList<Token> {
        val parseTree = resource.parseTree
        val tokenNodes = TerminalNodesCollector(parseTree).getTokens()

        val tokens = mutableListOf<Token>()
        for (node in tokenNodes) {
            val symbolKind = resource.getSymbolKindForInterval(node.sourceInterval)
            if (symbolKind != null) {
                tokens.add(Token(symbolKind, node))
            }
        }

        return tokens
    }

    // The position of the tokens in the response needs to be the position
    // relative to the previous token, and returned as a flattened list
    private fun relativeTokens(tokens: MutableList<Token>): List<Int> {
        val tokensRelative = mutableListOf<List<Int>>()
        tokens.sort()
        var activeLine = 0
        var activeColumn = 0
        for (token in tokens) {
            val deltaLine = token.line - activeLine
            val deltaColumn = if (deltaLine == 0) token.col - activeColumn else token.col
            tokensRelative.add(listOf(deltaLine, deltaColumn, token.len, token.tokenType, 0))
            activeLine = token.line
            activeColumn = token.col
        }
        return tokensRelative.flatMap { it.toImmList() }
    }



    companion object {
        val tokenTypes: List<String>
            get() {
                val tokenTypes: MutableList<String> = ArrayList()
                for (kind in RellSymbolKind.entries) {
                    tokenTypes.add(kind.lspId)
                }
                return ImmutableList.copyOf(tokenTypes)
            }
    }
}

