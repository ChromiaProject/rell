package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.parser.RellCustomTokenChannels
import net.postchain.rell.toolbox.parser.RellLexer
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

class TokenAnalyzer(private val parser: RellParser) {

    fun tokenFor(node: ParserRuleContext?, tokenText: String): TerminalNode? {
        if (node == null) {
            return null
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol
                if (token.text == tokenText) {
                    return child
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is ParserRuleContext) {
                val token = tokenFor(child, tokenText)
                if (token != null) {
                    return token
                }
            }
        }
        return null
    }

    fun nextSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return try {
            commonTokenStream.get(token.tokenIndex + 1)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun previousSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex <= 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return try {
            commonTokenStream.get(token.tokenIndex - 1)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun nextHiddenRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToRight(token.tokenIndex, RellLexer.HIDDEN)?.firstOrNull()
    }

    fun previousHiddenRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellLexer.HIDDEN)?.lastOrNull()
    }

    fun previousCommentRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellCustomTokenChannels.COMMENTS.channel)
            ?.lastOrNull()
    }

    fun previousHiddenRegionList(token: Token): List<Token> {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellLexer.HIDDEN) ?: listOf()
    }
}