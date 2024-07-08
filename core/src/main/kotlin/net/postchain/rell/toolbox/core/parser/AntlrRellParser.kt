package net.postchain.rell.toolbox.core.parser

import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_RootParserContext
import net.postchain.rell.toolbox.core.tokens.RellCustomTokenChannels
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTreeListener

class AntlrRellParser {

    fun parse(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): RuleX_RootParserContext {
        val parser = parserFor(source, parseListeners, errorListeners)
        return parser.ruleX_RootParser()
    }

    fun parserFor(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): RellParser {
        val input: CharStream = CharStreams.fromString(source)
        val lexer = RellLexer(input)
        val tokens = RellCommonTokenStream(lexer)
        val parser = RellParser(tokens)

        parser.removeErrorListeners()
        parser.removeParseListeners()
        parseListeners.forEach { parser.addParseListener(it) }
        errorListeners.forEach { parser.addErrorListener(it) }

        return parser
    }
}

class RellCommonTokenStream(lexer: RellLexer) : CommonTokenStream(lexer) {
    fun getPreviousTokenOnChannel(tokenIndex: Int, channel: Int): Token? {
        if (tokenIndex <= 0) return null
        val prevOnChannel = previousTokenOnChannel(tokenIndex - 1, channel)
        return if (prevOnChannel != -1) tokens[prevOnChannel] else null
    }

    fun getPreviousRellDocCommentToken(tokenIndex: Int): Token? {
        if (tokenIndex < 0 || tokenIndex >= tokens.size || get(tokenIndex).channel != RellLexer.DEFAULT_TOKEN_CHANNEL) {
            return null
        }
        return getPreviousTokenOnChannel(tokenIndex, RellCustomTokenChannels.COMMENTS.channel)?.takeIf {
            nextTokenOnChannel(it.tokenIndex, RellLexer.DEFAULT_TOKEN_CHANNEL) == tokenIndex && isRellDocComment(it)
        }
    }

    fun getCommentLineAboveToken(token: Token): Token? {
        val comment = getPreviousTokenOnChannel(token.tokenIndex, RellCustomTokenChannels.COMMENTS.channel)
        return if (comment?.line == token.line - 1) comment else null
    }

    fun isRellDocComment(token: Token?): Boolean {
        if (token == null || token.type != RellLexer.RULE_ML_COMMENT) {
            return false
        }
        val text = token.text
        return text.startsWith("/**") && text.endsWith("*/") && text.length >= 5
    }
}