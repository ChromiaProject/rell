package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError

data class ParsingResult(
    val parseTree: RellParser.RuleX_RootParserContext,
    val syntaxErrors: MutableList<SyntaxError>,
    val parser: RellParser
)
