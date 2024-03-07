package net.postchain.rell.toolbox.core.parser

import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_RootParserContext
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
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

    private fun parserFor(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): RellParser {
        val input: CharStream = CharStreams.fromString(source)
        val lexer = RellLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = RellParser(tokens)

        parser.removeErrorListeners()
        parser.removeParseListeners()
        parseListeners.forEach { parser.addParseListener(it) }
        errorListeners.forEach { parser.addErrorListener(it) }

        return parser
    }
}