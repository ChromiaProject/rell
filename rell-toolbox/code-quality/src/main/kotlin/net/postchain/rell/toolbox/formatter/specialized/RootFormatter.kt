package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class RootNodeFormatter(
    private val expressionFormatter: ExpressionFormatter
) : NodeFormatter<RuleX_RootParserContext> {
    override fun format(rootNode: RuleX_RootParserContext, doc: FormattableDocument) {
        doc.format(rootNode.ruleX_ModuleHeader())
        expressionFormatter.formatOpeningClosingLines(rootNode.ruleX_AnnotatedDef(), doc)
        rootNode.ruleX_AnnotatedDef().forEach { xAnnotedDef ->
            doc.format(xAnnotedDef)
        }
    }
}

class MooduleHeaderFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ModuleHeaderContext> {
    override fun format(xRootParser: RuleX_ModuleHeaderContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(xRootParser, doc)
        doc.prepend(xRootParser) { it.noSpace() }
        doc.append(xRootParser) {
            it.setNewLines(2)
            it.superHighPriority()
        }
    }
}

class ModifierFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ModifierContext> {
    override fun format(xModifier: RuleX_ModifierContext, doc: FormattableDocument) {
        val annotation = xModifier.ruleX_Annotation()
        doc.append(annotation) {
            it.newLine()
            it.highPriority()
        }
        xModifier.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_0()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }

        xModifier.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_1()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }

        xModifier.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_2()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }
    }
}
