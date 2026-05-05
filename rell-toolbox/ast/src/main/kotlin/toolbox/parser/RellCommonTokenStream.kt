/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token

/**
 * Common token stream used across both the legacy `Rell.g4` and the canonical `RellManual.g4`
 * lexers. The relevant token-type constants (`RULE_ML_COMMENT`, `RULE_SL_COMMENT`, etc.) and the
 * `COMMENTS` channel are identical between the two grammars, so we resolve the comment-token id
 * at construction time from the lexer's token-type map.
 */
class RellCommonTokenStream(lexer: Lexer) : AbstractRellCommonTokenStream(lexer) {

    private val mlCommentType: Int = lexer.tokenTypeMap["RULE_ML_COMMENT"] ?: -1

    override fun getPreviousTokenOnChannel(tokenIndex: Int, channel: Int): Token? {
        if (tokenIndex <= 0) return null
        val prevOnChannel = previousTokenOnChannel(tokenIndex - 1, channel)
        return if (prevOnChannel != -1) tokens[prevOnChannel] else null
    }

    override fun getPreviousRellDocCommentToken(tokenIndex: Int): Token? {
        if (tokenIndex < 0 || tokenIndex >= tokens.size || get(tokenIndex).channel != Lexer.DEFAULT_TOKEN_CHANNEL) {
            return null
        }

        return getPreviousTokenOnChannel(tokenIndex, RellCustomTokenChannels.COMMENTS.channel)?.takeIf {
            nextTokenOnChannel(it.tokenIndex, Lexer.DEFAULT_TOKEN_CHANNEL) == tokenIndex && isRellDocComment(it)
        }
    }

    override fun getCommentLineAboveToken(token: Token): Token? {
        val comment = getPreviousTokenOnChannel(token.tokenIndex, RellCustomTokenChannels.COMMENTS.channel)
        return if (comment?.line == token.line - 1) comment else null
    }

    override fun isRellDocComment(token: Token?): Boolean {
        if (token == null || token.type != mlCommentType) {
            return false
        }

        val text = token.text
        return text.startsWith("/**") && text.endsWith("*/") && text.length >= MINIMUM_DOC_COMMENT_LENGTH
    }

    companion object {
        private const val MINIMUM_DOC_COMMENT_LENGTH = 5
    }
}
