/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.toolbox.parser.RellParser
import net.postchain.rell.toolbox.parser.SyntaxError

data class ParsingResult(
    val parseTree: RellParser.RuleX_RootParserContext,
    val syntaxErrors: MutableList<SyntaxError>,
    val parser: RellParser
)
