package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.parser.RellCommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

interface FormattableDocument {
    fun append(appendAfterNode: ParserRuleContext?, changeModifier: (Changes) -> Unit)

    fun append(appendAfterNode: TerminalNode?, changeModifier: (Changes) -> Unit)

    fun prepend(prependBeforeNode: ParserRuleContext?, changeModifier: (Changes) -> Unit)

    fun prepend(prependBeforeNode: TerminalNode?, changeModifier: (Changes) -> Unit)

    fun surround(surroundNode: ParserRuleContext?, changeModifier: (Changes) -> Unit)

    fun surround(surroundNode: TerminalNode?, changeModifier: (Changes) -> Unit)

    fun format(node: ParserRuleContext?): ParserRuleContext?

    fun formatRellDocsComments(tokenStream: RellCommonTokenStream)

    fun interiorIndent(interiorNode: ParserRuleContext?)

    fun interiorIndentRange(startNode: ParserRuleContext?, endNode: ParserRuleContext?)

    fun interiorIndentRangeIncludeLast(startNode: ParserRuleContext?, endNode: ParserRuleContext?)
}
