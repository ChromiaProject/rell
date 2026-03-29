/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class RootNodeFormatter(
    private val expressionFormatter: ExpressionFormatter
) : NodeFormatter<RuleX_RootParserContext> {
    override fun format(node: RuleX_RootParserContext, doc: FormattableDocument) {
        doc.format(node.ruleX_ModuleHeader())
        expressionFormatter.formatOpeningClosingLines(node.ruleX_AnnotatedDef(), doc)
        node.ruleX_AnnotatedDef().forEach { xAnnotedDef ->
            doc.format(xAnnotedDef)
        }
    }
}

class MooduleHeaderFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ModuleHeaderContext> {
    override fun format(node: RuleX_ModuleHeaderContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        doc.prepend(node) { it.noSpace() }
        doc.append(node) {
            it.setNewLines(2)
            it.superHighPriority()
        }
    }
}

class ModifierFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ModifierContext> {
    override fun format(node: RuleX_ModifierContext, doc: FormattableDocument) {
        val annotation = node.ruleX_Annotation()
        doc.append(annotation) {
            it.newLine()
            it.highPriority()
        }
        node.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_0()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }

        node.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_1()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }

        node.ruleX_KeywordModifier()?.ruleX_KeywordModifier0()?.ruleX_Modifier_2()?.let {
            whitespaceFormatter.formatModifier(it, doc)
        }
    }
}
