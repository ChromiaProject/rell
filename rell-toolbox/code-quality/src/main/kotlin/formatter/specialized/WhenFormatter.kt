/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import org.antlr.v4.runtime.ParserRuleContext

class WhenStmtFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<WhenStmtAltContext> {
    override fun format(node: WhenStmtAltContext, doc: FormattableDocument) {
        // whenStmtAlt: 'when' ('(' expression ')')? '{' (whenCondition '->' statement ';'?)* '}'
        doc.interiorIndent(node)
        val whenTok = tokenAnalyzer.tokenFor(node, "when")
        if (whenTok != null) doc.append(whenTok) { it.oneSpace() }
        node.expression()?.let { doc.surround(it) { c -> c.noSpace() }; doc.format(it) }
        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        if (openingCurly != null) doc.prepend(openingCurly) { it.oneSpace() }

        // Iterate through whenCondition + statement pairs.
        val conds = node.whenCondition()
        val stmts = node.statement()
        for (i in conds.indices) {
            val whenCond = conds[i]
            doc.prepend(whenCond) { it.newLine() }
            doc.append(whenCond) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(whenCond)
            stmts.getOrNull(i)?.let { stmt ->
                doc.prepend(stmt) { it.oneSpace() }
                doc.format(stmt)
            }
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        if (closingCurly != null) doc.prepend(closingCurly) { it.newLine() }
    }
}

/** Pairs a when-expr case's condition with its arm, or null if either is missing (error recovery). */
private fun caseOf(
    cond: WhenConditionContext?,
    valueBlock: ValueBlockContext?,
    expr: ExpressionContext?,
): Pair<WhenConditionContext, ParserRuleContext>? {
    val arm = valueBlock ?: expr ?: return null
    return cond?.let { it to arm }
}

class WhenExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<WhenExprContext> {
    override fun format(node: WhenExprContext, doc: FormattableDocument) {
        // whenExpr: 'when' ('(' expression ')')? '{' whenExprCase* whenExprLastCase '}'
        doc.interiorIndent(node)
        val whenTok = tokenAnalyzer.tokenFor(node, "when")
        if (whenTok != null) doc.append(whenTok) { it.oneSpace() }

        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        if (openingCurly != null) doc.prepend(openingCurly) { it.oneSpace() }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        if (closingCurly != null) doc.prepend(closingCurly) { it.newLine() }

        node.expression()?.let { doc.surround(it) { c -> c.noSpace() }; doc.format(it) }

        // Each case is a whenCondition '->' arm (valueBlock or expression). Under ANTLR error
        // recovery (e.g. an incomplete when-expr mid-edit), whenExprLastCase()/whenCondition()/
        // valueBlock()/expression() can all come back null - skip anything incomplete instead of
        // crashing the whole format request.
        val cases = node.whenExprCase().mapNotNull { caseOf(it.whenCondition(), it.valueBlock(), it.expression()) } +
            listOfNotNull(node.whenExprLastCase()?.let { caseOf(it.whenCondition(), it.valueBlock(), it.expression()) })
        for ((cond, arm) in cases) {
            doc.prepend(cond) { it.newLine() }
            doc.append(cond) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(cond)
            doc.format(arm)
        }
    }
}

class WhenCondExprFormatter : NodeFormatter<WhenConditionExprContext> {
    override fun format(node: WhenConditionExprContext, doc: FormattableDocument) {
        val expressions = node.binaryExpr()
        expressions.forEachIndexed { index, xExprRef ->
            doc.prepend(xExprRef) { it.oneSpace() }
            if (index == expressions.lastIndex) {
                doc.append(xExprRef) { it.noSpace() }
            }
            doc.format(xExprRef)
        }
    }
}
