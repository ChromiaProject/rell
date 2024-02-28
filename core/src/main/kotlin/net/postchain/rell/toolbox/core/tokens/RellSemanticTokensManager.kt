package net.postchain.rell.toolbox.core.tokens


import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.core.indexer.NodeInterval
import net.postchain.rell.toolbox.core.indexer.Resource

class RellSemanticTokensManager {

    private val supportedModifiers = RellTokenModifier.entries

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
                    getModifierId(token.tokenType.modifiersAsList)
                )
            )
            activeLine = token.line
            activeColumn = token.col
        }
        return tokensRelative.flatMap { it.toImmList() }
    }

    //A token can have multiple modifiers, each combination of modifiers needs to be encoded
    //as a single integer to follow the Language Server Protocol
    //https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens
    private fun getModifierId(tokensModifiers: List<RellTokenModifier>): Int {
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
                for (type in RellTokenType.entries) {
                    tokenTypes.add(type.tokenStringId)
                }
                return tokenTypes
            }

        val tokenModifiers: List<String>
            get() {
                val tokenModifiers: MutableList<String> = ArrayList()
                for (modifier in RellTokenModifier.entries) {
                    tokenModifiers.add(modifier.modifierStringId)
                }
                return tokenModifiers
            }
    }
}

