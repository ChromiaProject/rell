package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.Range
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.parser.RellBaseVisitor
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_CommaSeparated_29Context
import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_IncrementStmtContext
import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_SimpleVarDeclaratorContext
import net.postchain.rell.toolbox.core.references.ReferenceIndexer
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

    override fun visitRuleX_VarStmt(ctx: RellParser.RuleX_VarStmtContext) {
        super.visitRuleX_VarStmt(ctx)
        if (isDisabled(config.ruleConstantDetection) || hasSemanticErrors()) {
            return
        }

        val varOrVal = ctx.ruleX_VarVal().text
        if (varOrVal == "val") {
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
            val isTupleDeclarator = declarator.parent?.parent is RuleX_CommaSeparated_29Context
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

    override fun visitRuleX_BaseExpr(ctx: RellParser.RuleX_BaseExprContext) {
        super.visitRuleX_BaseExpr(ctx)
        val isPostfix = ctx.ruleX_BaseExprTail(0)?.ruleX_BaseExprTailUnaryPostfixOp() != null
        val isPrefix = ctx.parent is RuleX_IncrementStmtContext
        if (isPostfix || isPrefix) {
            val ruleCtx = ctx.ruleX_BaseExprHead()?.ruleX_NameExpr()?.ruleX_Name()
            if (ruleCtx != null) {
                val range = Range(
                    Position(ruleCtx.start.line - 1, ruleCtx.stop.charPositionInLine),
                    Position(ruleCtx.stop.line - 1, ruleCtx.stop.charPositionInLine + ruleCtx.text.length)
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
        }
    }

    override fun visitRuleX_AssignStmt(ctx: RellParser.RuleX_AssignStmtContext) {
        super.visitRuleX_AssignStmt(ctx)
        val name = ctx.ruleX_BaseExpr()?.ruleX_BaseExprHead()?.ruleX_NameExpr()?.ruleX_Name()
        name?.let {
            val range = Range(
                Position(it.start.line - 1, it.stop.charPositionInLine),
                Position(it.stop.line - 1, it.stop.charPositionInLine + name.text.length)
            )
            val removed = varReferences.remove(range)
            if (removed != null) {
                val ruleCtx = removed.parserRuleContext
                val varRange = Range(
                    Position(ruleCtx.start.line - 1, ruleCtx.stop.charPositionInLine),
                    Position(ruleCtx.stop.line - 1, ruleCtx.stop.charPositionInLine + ruleCtx.text.length)
                )
                constants.remove(varRange)
            }
        }
    }

    override fun visitRuleX_RootParser(ctx: RellParser.RuleX_RootParserContext?) {
        super.visitRuleX_RootParser(ctx)
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
}

class ConstantDetectionRuleContext(
    val varName: String,
    val parserRuleContext: ParserRuleContext,
    val isTupleDeclarator: Boolean
)


class SimpleVarDeclaratorCollector : RellBaseVisitor<Unit>() {
    val declarations = mutableListOf<RuleX_SimpleVarDeclaratorContext>()
    override fun visitRuleX_SimpleVarDeclarator(ctx: RuleX_SimpleVarDeclaratorContext?) {
        if (ctx != null) {
            declarations.add(ctx)
        }
    }
}
