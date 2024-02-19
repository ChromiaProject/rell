package net.postchain.rell.toolbox.core.tokens


import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.core.indexer.NodeInterval
import net.postchain.rell.toolbox.core.indexer.Resource

data class SemanticTokenInfo(val tokenTypes: List<String>, val tokenModifiers: List<String>)

class RellSemanticTokensManager {

    private val supportedModifiers = RellSymbolModifier.entries.sortedBy { it.modifierStringId }

    fun getRelativeSemanticTokens(resource: Resource): List<Int> {
        val tokens = getSemanticTokens(resource)
        return relativeTokens(tokens)
    }

    fun getSemanticTokens(resource: Resource): MutableList<Token> {
        val parseTree = resource.parseTree
        val tokenNodes = TerminalNodesCollector(parseTree).getTokens()

        val tokens = mutableListOf<Token>()
        for (node in tokenNodes) {
            val symbolKind = resource.getSymbolKindForInterval(NodeInterval(node))
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
            tokensRelative.add(
                listOf(
                    deltaLine,
                    deltaColumn,
                    token.len,
                    token.tokenType.tokenId,
                    getModifierValue(token.tokenType.modifiers)
                )
            )
            activeLine = token.line
            activeColumn = token.col
        }
        return tokensRelative.flatMap { it.toImmList() }
    }

    private fun getModifierValue(tokensModifiers: List<RellSymbolModifier>): Int {
        if (tokensModifiers.isEmpty()) return 0
        var bitmask = 0
        for (modifier in tokensModifiers) {
            val index = supportedModifiers.indexOf(modifier)
            if (index != -1) {
                bitmask = bitmask or (1 shl index)
            }
        }
        return bitmask
    }


    companion object {
        val semanticTokens: List<String>
            get() {
                val tokenTypes: MutableList<String> = ArrayList()
                for (type in RellSymbolKind.entries) {
                    tokenTypes.add(type.tokenStringId)
                }
                return tokenTypes
            }

        val tokenModifiers: List<String>
            get() {
                val tokenModifiers: MutableList<String> = ArrayList()
                for (modifier in RellSymbolModifier.entries.sortedBy { it.modifierStringId }) {
                    tokenModifiers.add(modifier.modifierStringId)
                }
                return tokenModifiers
            }
    }
}

