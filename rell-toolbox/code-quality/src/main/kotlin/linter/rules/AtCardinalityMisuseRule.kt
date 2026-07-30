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
import net.postchain.rell.toolbox.linter.issues.AtCardinalityMisuseIssue
import net.postchain.rell.toolbox.linter.issues.NotNullToUnitFix
import net.postchain.rell.toolbox.linter.singleBaseExpr
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Flags at-expression cardinality smells:
 *  - `entity @? {...}!!`                          → `entity @ {...}` (semantics-preserving; auto-fixed)
 *  - `(entity @* {...})[0]`                       → use `@` / `@?` instead of indexing a list (report)
 *  - `(entity @* {...}).size() == 0` / `.empty()` → existence check; use `@?` + null-check or `exists()` (report)
 *  - `(entity @+ {...}).size() == 0` / `.empty()` → `@+` is always non-empty (report)
 */
internal class AtCardinalityMisuseRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.BaseExprContext::class.java,
        RellParser.BinaryExprContext::class.java,
    )

    override fun visitBaseExpr(ctx: RellParser.BaseExprContext) {
        if (isDisabled(config.ruleAtCardinalityMisuse) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }

        // (c) (entity @* {...})[0] - at-expr wrapped in parentheses and indexed at [0].
        if (isIndexedParenListAt(ctx)) {
            report(AtCardinalityMisuseIssue(ctx, ruleId, INDEXED_MESSAGE, null))
            return
        }

        // (d)/(e) `(entity @* {...}).empty()` - emptiness check on a list at-expr.
        parenListAtCall(ctx, "empty")?.let { card ->
            report(AtCardinalityMisuseIssue(ctx, ruleId, existenceMessage(card), null))
            return
        }

        // (b) entity @? {...}!! -> entity @ {...}
        val seg = atSegment(ctx) ?: return
        val qToken = seg.qToken
        val firstPost = seg.postOps.firstOrNull()
        if (seg.cardSymbol == "?" && qToken != null && firstPost is RellParser.BaseExprTailNotNullContext) {
            // Only auto-fix when `!!` is the final tail. With a trailing member/call
            // (`@? {...}!!.to_struct()`) dropping `!!` makes the `.member` re-parse as the at-expr's
            // WHAT projection, so such cases are reported without a (broken) fix.
            val fixData = if (seg.postOps.size == 1) NotNullToUnitFix(ctx, qToken, firstPost) else null
            report(AtCardinalityMisuseIssue(ctx, ruleId, NOT_NULL_MESSAGE, fixData))
        }
    }

    override fun visitBinaryExpr(ctx: RellParser.BinaryExprContext) {
        if (isDisabled(config.ruleAtCardinalityMisuse) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        // (d)/(e) `(entity @* {...}).size() <cmp> 0`.
        val cmp = ctx.asSimpleComparison() ?: return
        val card = when {
            cmp.right.asIntLiteral() == 0L -> parenListAtCall(cmp.left, "size")
            cmp.left.asIntLiteral() == 0L -> parenListAtCall(cmp.right, "size")
            else -> null
        } ?: return
        report(AtCardinalityMisuseIssue(ctx, ruleId, existenceMessage(card), null))
    }

    private class AtSegment(
        val cardSymbol: String,
        val postOps: List<ParseTree>,
        val qToken: TerminalNode?,
    )

    /** Analyzes the first at-expression operation within [ctx], or null if there is none. */
    private fun atSegment(ctx: RellParser.BaseExprContext): AtSegment? {
        val kids = ctx.children ?: return null
        val head = ctx.baseExprHead()
        if (head is RellParser.AtExprContext) {
            val at = head.atExprAt()
            val postOps = if (kids.size > 1) kids.subList(1, kids.size) else emptyList()
            return AtSegment(cardSymbol(at), postOps, qToken(at))
        }
        var i = 1
        while (i < kids.size) {
            val ch = kids[i]
            if (ch is RellParser.AtExprAtContext) {
                var j = i + 1
                if (j < kids.size && kids[j] is RellParser.AtExprWhereContext) j++
                if (j < kids.size && kids[j] is RellParser.AtExprWhatContext) j++
                if (j < kids.size && kids[j] is RellParser.AtExprModifiersContext) j++
                return AtSegment(cardSymbol(ch), kids.subList(j, kids.size), qToken(ch))
            }
            i++
        }
        return null
    }

    private fun isIndexedParenListAt(ctx: RellParser.BaseExprContext): Boolean {
        val head = ctx.baseExprHead() as? RellParser.TupleHeadContext ?: return false
        val exprs = head.expression()
        if (exprs.size != 1) return false
        val inner = exprs[0].binaryExpr()?.singleBaseExpr() ?: return false
        val innerSeg = atSegment(inner) ?: return false
        if (innerSeg.cardSymbol != "*" && innerSeg.cardSymbol != "+") return false
        val kids = ctx.children ?: return false
        val firstTail = kids.getOrNull(1) as? RellParser.BaseExprTailSubscriptContext ?: return false
        return firstTail.expression()?.text == "0"
    }

    /** Cardinality symbol if [expr] is `(<list at-expr>).method()`, else null. */
    private fun parenListAtCall(expr: RellParser.BaseExprContext, method: String): String? {
        val call = expr.asArgFreeMethodCall() ?: return null
        if (call.name != method) return null
        val head = expr.baseExprHead() as? RellParser.TupleHeadContext ?: return null
        val inner = head.expression().singleOrNull()?.binaryExpr()?.singleBaseExpr() ?: return null
        val seg = atSegment(inner) ?: return null
        if (seg.cardSymbol != "*" && seg.cardSymbol != "+") return null
        return seg.cardSymbol
    }

    private fun cardSymbol(at: RellParser.AtExprAtContext): String {
        val second = at.children?.getOrNull(1)
        return if (second is TerminalNode && second.text in CARD_SYMBOLS) second.text else ""
    }

    private fun qToken(at: RellParser.AtExprAtContext): TerminalNode? {
        val second = at.children?.getOrNull(1)
        return if (second is TerminalNode && second.text == "?") second else null
    }

    private fun existenceMessage(cardSymbol: String) =
        if (cardSymbol == "+") NON_EMPTY_MESSAGE else EXISTENCE_MESSAGE

    companion object {
        const val RULE_ID = "rule_at_cardinality_misuse"
        private val CARD_SYMBOLS = setOf("?", "*", "+")
        private const val NOT_NULL_MESSAGE = "Replace '@? {...}!!' with '@ {...}'"
        private const val INDEXED_MESSAGE = "Indexing a '@*'/'@+' result with [0]; use '@' or '@?'"
        private const val EXISTENCE_MESSAGE =
            "Existence check materializes a list; use '@? {...}' with a null-check or 'exists(...)'"
        private const val NON_EMPTY_MESSAGE = "'@+' always returns a non-empty list; the check is redundant"
    }
}
