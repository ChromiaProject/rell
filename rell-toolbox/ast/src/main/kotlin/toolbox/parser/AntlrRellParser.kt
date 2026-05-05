/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.compiler.parser.antlr.RellParser.FileContext
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.tree.ParseTreeListener

/**
 * ANTLR-driven parser facade for Rell sources, backed by the canonical
 * `Rell.g4` grammar that lives in `:rell-base:frontend`.
 *
 * Phase 4 of the better-parse → ANTLR migration. The legacy auto-generated
 * `Rell.g4` grammar (with its `RuleX_*` rules) has been retired; consumers
 * should consume `FileContext` and the camelCase rule context types of the
 * new grammar.
 */
class AntlrRellParser {

    fun parse(
        source: String,
        parseListeners: List<ParseTreeListener> = listOf(),
        errorListeners: List<ANTLRErrorListener> = listOf()
    ): FileContext {
        val parser = parserFor(source, parseListeners, errorListeners)
        return parser.file()
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

        for (listener in parseListeners) {
            parser.addParseListener(listener)
        }

        for (listener in errorListeners) {
            parser.addErrorListener(listener)
        }

        return parser
    }
}
