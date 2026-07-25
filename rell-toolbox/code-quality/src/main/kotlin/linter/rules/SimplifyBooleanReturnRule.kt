/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.asBooleanLiteral
import net.postchain.rell.toolbox.linter.issues.SimplifyBooleanReturnIssue
import net.postchain.rell.toolbox.linter.singleBaseExpr
import org.antlr.v4.runtime.RuleContext

/**
 * Simplifies boolean-literal `if` chains that only pick between `true` and `false`:
 *   `if (c) return true; else return false;`  →  `return c;`
 *   `return if (c) true else false;`           →  `return c;`
 * The swapped forms produce `return not c;`.
 */
class SimplifyBooleanReturnRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.IfStmtAltContext::class.java,
        RellParser.ReturnStmtAltContext::class.java,
    )

    override fun visitIfStmtAlt(ctx: RellParser.IfStmtAltContext) {
        if (isDisabled(config.ruleSimplifyBooleanReturn) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        // An `if` that is the else-arm of an outer chain is covered by rule_replace_if_with_when's
        // whole-chain fix; reporting it here too would emit an overlapping (corrupting) fix.
        if (isElseBranch(ctx)) {
            return
        }
        val stmts = ctx.statement()
        if (stmts.size != 2) {
            return
        }
        val thenBool = returnBool(stmts[0]) ?: return
        val elseBool = returnBool(stmts[1]) ?: return
        if (thenBool == elseBool) {
            return
        }
        report(SimplifyBooleanReturnIssue(ctx, ruleId, MESSAGE, ctx.expression(), negate = !thenBool))
    }

    // Only the head of an if/else-if chain is considered; an inner else-arm if is left to the
    // if->when rule so the two rules never emit overlapping fixes.
    private fun isElseBranch(ctx: RellParser.IfStmtAltContext): Boolean {
        val parent = ctx.parent
        return parent is RellParser.IfStmtAltContext && parent.statement().size > 1 && parent.statement(1) === ctx
    }

    override fun visitReturnStmtAlt(ctx: RellParser.ReturnStmtAltContext) {
        if (isDisabled(config.ruleSimplifyBooleanReturn) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val bin = ctx.expression()?.binaryExpr() ?: return
        if (bin.childCount != 1) {
            return
        }
        val ifExpr = bin.getChild(0) as? RellParser.IfExprContext ?: return
        val arms = ifExpr.exprOrValueBlock()
        if (arms.size != 2) {
            return
        }
        val thenBool = arms[0].expression()?.boolLiteral() ?: return
        val elseBool = arms[1].expression()?.boolLiteral() ?: return
        if (thenBool == elseBool) {
            return
        }
        report(SimplifyBooleanReturnIssue(ctx, ruleId, MESSAGE, ifExpr.expression(), negate = !thenBool))
    }

    private fun returnBool(stmt: RellParser.StatementContext): Boolean? {
        val ret = when (stmt) {
            is RellParser.ReturnStmtAltContext -> stmt
            is RellParser.BlockStmtAltContext -> {
                val inner = stmt.blockStmt().statement()
                if (inner.size == 1) inner[0] as? RellParser.ReturnStmtAltContext else null
            }
            else -> null
        } ?: return null
        return ret.expression()?.boolLiteral()
    }

    private fun RellParser.ExpressionContext.boolLiteral(): Boolean? =
        binaryExpr()?.singleBaseExpr()?.asBooleanLiteral()

    companion object {
        const val RULE_ID = "rule_simplify_boolean_return"
        private const val MESSAGE = "Boolean return can be simplified to the condition"
    }
}
