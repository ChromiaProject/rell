/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.OuterJoinCartesianProductIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Flags `@outer`-annotated entries in an at-expression's from-list that have no join condition,
 * which would produce a SQL cartesian product.
 *
 * The legacy grammar exposed each from-item via a dedicated `RuleX_AtExprFromItem` rule. The
 * canonical `Rell.g4` grammar parses the entire from-list flatly inside a single
 * `AtExprContext`, with annotations, optional names, expressions, and commas as direct children.
 * This rule walks those children, slices them into per-item ranges at every comma, and applies
 * the legacy diagnostic to each slice.
 */
class OuterJoinCartesianProductRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId = RULE_ID

    override fun visitAtExpr(ctx: RellParser.AtExprContext) {
        if (isDisabled(config.ruleOuterJoinCartesianProduct)) {
            return
        }
        for (item in splitFromItems(ctx)) {
            if (hasIgnoreCommentOnTop(item.firstToken)) {
                continue
            }
            if (item.hasOuterAnnotation && joinConditionMissing(item.expression)) {
                // Report at the from-item's first annotation so positions line up with what the
                // legacy `RuleX_AtExprFromItem`-based rule reported.
                val reportCtx = item.annotations.firstOrNull() ?: item.expression
                report(
                    OuterJoinCartesianProductIssue(
                        reportCtx,
                        ruleId,
                        "Missing 'outer join' condition. resulting in cartesian product"
                    )
                )
            }
        }
    }

    private fun joinConditionMissing(expr: RellParser.ExpressionContext): Boolean {
        // The from-item's expression is typically a single BaseExpr that may carry at-clauses.
        val baseExpr = findFirstBaseExpr(expr) ?: return true
        // Empty at-where (no expressions) or no at-clause at all: missing condition.
        val whereClauses = baseExpr.atExprWhere()
        if (whereClauses.isEmpty()) return true
        return whereClauses.all { it.expression().isEmpty() }
    }

    private fun findFirstBaseExpr(node: ParseTree): RellParser.BaseExprContext? {
        if (node is RellParser.BaseExprContext) return node
        if (node is TerminalNode) return null
        for (i in 0 until node.childCount) {
            val r = findFirstBaseExpr(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun splitFromItems(ctx: RellParser.AtExprContext): List<FromItem> {
        // Walk top-level children, accumulating into items and starting a new item after every
        // top-level comma. Skip the leading `(` and trailing `) @ ...` tail.
        val items = mutableListOf<FromItem>()
        var current = mutableListOf<ParseTree>()
        // The trailing tail elements (atExprAt, atExprWhere, atExprWhat, atExprModifiers) belong
        // to the outer at-expression, not to any from-item; stop slicing once we hit them.
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is RellParser.AtExprAtContext ||
                child is RellParser.AtExprWhereContext ||
                child is RellParser.AtExprWhatContext ||
                child is RellParser.AtExprModifiersContext
            ) {
                break
            }
            if (child is TerminalNode) {
                val text = child.text
                if (text == "(") continue
                if (text == ")") continue
                if (text == ",") {
                    items.addNotEmpty(current)
                    current = mutableListOf()
                    continue
                }
                if (text == ":") continue
                // RULE_ID before `:` is the from-item name; keep as part of the slice (we read it
                // for the `firstToken` only).
            }
            current.add(child)
        }
        items.addNotEmpty(current)
        return items
    }

    private fun MutableList<FromItem>.addNotEmpty(children: List<ParseTree>) {
        if (children.isEmpty()) return
        val annotations = children.filterIsInstance<RellParser.AnnotationContext>()
        val expression = children.filterIsInstance<RellParser.ExpressionContext>().firstOrNull() ?: return
        val firstChild = children.first()
        val firstToken = (firstChild as? ParserRuleContext)?.start
            ?: (firstChild as? TerminalNode)?.symbol
            ?: expression.start
        val hasOuter = annotations.any { it.text == "@outer" }
        add(FromItem(annotations, expression, hasOuter, firstToken))
    }

    private data class FromItem(
        val annotations: List<RellParser.AnnotationContext>,
        val expression: RellParser.ExpressionContext,
        val hasOuterAnnotation: Boolean,
        val firstToken: org.antlr.v4.runtime.Token
    )

    companion object {
        const val RULE_ID = "rule_outer_join_cartesian_product"
    }
}
