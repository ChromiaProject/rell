/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.parser.RellParser.*
import org.antlr.v4.runtime.ParserRuleContext


class BaseExprFormatter(
    private val expressionFormatter: ExpressionFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_BaseExprContext> {
    override fun format(xBaseExpr: RuleX_BaseExprContext, doc: FormattableDocument) {
        val exprHead = xBaseExpr.ruleX_BaseExprHead()
        val exprTailList = xBaseExpr.ruleX_BaseExprTail()
        var shouldLineSeparateExpression: Boolean
        var previousExpr: ParserRuleContext? = exprHead

        doc.append(exprHead) {
            it.noSpace()
            it.lowPriority()
        }

        if (exprTailList.isNotEmpty()) {
            expressionFormatter.indentExpressionTail(exprHead, exprTailList, doc)
        }

        for (i in 0 until exprTailList.size) {
            val currentExpr = exprTailList[i]
            if (i > 0) {
                previousExpr = exprTailList[i - 1]
                shouldLineSeparateExpression = lineAnalyzer.lineSeparateExpr(currentExpr, previousExpr)
            } else {
                shouldLineSeparateExpression = lineAnalyzer.lineSeparateExpr(currentExpr, exprHead)
            }
            if (shouldLineSeparateExpression) {
                expressionFormatter.formatExprTailMultiline(currentExpr, previousExpr, doc)
            } else {
                expressionFormatter.formatExprTailSingleline(currentExpr, doc)
            }
        }
        doc.format(exprHead)
    }
}
