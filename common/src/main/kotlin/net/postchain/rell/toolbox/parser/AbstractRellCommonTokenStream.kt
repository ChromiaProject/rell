package net.postchain.rell.toolbox.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token

abstract class AbstractRellCommonTokenStream(lexer: Lexer) : CommonTokenStream(lexer) {
    abstract fun getPreviousTokenOnChannel(tokenIndex: Int, channel: Int): Token?
    abstract fun getPreviousRellDocCommentToken(tokenIndex: Int): Token?
    abstract fun getCommentLineAboveToken(token: Token): Token?
    abstract fun isRellDocComment(token: Token?): Boolean
}