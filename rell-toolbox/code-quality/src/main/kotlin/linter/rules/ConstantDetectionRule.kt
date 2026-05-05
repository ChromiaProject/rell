/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellBaseVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.common.Position
import net.postchain.rell.toolbox.common.Range
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.references.ReferenceIndexer
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.NameNodesFinder
import net.postchain.rell.toolbox.linter.isUnderscore
import net.postchain.rell.toolbox.linter.issues.ConstantDetectionIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval

class ConstantDetectionRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    companion object {
        const val RULE_ID = "rule_constant_detection"
    }

    override val ruleId = RULE_ID

    private val referenceIndexer = ReferenceIndexer(resource.workspaceUri, mutableMapOf(resource.fileUri to resource))

    private val varReferences = mutableMapOf<Range, ConstantDetectionRuleContext>()
    private val constants = mutableMapOf<Range, ConstantDetectionRuleContext>()

    override fun visitVarStmtAlt(ctx: RellParser.VarStmtAltContext) {
        super.visitVarStmtAlt(ctx)
        if (isDisabled(config.ruleConstantDetection) || hasSemanticErrors()) {
            return
        }

        val keyword = ctx.start?.text
        if (keyword != "var") {
            return
        }
        val varDeclaratorCollector = SimpleVarDeclaratorCollector()
        ctx.accept(varDeclaratorCollector)
        val declarators = varDeclaratorCollector.declarations
        val nameNodesFinder = NameNodesFinder()
        for (declarator in declarators) {
            val name = nameNodesFinder.getFirstNodeUnder(declarator) ?: continue
            if (name.isUnderscore()) {
                continue
            }
            val symbolInterval = Interval.of(name.start.startIndex, name.stop.stopIndex)
            val symbolInfo = resource.locationInfo[symbolInterval]
            val references = referenceIndexer.findAllReferences(resource.fileUri, symbolInfo)
            val varName = name.text
            // A tuple declarator is one whose enclosing declarator chain reaches a TupleVarDeclarator.
            val isTupleDeclarator = isInsideTupleDeclarator(declarator)
            val constantContext = ConstantDetectionRuleContext(varName, name, isTupleDeclarator)
            val varRange = Range(
                Position(name.start.line - 1, name.stop.charPositionInLine),
                Position(name.stop.line - 1, name.stop.charPositionInLine + name.text.length)
            )
            constants[varRange] = constantContext
            references.forEach {
                varReferences[it.range] = constantContext
            }
        }
    }

    override fun visitBaseExpr(ctx: RellParser.BaseExprContext) {
        super.visitBaseExpr(ctx)
        // Detect: postfix increment (x++ / x-- as the only tail), prefix increment (parent is
        // IncrementStmtAlt), or assignment (parent is ExprStmtAlt with an `=` expression child).
        val isPostfix = ctx.baseExprTailNoCallNoAt().lastOrNull() is RellParser.BaseExprTailUnaryPostfixOpContext
        val parent = ctx.parent
        val isPrefix = parent is RellParser.IncrementStmtAltContext
        val isAssignmentLhs = parent is RellParser.ExprStmtAltContext &&
            parent.expression() != null &&
            parent.baseExpr() === ctx
        if (!(isPostfix || isPrefix || isAssignmentLhs)) {
            return
        }
        val nameExpr = ctx.baseExprHead() as? RellParser.NameExprContext ?: return
        val qualifiedName = nameExpr.qualifiedName() ?: return
        // Only treat single-segment names as variable references (qualified names like a.b are
        // member accesses, not variable rebinds).
        val ruleIds = qualifiedName.RULE_ID()
        if (ruleIds.size != 1) {
            return
        }
        val nameToken = ruleIds[0].symbol
        val range = Range(
            Position(nameToken.line - 1, nameToken.charPositionInLine + nameToken.text.length - nameToken.text.length),
            Position(nameToken.line - 1, nameToken.charPositionInLine + nameToken.text.length)
        )
        val removed = varReferences.remove(range)
        if (removed != null) {
            val removedRuleCtx = removed.parserRuleContext
            val varRange = Range(
                Position(removedRuleCtx.start.line - 1, removedRuleCtx.stop.charPositionInLine),
                Position(
                    removedRuleCtx.stop.line - 1,
                    removedRuleCtx.stop.charPositionInLine + removedRuleCtx.text.length
                )
            )
            constants.remove(varRange)
        }
    }

    override fun visitFile(ctx: RellParser.FileContext) {
        super.visitFile(ctx)
        constants.forEach {
            if (!hasIgnoreCommentOnTop(it.value.parserRuleContext.start)) {
                val varMessage = "Variable '${it.value.varName}' is never modified"
                val message = if (it.value.isTupleDeclarator) {
                    varMessage
                } else {
                    "$varMessage, so it can be declared using 'val'"
                }
                report(
                    ConstantDetectionIssue(
                        it.value.parserRuleContext,
                        ruleId,
                        message
                    )
                )
            }
        }
        varReferences.clear()
        constants.clear()
    }

    private fun isInsideTupleDeclarator(declarator: RellParser.SimpleVarDeclaratorContext): Boolean {
        var p: org.antlr.v4.runtime.RuleContext? = declarator.parent
        while (p != null) {
            if (p is RellParser.TupleVarDeclaratorContext) return true
            if (p is RellParser.VarStmtAltContext) return false
            p = p.parent
        }
        return false
    }
}

class ConstantDetectionRuleContext(
    val varName: String,
    val parserRuleContext: ParserRuleContext,
    val isTupleDeclarator: Boolean
)

class SimpleVarDeclaratorCollector : RellBaseVisitor<Unit>() {
    val declarations = mutableListOf<RellParser.SimpleVarDeclaratorContext>()
    override fun visitSimpleVarDeclarator(ctx: RellParser.SimpleVarDeclaratorContext?) {
        if (ctx != null) {
            declarations.add(ctx)
        }
    }
}
