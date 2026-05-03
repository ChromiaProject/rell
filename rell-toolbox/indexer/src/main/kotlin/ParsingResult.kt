/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.toolbox.parser.SyntaxError
import org.antlr.v4.runtime.BufferedTokenStream

/**
 * Result of parsing a Rell source. Backed by the canonical `RellManual.g4` grammar
 * in `:rell-base:frontend`; the parse tree is consumed by the visitor that builds
 * `S_RellFile` and by downstream tools (formatter/linter/language-server).
 */
data class ParsingResult(
    val parseTree: RellManualParser.FileContext,
    val syntaxErrors: List<SyntaxError>,
    val tokenStream: BufferedTokenStream?,
)
