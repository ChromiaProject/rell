/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.NameNodesFinder
import net.postchain.rell.toolbox.linter.isUnderscore
import net.postchain.rell.toolbox.linter.issues.PreferValIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Flags a `var` local that is never reassigned and suggests `val`.
 */
class PreferValRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override fun visitVarStmtAlt(ctx: RellParser.VarStmtAltContext) {
        if (isDisabled(config.rulePreferVal) || hasIgnoreCommentOnTop(ctx.start) || hasSemanticErrors()) {
            return
        }
        if (ctx.start.text != "var") {
            return
        }
        // Tuple destructuring (`var (a, b) = ...`) is out of scope - each element needs its own analysis.
        if (ctx.varDeclarator() !is RellParser.SimpleVarDeclaratorContext) {
            return
        }
        val nameNode = NameNodesFinder().getFirstNodeUnder(ctx.varDeclarator()) ?: return
        if (nameNode.isUnderscore()) {
            return
        }
        val name = nameNode.text
        val scope = enclosingScope(ctx) ?: return
        if (isReassigned(scope, name)) {
            return
        }
        report(PreferValIssue(ctx, ruleId, "Variable '$name' is never reassigned; use 'val'"))
    }

    private fun enclosingScope(ctx: ParserRuleContext): ParserRuleContext? {
        var p: RuleContext? = ctx.parent
        var topBlock: ParserRuleContext? = null
        while (p != null) {
            when (p) {
                is RellParser.FunctionDefContext, is RellParser.OpDefContext, is RellParser.QueryDefContext ->
                    return p
                is RellParser.BlockStmtContext -> topBlock = p
            }
            p = p.parent
        }
        return topBlock
    }

    private fun isReassigned(scope: ParserRuleContext, name: String): Boolean = findWrite(scope, name)

    private fun findWrite(node: ParseTree, name: String): Boolean {
        when (node) {
            // `x = ...`, `x += ...`, etc.
            is RellParser.ExprStmtAltContext ->
                if (node.expression() != null && node.baseExpr()?.text == name) return true
            // `++x;` / `--x;`
            is RellParser.IncrementStmtAltContext -> if (node.baseExpr()?.text == name) return true
            // `x++` / `x--` in any position (statement or sub-expression, e.g. `val y = x++;`).
            is RellParser.BaseExprContext -> if (isPostfixIncDec(node, name)) return true
            // `++x` / `--x` in expression position (e.g. `val y = ++x;`, `f(--x)`).
            is RellParser.BinaryExprContext -> if (hasPrefixIncDec(node, name)) return true
        }
        for (i in 0 until node.childCount) {
            if (findWrite(node.getChild(i), name)) {
                return true
            }
        }
        return false
    }

    private fun isPostfixIncDec(expr: RellParser.BaseExprContext, name: String): Boolean {
        if (expr.childCount != 2 || expr.getChild(0).text != name) return false
        val tail = expr.getChild(1)
        return tail is RellParser.BaseExprTailUnaryPostfixOpContext && (tail.text == "++" || tail.text == "--")
    }

    private fun hasPrefixIncDec(bin: RellParser.BinaryExprContext, name: String): Boolean {
        val kids = bin.children ?: return false
        for (i in 0 until kids.size - 1) {
            val ch = kids[i]
            if (ch is TerminalNode && (ch.text == "++" || ch.text == "--") &&
                (kids[i + 1] as? RellParser.BaseExprContext)?.text == name
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        const val RULE_ID = "rule_prefer_val"
    }
}
