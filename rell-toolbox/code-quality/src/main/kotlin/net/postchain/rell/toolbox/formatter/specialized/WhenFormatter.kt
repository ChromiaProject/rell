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
    override fun format(xWhenStmt: RuleX_WhenStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(xWhenStmt)
        doc.append(xWhenStmt.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(xWhenStmt.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(xWhenStmt.ruleX_ExpressionRef())
        val openingCurly = tokenAnalyzer.tokenFor(xWhenStmt, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        for (whenCase in xWhenStmt.ruleX_WhenStmtCase()) {
            doc.prepend(whenCase) { it.newLine() }
            doc.append(whenCase.ruleX_WhenCondition()) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(whenCase.ruleX_WhenCondition())
            doc.prepend(whenCase.ruleX_StatementRef()) { it.oneSpace() }
            doc.format(whenCase.ruleX_StatementRef())
        }
        val closingCurly = xWhenStmt.ruleX_tkRCURL()
        doc.prepend(closingCurly) { it.newLine() }
    }
}

class WhenExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_WhenExprContext> {
    override fun format(xWhenExpr: RuleX_WhenExprContext, doc: FormattableDocument) {
        doc.interiorIndent(xWhenExpr)
        doc.append(xWhenExpr.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(xWhenExpr.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(xWhenExpr.ruleX_ExpressionRef())
        val openingCurly = tokenAnalyzer.tokenFor(xWhenExpr, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        val closingCurly = tokenAnalyzer.tokenFor(xWhenExpr, "}")
        doc.prepend(closingCurly) { it.newLine() }
        xWhenExpr.ruleX_WhenExprCase()?.forEach { whenCase ->
            doc.format(whenCase)
        }
    }
}

class WhenCaseFormatter : NodeFormatter<RuleX_WhenExprCaseContext> {
    override fun format(whenCase: RuleX_WhenExprCaseContext, doc: FormattableDocument) {
        doc.prepend(whenCase) { it.newLine() }
        doc.append(whenCase.ruleX_WhenCondition()) {
            it.oneSpace()
            it.highPriority()
        }
        doc.format(whenCase.ruleX_WhenCondition())
        doc.prepend(whenCase.ruleX_ExpressionRef()) { it.oneSpace() }
        doc.format(whenCase.ruleX_ExpressionRef())
    }
}

class WhenCondExprFormatter : NodeFormatter<RuleX_WhenConditionExprContext> {
    override fun format(xWhenCondExpr: RuleX_WhenConditionExprContext, doc: FormattableDocument) {
        val expressions = xWhenCondExpr.ruleX_ExpressionRef()
        expressions.forEachIndexed { index, xExprRef ->
            doc.prepend(xExprRef) { it.oneSpace() }
            if (index == expressions.lastIndex) {
                doc.append(xExprRef) { it.noSpace() }
            }
            doc.format(xExprRef)
        }
    }
}
