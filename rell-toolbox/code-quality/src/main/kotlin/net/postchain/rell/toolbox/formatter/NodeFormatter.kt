package net.postchain.rell.toolbox.formatter

import org.antlr.v4.runtime.ParserRuleContext

interface NodeFormatter<T : ParserRuleContext> {
    fun format(node: T, doc: FormattableDocument)
}
