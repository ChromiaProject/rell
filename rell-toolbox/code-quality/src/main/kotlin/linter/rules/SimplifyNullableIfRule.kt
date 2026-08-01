/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.NullGuardKind
import net.postchain.rell.toolbox.linter.asNullGuard
import net.postchain.rell.toolbox.linter.issues.SimplifyNullableIfIssue
import net.postchain.rell.toolbox.linter.nullGuardedName
import net.postchain.rell.toolbox.linter.singleBaseExpr
import net.postchain.rell.toolbox.linter.singleName
import net.postchain.rell.toolbox.linter.sourceText
import net.postchain.rell.toolbox.parser.RellCustomTokenChannels
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval

/**
 * Rewrites pure-expression null guards:
 *   `if (x != null) x else y`   → `x ?: y`
 *   `if (x != null) x.f else null` → `x?.f`
 * and the statement form, where the guard only jumps out of the enclosing function or loop:
 *   `val x = f(); if (x == null) return null;` → `val x = f() ?: return null;`
 * Either condition may equally be spelled with the null-check operator, `x??` and `not x??`.
 */
internal class SimplifyNullableIfRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.IfExprContext::class.java,
        RellParser.IfStmtAltContext::class.java,
    )

    override fun visitIfExpr(ctx: RellParser.IfExprContext) {
        if (isDisabled(config.ruleSimplifyNullableIf) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val guard = ctx.asNullGuard() ?: return
        val checkedText = guard.checkedText

        when (guard.kind) {
            NullGuardKind.ELVIS -> {
                // `?:` binds tighter than or/and/comparisons/in, so a compound (or lambda) else arm must
                // be parenthesized to keep its grouping: `f ?: a or b` would otherwise reparse as
                // `(f ?: a) or b`, and a bare lambda arm (`f ?: x -> x`) would not parse at all.
                val elseText = guard.elseExpr.sourceText()
                val wrappedElse = if (elseArmNeedsParens(guard.elseExpr)) "($elseText)" else elseText
                val replacement = "$checkedText ?: $wrappedElse"
                val newText = if (needsParens(ctx)) "($replacement)" else replacement
                report(SimplifyNullableIfIssue(ctx, ruleId, ELVIS_MESSAGE, ELVIS_TITLE, newText))
            }
            NullGuardKind.SAFE_ACCESS -> {
                val member = guard.thenExpr.sourceText().substring(checkedText.length)
                report(SimplifyNullableIfIssue(ctx, ruleId, SAFE_ACCESS_MESSAGE, SAFE_ACCESS_TITLE, "$checkedText?$member"))
            }
        }
    }

    override fun visitIfStmtAlt(ctx: RellParser.IfStmtAltContext) {
        if (isDisabled(config.ruleSimplifyNullableIf)) {
            return
        }
        val stmts = ctx.statement()
        // An else-arm means the guard picks between two paths, not "leave early or carry on".
        if (stmts.size != 1) {
            return
        }
        val jumpText = jumpText(stmts[0]) ?: return
        // The guard has to name the declaration above it, so only a plain variable is a candidate.
        val name = ctx.expression().nullGuardedName() ?: return
        val decl = guardedDeclaration(ctx, name) ?: return
        if (hasIgnoreCommentOnTop(ctx.start) || hasIgnoreCommentOnTop(decl.start)) {
            return
        }
        val initExpr = decl.expression()
        val declText = decl.start.inputStream.getText(Interval.of(decl.start.startIndex, initExpr.stop.stopIndex))
        // The fix replaces both statements, so a comment between them would be lost: report it, but
        // leave the rewrite to the reader.
        val newText = if (hasCommentsBetween(decl.stop, ctx.stop)) null else "$declText ?: $jumpText;"
        report(SimplifyNullableIfIssue(ctx, ruleId, GUARD_MESSAGE, GUARD_TITLE, newText, decl.start, ctx.stop))
    }

    /**
     * The `val` declaration of [name] directly above the guard, when merging the two is safe.
     * Requires the initialiser to be a single operand: `?:` binds tighter than `or`, `and`, `in` and
     * the comparisons, so appending it to a compound initialiser would regroup the expression.
     */
    private fun guardedDeclaration(ctx: RellParser.IfStmtAltContext, name: String): RellParser.VarStmtAltContext? {
        val siblings = when (val parent = ctx.parent) {
            is RellParser.BlockStmtContext -> parent.statement()
            is RellParser.ValueBlockContext -> parent.statement()
            else -> return null
        }
        val index = siblings.indexOfFirst { it === ctx }
        // `var` is left alone: the rewrite drops the null from the inferred type, which a later
        // assignment of null would no longer accept.
        val decl = siblings.getOrNull(index - 1) as? RellParser.VarStmtAltContext ?: return null
        if (decl.start.text != "val") {
            return null
        }
        // A declared type is kept verbatim by the rewrite, so `val x: T? = f() ?: return` would leave
        // x nullable and break the code below the guard, which counts on it being non-null.
        val header = (decl.varDeclarator() as? RellParser.SimpleVarDeclaratorContext)
            ?.attrHeader() as? RellParser.AnonAttrHeaderContext ?: return null
        // `childCount == 1` excludes `val x?`, whose declared type is nullable as well.
        if (header.childCount != 1 || header.qualifiedName().singleName() != name) {
            return null
        }
        if (decl.expression()?.binaryExpr()?.singleBaseExpr() == null) {
            return null
        }
        return decl
    }

    /** The guard body as a jump expression, when it is nothing but a jump. */
    private fun jumpText(stmt: RellParser.StatementContext): String? {
        val jump = if (stmt is RellParser.BlockStmtAltContext) {
            stmt.blockStmt().statement().singleOrNull()
        } else {
            stmt
        }
        return when (jump) {
            // A `return` value needs no parentheses: as the right operand of `?:` it extends to the
            // end of the expression anyway.
            is RellParser.ReturnStmtAltContext -> jump.expression()?.let { "return ${it.sourceText()}" } ?: "return"
            is RellParser.BreakStmtAltContext -> "break"
            is RellParser.ContinueStmtAltContext -> "continue"
            else -> null
        }
    }

    private fun hasCommentsBetween(from: Token, to: Token): Boolean {
        val tokens = resource.tokenStream
        return (from.tokenIndex + 1..to.tokenIndex).any {
            tokens.get(it).channel == RellCustomTokenChannels.COMMENTS.channel
        }
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
        private const val ELVIS_TITLE = "Replace 'if' with '?:'"
        private const val SAFE_ACCESS_MESSAGE = "Replace 'if' with safe-access '?.'"
        private const val SAFE_ACCESS_TITLE = "Replace 'if' with '?.'"
        private const val GUARD_MESSAGE = "Null guard can be merged into the declaration with '?:'"
        private const val GUARD_TITLE = "Merge the null guard into the declaration with '?:'"
    }
}
