/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.asArgFreeMethodCall
import net.postchain.rell.toolbox.linter.asIntLiteral
import net.postchain.rell.toolbox.linter.asSimpleComparison
import net.postchain.rell.toolbox.linter.containsAtOperation
import net.postchain.rell.toolbox.linter.singleBaseExpr
import net.postchain.rell.toolbox.linter.issues.PreferEmptyIssue
import org.antlr.v4.runtime.RuleContext

/**
 * Flags collection existence checks written through `.size()`:
 * `c.size() == 0` → `c.empty()`, `c.size() > 0` → `not c.empty()`, and equivalents.
 *
 * Receivers that are themselves at-expressions (`entity @* {...}.size()`) are left to
 * [AtCardinalityMisuseRule], which suggests a cheaper cardinality-based check.
 *
 * The rule is syntactic and does not resolve receiver types, so it assumes every `.size()`
 * receiver also has `.empty()`. That holds for every collection type plus `text`, but not for the
 * rare `json.size()` (json has no `.empty()`) or a user function named `size`; on those receivers
 * the fix produces a compile error the editor surfaces immediately. A type-aware check is Wave 2.
 */
internal class PreferEmptyRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.BinaryExprContext::class.java,
    )

    override fun visitBinaryExpr(ctx: RellParser.BinaryExprContext) {
        if (isDisabled(config.rulePreferEmpty) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val cmp = ctx.asSimpleComparison() ?: return
        val op = cmp.op.text

        val leftRecv = sizeCallReceiver(cmp.left)
        val rightRecv = sizeCallReceiver(cmp.right)
        // Normalize to `size() OP literal`; if size() is on the right, flip the operator.
        val (recvText, literal, normalizedOp) = when {
            leftRecv != null && cmp.right.asIntLiteral() != null ->
                Triple(leftRecv, cmp.right.asIntLiteral()!!, op)
            rightRecv != null && cmp.left.asIntLiteral() != null ->
                Triple(rightRecv, cmp.left.asIntLiteral()!!, flip(op))
            else -> return
        }
        val empty = emptinessFor(normalizedOp, literal) ?: return
        report(PreferEmptyIssue(ctx, ruleId, "Use '.empty()' instead of a '.size()' comparison", recvText, empty))
    }

    /** Receiver source text if [expr] is `<recv>.size()` on a non-at-expression receiver, else null. */
    private fun sizeCallReceiver(expr: RellParser.BaseExprContext): String? {
        val call = expr.asArgFreeMethodCall() ?: return null
        if (call.name != "size") return null
        // At-expression receivers are handled by rule_at_cardinality_misuse.
        if (receiverIsAtExpr(expr)) return null
        return call.receiverText
    }

    private fun receiverIsAtExpr(expr: RellParser.BaseExprContext): Boolean {
        val head = expr.baseExprHead()
        if (head is RellParser.TupleHeadContext) {
            val inner = head.expression().singleOrNull()?.binaryExpr()?.singleBaseExpr()
            if (inner != null && inner.containsAtOperation()) return true
        }
        return expr.containsAtOperation()
    }

    /** true = `.empty()`, false = `not .empty()`, null = not an empty/non-empty check. */
    private fun emptinessFor(op: String, literal: Long): Boolean? = when {
        op == "==" && literal == 0L -> true
        op == "!=" && literal == 0L -> false
        op == "<=" && literal == 0L -> true
        op == "<" && literal == 1L -> true
        op == ">" && literal == 0L -> false
        op == ">=" && literal == 1L -> false
        else -> null
    }

    private fun flip(op: String): String = when (op) {
        "<" -> ">"
        ">" -> "<"
        "<=" -> ">="
        ">=" -> "<="
        else -> op
    }

    companion object {
        const val RULE_ID = "rule_prefer_empty"
    }
}
