/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.parser.RellParser.*

class WhenStmtFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_WhenStmtContext> {
    override fun format(node: RuleX_WhenStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(node)
        doc.append(node.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(node.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(node.ruleX_ExpressionRef())
        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        for (whenCase in node.ruleX_WhenStmtCase()) {
            doc.prepend(whenCase) { it.newLine() }
            doc.append(whenCase.ruleX_WhenCondition()) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(whenCase.ruleX_WhenCondition())
            doc.prepend(whenCase.ruleX_StatementRef()) { it.oneSpace() }
            doc.format(whenCase.ruleX_StatementRef())
        }
        val closingCurly = node.ruleX_tkRCURL()
        doc.prepend(closingCurly) { it.newLine() }
    }
}

class WhenExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_WhenExprContext> {
    override fun format(node: RuleX_WhenExprContext, doc: FormattableDocument) {
        doc.interiorIndent(node)
        doc.append(node.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(node.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(node.ruleX_ExpressionRef())
        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
        node.ruleX_WhenExprCase()?.forEach { whenCase ->
            doc.format(whenCase)
        }
    }
}

class WhenCaseFormatter : NodeFormatter<RuleX_WhenExprCaseContext> {
    override fun format(node: RuleX_WhenExprCaseContext, doc: FormattableDocument) {
        doc.prepend(node) { it.newLine() }
        doc.append(node.ruleX_WhenCondition()) {
            it.oneSpace()
            it.highPriority()
        }
        doc.format(node.ruleX_WhenCondition())
        doc.prepend(node.ruleX_ExpressionRef()) { it.oneSpace() }
        doc.format(node.ruleX_ExpressionRef())
    }
}

class WhenCondExprFormatter : NodeFormatter<RuleX_WhenConditionExprContext> {
    override fun format(node: RuleX_WhenConditionExprContext, doc: FormattableDocument) {
        val expressions = node.ruleX_ExpressionRef()
        expressions.forEachIndexed { index, xExprRef ->
            doc.prepend(xExprRef) { it.oneSpace() }
            if (index == expressions.lastIndex) {
                doc.append(xExprRef) { it.noSpace() }
            }
            doc.format(xExprRef)
        }
    }
}
