package net.postchain.rell.toolbox.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token

class RellCommonTokenStream(lexer: RellLexer) : AbstractRellCommonTokenStream(lexer) {
    override fun getPreviousTokenOnChannel(tokenIndex: Int, channel: Int): Token? {
        if (tokenIndex <= 0) return null
        val prevOnChannel = previousTokenOnChannel(tokenIndex - 1, channel)
        return if (prevOnChannel != -1) tokens[prevOnChannel] else null
    }

    override fun getPreviousRellDocCommentToken(tokenIndex: Int): Token? {
        if (tokenIndex < 0 || tokenIndex >= tokens.size || get(tokenIndex).channel != RellLexer.DEFAULT_TOKEN_CHANNEL) {
            return null
        }
        return getPreviousTokenOnChannel(tokenIndex, RellCustomTokenChannels.COMMENTS.channel)?.takeIf {
            nextTokenOnChannel(it.tokenIndex, RellLexer.DEFAULT_TOKEN_CHANNEL) == tokenIndex && isRellDocComment(it)
        }
    }

    override fun getCommentLineAboveToken(token: Token): Token? {
        val comment = getPreviousTokenOnChannel(token.tokenIndex, RellCustomTokenChannels.COMMENTS.channel)
        return if (comment?.line == token.line - 1) comment else null
    }

    override fun isRellDocComment(token: Token?): Boolean {
        if (token == null || token.type != RellLexer.RULE_ML_COMMENT) {
            return false
        }
        val text = token.text
        return text.startsWith("/**") && text.endsWith("*/") && text.length >= 5
    }
}