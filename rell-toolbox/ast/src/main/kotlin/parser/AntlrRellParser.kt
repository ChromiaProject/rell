/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import net.postchain.rell.base.compiler.parser.RellParserErrorStrategy
import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.compiler.parser.antlr.RellParser.FileContext
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.tree.ParseTreeListener

class AntlrRellParser {
    fun parse(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): FileContext = parserFor(source, parseListeners, errorListeners).file()

    fun parserFor(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): RellParser = RellParser(RellCommonTokenStream(RellLexer(CharStreams.fromString(source)))).apply {
        removeErrorListeners()
        removeParseListeners()
        errorHandler = RellParserErrorStrategy()

        for (listener in parseListeners) {
            addParseListener(listener)
        }

        for (listener in errorListeners) {
            addErrorListener(listener)
        }
    }
}
