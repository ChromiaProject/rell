/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.parser.RellCustomTokenChannels
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

internal class TokenAnalyzer(private val parser: RellParser) {
    fun tokenFor(node: ParserRuleContext?, tokenText: String): TerminalNode? {
        if (node == null) return null

        for (i in 0..<node.childCount) {
            val child = node.getChild(i)

            if (child is TerminalNode) {
                if (child.symbol.text == tokenText) return child
            }
        }

        for (i in 0..<node.childCount) {
            val child = node.getChild(i)

            if (child is ParserRuleContext) {
                val token = tokenFor(child, tokenText)
                if (token != null) return token
            }
        }

        return null
    }

    /**
     * Find a direct (non-recursive) child terminal node whose text matches [tokenText].
     * Useful when a recursive search would dig into nested rule contexts that may also
     * contain the same token.
     */
    fun directTokenFor(node: ParserRuleContext?, tokenText: String): TerminalNode? {
        if (node == null) return null

        for (i in 0..<node.childCount) {
            val child = node.getChild(i)
            if (child is TerminalNode && child.symbol.text == tokenText) return child
        }

        return null
    }

    fun nextSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null

        return try {
            commonTokenStream[token.tokenIndex + 1]
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun previousSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex <= 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null

        return try {
            commonTokenStream[token.tokenIndex - 1]
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun nextHiddenRegion(token: Token): Token? = hiddenTokensToRight(token, RellLexer.HIDDEN)?.firstOrNull()
    fun previousHiddenRegion(token: Token): Token? = hiddenTokensToLeft(token, RellLexer.HIDDEN)?.lastOrNull()

    fun nextCommentRegion(token: Token): Token? =
        hiddenTokensToRight(token, RellCustomTokenChannels.COMMENTS.channel)?.firstOrNull()

    fun previousCommentRegion(token: Token): Token? =
        hiddenTokensToLeft(token, RellCustomTokenChannels.COMMENTS.channel)?.lastOrNull()

    fun previousHiddenRegionList(token: Token): List<Token> = hiddenTokensToLeft(token, RellLexer.HIDDEN) ?: listOf()

    /**
     * Error recovery synthesizes "missing" tokens with tokenIndex == -1 (e.g. the name of
     * `entity { }`); asking the stream about their neighbours must yield nothing, not throw.
     */
    private fun hiddenTokensToLeft(token: Token, channel: Int): List<Token>? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, channel)
    }

    private fun hiddenTokensToRight(token: Token, channel: Int): List<Token>? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return commonTokenStream.getHiddenTokensToRight(token.tokenIndex, channel)
    }
}
