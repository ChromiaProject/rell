/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.asSimpleComparison
import net.postchain.rell.toolbox.linter.isNullLiteral
import net.postchain.rell.toolbox.linter.issues.SimplifyNullableIfIssue
import net.postchain.rell.toolbox.linter.sourceText
import org.antlr.v4.runtime.tree.ParseTree

/**
 * Rewrites pure-expression null guards:
 *   `if (x != null) x else y`   → `x ?: y`
 *   `if (x != null) x.f else null` → `x?.f`
 */
class SimplifyNullableIfRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override fun visitIfExpr(ctx: RellParser.IfExprContext) {
        if (isDisabled(config.ruleSimplifyNullableIf) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val checked = notNullOperand(ctx.expression()) ?: return
        if (hasCall(checked)) {
            return
        }
        val arms = ctx.exprOrValueBlock()
        if (arms.size != 2) {
            return
        }
        val thenExpr = arms[0].expression() ?: return
        val elseExpr = arms[1].expression()
        val checkedText = checked.sourceText()
        val thenText = thenExpr.sourceText()

        // Case A - elvis: then-arm is exactly the checked expression.
        if (thenText == checkedText) {
            elseExpr ?: return
            // `?:` binds tighter than or/and/comparisons/in, so a compound (or lambda) else arm must
            // be parenthesized to keep its grouping: `f ?: a or b` would otherwise reparse as
            // `(f ?: a) or b`, and a bare lambda arm (`f ?: x -> x`) would not parse at all.
            val elseText = elseExpr.sourceText()
            val wrappedElse = if (elseArmNeedsParens(elseExpr)) "($elseText)" else elseText
            val replacement = "$checkedText ?: $wrappedElse"
            val newText = if (needsParens(ctx)) "($replacement)" else replacement
            report(SimplifyNullableIfIssue(ctx, ruleId, ELVIS_MESSAGE, newText))
            return
        }

        // Case B - safe access: then-arm is `x.<member>` (single member, no call) and else is `null`.
        if (elseExpr != null && elseExpr.text == "null" && thenText.startsWith(checkedText)) {
            val rest = thenText.substring(checkedText.length)
            if (SINGLE_MEMBER.matches(rest)) {
                report(SimplifyNullableIfIssue(ctx, ruleId, SAFE_ACCESS_MESSAGE, "$checkedText?$rest"))
            }
        }
    }

    /** The non-null operand of a `<expr> != null` / `null != <expr>` condition, else null. */
    private fun notNullOperand(cond: RellParser.ExpressionContext): RellParser.BaseExprContext? {
        val cmp = cond.binaryExpr()?.asSimpleComparison() ?: return null
        if (cmp.op.text != "!=") {
            return null
        }
        val leftNull = cmp.left.isNullLiteral()
        val rightNull = cmp.right.isNullLiteral()
        return when {
            rightNull && !leftNull -> cmp.left
            leftNull && !rightNull -> cmp.right
            else -> null
        }
    }

    private fun hasCall(node: ParseTree): Boolean {
        if (node is RellParser.CallArgsContext) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (hasCall(node.getChild(i))) {
                return true
            }
        }
        return false
    }

    // `?:` is a low-precedence binary operator; if the `if`-expr is combined with other operators
    // in the same (flat) binary expression, the elvis form must be parenthesized to preserve grouping.
    private fun needsParens(ctx: RellParser.IfExprContext): Boolean {
        val parent = ctx.parent as? RellParser.BinaryExprContext ?: return false
        return parent.childCount > 1
    }

    // The else arm becomes the `?:` right operand: it needs parentheses when it is a lambda or a
    // compound expression (any binary operator), all of which bind looser than `?:`.
    private fun elseArmNeedsParens(elseExpr: RellParser.ExpressionContext): Boolean {
        if (elseExpr.lambdaExpr() != null) return true
        val bin = elseExpr.binaryExpr() ?: return true
        return bin.childCount != 1
    }

    companion object {
        const val RULE_ID = "rule_simplify_nullable_if"
        private const val ELVIS_MESSAGE = "Replace 'if' with the elvis operator '?:'"
        private const val SAFE_ACCESS_MESSAGE = "Replace 'if' with safe-access '?.'"
        private val SINGLE_MEMBER = Regex("^\\.[A-Za-z_][A-Za-z0-9_]*$")
    }
}
