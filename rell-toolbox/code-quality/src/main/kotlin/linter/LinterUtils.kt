/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
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

/** The non-null operand of a `<expr> OP null` / `null OP <expr>` condition, else null. */
internal fun RellParser.ExpressionContext.nullComparisonOperand(op: String): RellParser.BaseExprContext? {
    val cmp = binaryExpr()?.asSimpleComparison() ?: return null
    if (cmp.op.text != op) return null
    val leftNull = cmp.left.isNullLiteral()
    val rightNull = cmp.right.isNullLiteral()
    return when {
        rightNull && !leftNull -> cmp.left
        leftNull && !rightNull -> cmp.right
        else -> null
    }
}

/** True if a call appears anywhere in this subtree. */
internal fun ParseTree.containsCall(): Boolean {
    if (this is RellParser.CallArgsContext) return true
    for (i in 0 until childCount) {
        if (getChild(i).containsCall()) return true
    }
    return false
}

/** The trailing null-check operator of an operand written as `x??`, else null. */
private fun RellParser.BaseExprContext.nullCheckTail(): RellParser.BaseExprTailUnaryPostfixOpContext? =
    (getChild(childCount - 1) as? RellParser.BaseExprTailUnaryPostfixOpContext)?.takeIf { it.text == "??" }

/** A "value is not null" test, however it is spelled. */
internal class NullCheck(
    /** Source text of the tested expression, without the test itself. */
    val text: String,
    /** True if the tested expression contains a call, which a rewrite must not duplicate. */
    val hasCall: Boolean,
)

/** This expression as a not-null test - `x != null`, `null != x` or `x??` - else null. */
internal fun RellParser.ExpressionContext.asNullCheck(): NullCheck? {
    nullComparisonOperand("!=")?.let { return NullCheck(it.sourceText(), it.containsCall()) }
    val base = binaryExpr()?.singleBaseExpr() ?: return null
    val tail = base.nullCheckTail() ?: return null
    val text = base.start.inputStream.getText(Interval.of(base.start.startIndex, tail.start.startIndex - 1))
    return NullCheck(text, base.containsCall())
}

/**
 * The name tested by a "value is null" guard - `x == null` or `not x??` - when the test is a plain
 * variable. A member access (`a.b == null`) does not name a declaration, so it is not one.
 */
internal fun RellParser.ExpressionContext.nullGuardedName(): String? {
    nullComparisonOperand("==")?.let { operand ->
        if (operand.childCount != 1) return null
        return (operand.baseExprHead() as? RellParser.NameExprContext)?.qualifiedName().singleName()
    }
    val bin = binaryExpr() ?: return null
    // `not x??`: the `not` prefix and the operand are the whole expression.
    if (bin.childCount != 2 || (bin.getChild(0) as? TerminalNode)?.text != "not") return null
    val base = bin.getChild(1) as? RellParser.BaseExprContext ?: return null
    if (base.childCount != 2 || base.nullCheckTail() == null) return null
    return (base.baseExprHead() as? RellParser.NameExprContext)?.qualifiedName().singleName()
}

/** The name behind a one-segment qualified name (`x`), else null (`a.b`, an at-alias, ...). */
internal fun RellParser.QualifiedNameContext?.singleName(): String? {
    val ids = this?.RULE_ID() ?: return null
    return if (ids.size == 1) ids[0].text else null
}

internal enum class NullGuardKind { ELVIS, SAFE_ACCESS }

/** An `if`-expression that is nothing but a null guard around one value. */
internal class NullGuardExpr(
    val checkedText: String,
    val thenExpr: RellParser.ExpressionContext,
    val elseExpr: RellParser.ExpressionContext,
    val kind: NullGuardKind,
)

internal val SINGLE_MEMBER = Regex("^\\.[A-Za-z_][A-Za-z0-9_]*$")

/**
 * Recognises the pure-expression null guards that collapse to a null-aware operator:
 *   `if (x != null) x else y`      → `x ?: y`     ([NullGuardKind.ELVIS])
 *   `if (x != null) x.f else null` → `x?.f`       ([NullGuardKind.SAFE_ACCESS])
 * The condition may equally be spelled `x??`. Calls in the checked expression disqualify it:
 * repeating the call in the rewrite would run its side effects twice.
 */
internal fun RellParser.IfExprContext.asNullGuard(): NullGuardExpr? {
    val check = expression().asNullCheck() ?: return null
    if (check.hasCall) return null
    val arms = exprOrValueBlock()
    if (arms.size != 2) return null
    val thenExpr = arms[0].expression() ?: return null
    val elseExpr = arms[1].expression() ?: return null
    val checkedText = check.text
    val thenText = thenExpr.sourceText()
    if (thenText == checkedText) {
        return NullGuardExpr(checkedText, thenExpr, elseExpr, NullGuardKind.ELVIS)
    }
    if (elseExpr.text == "null" && thenText.startsWith(checkedText) &&
        SINGLE_MEMBER.matches(thenText.substring(checkedText.length))
    ) {
        return NullGuardExpr(checkedText, thenExpr, elseExpr, NullGuardKind.SAFE_ACCESS)
    }
    return null
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
