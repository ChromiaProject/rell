/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode

internal fun ParserRuleContext.isUnderscore(): Boolean = this.text == "_"

/** Verbatim source text spanned by this context, taken from the original input stream. */
internal fun ParserRuleContext.sourceText(): String =
    start.inputStream.getText(Interval.of(start.startIndex, stop.stopIndex))

private val COMPARISON_OPS = setOf("==", "!=", "<", ">", "<=", ">=")

/** A `binaryExpr` shaped as exactly `left OP right` with a single comparison operator. */
internal class SimpleComparison(
    val left: RellParser.BaseExprContext,
    val op: TerminalNode,
    val right: RellParser.BaseExprContext,
)

/**
 * Rell keeps `binaryExpr` flat and resolves precedence downstream, so only the plain
 * two-operand shape (`left OP right`, no prefix operators, no operator chaining) can be
 * rewritten safely without precedence analysis. Returns that triple for a comparison
 * operator, or null otherwise.
 */
internal fun RellParser.BinaryExprContext.asSimpleComparison(): SimpleComparison? {
    val kids = children ?: return null
    if (kids.size != 3) return null
    val left = kids[0] as? RellParser.BaseExprContext ?: return null
    val op = kids[1] as? TerminalNode ?: return null
    val right = kids[2] as? RellParser.BaseExprContext ?: return null
    if (op.text !in COMPARISON_OPS) return null
    return SimpleComparison(left, op, right)
}

/** `true`/`false` as a bare literal operand (no member access or other tails), else null. */
internal fun RellParser.BaseExprContext.asBooleanLiteral(): Boolean? {
    if (childCount != 1) return null
    return when (baseExprHead()) {
        is RellParser.TrueExprContext -> true
        is RellParser.FalseExprContext -> false
        else -> null
    }
}

/** True if this operand is a bare `null` literal. */
internal fun RellParser.BaseExprContext.isNullLiteral(): Boolean =
    childCount == 1 && baseExprHead() is RellParser.NullExprContext

/** Bare integer literal operand as a Long, else null. */
internal fun RellParser.BaseExprContext.asIntLiteral(): Long? {
    if (childCount != 1) return null
    val head = baseExprHead() as? RellParser.IntExprContext ?: return null
    return head.text.toLongOrNull()
}

/** True if this expression's head or postfix chain includes an at-expression (`@`, `@?`, `@*`, `@+`). */
internal fun RellParser.BaseExprContext.containsAtOperation(): Boolean =
    baseExprHead() is RellParser.AtExprContext || (children?.any { it is RellParser.AtExprAtContext } ?: false)

/** The sole `baseExpr` of a single-operand binary expression (no operators, no prefixes), else null. */
internal fun RellParser.BinaryExprContext.singleBaseExpr(): RellParser.BaseExprContext? {
    if (childCount != 1) return null
    return getChild(0) as? RellParser.BaseExprContext
}

/** An argument-free method call `<receiver>.name()`. */
internal class ArgFreeMethodCall(val name: String, val receiverText: String)

/**
 * Detects `<receiver>.name()` with an empty argument list. Two parse shapes occur:
 *  - head-qualified (`xs.size()`, `a.b.items.size()`): `.size` is the last component of the head
 *    name expression's qualified name;
 *  - tail-member (`(...).size()`, `foo().size()`): `.size` is a member tail after a call/paren/etc.
 * Returns null when this is not an argument-free method call (e.g. a bare `foo()` function call).
 */
internal fun RellParser.BaseExprContext.asArgFreeMethodCall(): ArgFreeMethodCall? {
    val kids = children ?: return null
    if (kids.size < 2) return null
    val last = kids[kids.size - 1]
    if (last !is RellParser.CallArgsContext || last.text != "()") return null
    val input = start.inputStream
    return when (val prev = kids[kids.size - 2]) {
        is RellParser.BaseExprTailMemberContext -> {
            val name = prev.RULE_ID()?.text ?: return null
            val receiver = input.getText(Interval.of(start.startIndex, prev.start.startIndex - 1))
            ArgFreeMethodCall(name, receiver)
        }
        is RellParser.NameExprContext -> {
            val qName = prev.qualifiedName()
            val ids = qName?.RULE_ID() ?: return null
            if (ids.size < 2) return null
            val lastDot = qName.getChild(qName.childCount - 2) as? TerminalNode ?: return null
            val receiver = input.getText(Interval.of(start.startIndex, lastDot.symbol.startIndex - 1))
            ArgFreeMethodCall(ids.last().text, receiver)
        }
        else -> null
    }
}

/** True if this context sits (transitively) inside an at-expression where-clause. */
internal fun ParserRuleContext.isInsideAtWhere(): Boolean {
    var p = parent
    while (p != null) {
        if (p is RellParser.AtExprWhereContext) return true
        p = p.parent
    }
    return false
}
