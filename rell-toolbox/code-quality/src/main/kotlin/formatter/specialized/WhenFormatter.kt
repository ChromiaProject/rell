/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.WhenConditionExprContext
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.WhenExprContext
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.WhenStmtAltContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer

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

class WhenExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<WhenExprContext> {
    override fun format(node: WhenExprContext, doc: FormattableDocument) {
        // whenExpr: 'when' ('(' expression ')')? '{' (whenCondition '->' expression) (';' whenCondition '->' expression)* ';'? '}'
        doc.interiorIndent(node)
        val whenTok = tokenAnalyzer.tokenFor(node, "when")
        if (whenTok != null) doc.append(whenTok) { it.oneSpace() }

        val exprs = node.expression()
        // The first expression *may* be the discriminator inside `(` `)`. We can't
        // distinguish positionally; format all expressions with newline+space rules
        // delegated below.

        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        if (openingCurly != null) doc.prepend(openingCurly) { it.oneSpace() }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        if (closingCurly != null) doc.prepend(closingCurly) { it.newLine() }

        // Each whenCondition + the following expression form a case.
        val conds = node.whenCondition()
        for (i in conds.indices) {
            val cond = conds[i]
            doc.prepend(cond) { it.newLine() }
            doc.append(cond) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(cond)
        }
        // Format expressions; they self-handle.
        exprs.forEach { doc.format(it) }
    }
}

class WhenCondExprFormatter : NodeFormatter<WhenConditionExprContext> {
    override fun format(node: WhenConditionExprContext, doc: FormattableDocument) {
        val expressions = node.expression()
        expressions.forEachIndexed { index, xExprRef ->
            doc.prepend(xExprRef) { it.oneSpace() }
            if (index == expressions.lastIndex) {
                doc.append(xExprRef) { it.noSpace() }
            }
            doc.format(xExprRef)
        }
    }
}
