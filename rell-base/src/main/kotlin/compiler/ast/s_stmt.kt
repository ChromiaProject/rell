/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_LocalAttrHeaderIdeData
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.R_RellErrorType
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.stmt.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolKind

abstract class S_Statement(val startPos: S_Pos, val endPos: S_Pos) {
    private val modifiedVars = TypedKey<Set<R_Name>>()

    internal abstract fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement

    internal fun compileSafe(ctx: C_StmtContext, repl: Boolean = false): C_Statement {
        val cStmt = ctx.msgCtx.consumeError { compile(ctx, repl) }
        return cStmt ?: C_Statement.ERROR
    }

    internal fun compileWithVarStates(ctx: C_StmtContext, delta: C_VarStatesDelta): C_Statement {
        val subCtx = ctx.updateVarStates(delta)
        return compileSafe(subCtx)
    }

    internal fun discoverVars(map: MutableTypedKeyMap): C_StatementVars {
        val vars = discoverVars0(map)
        map.put(modifiedVars, vars.modified)
        return vars
    }

    protected open fun discoverVars0(map: MutableTypedKeyMap) = C_StatementVars.EMPTY

    internal fun getModifiedVars(ctx: C_FunctionContext): Set<R_Name> {
        val res = ctx.statementVars.get(modifiedVars)
        return res
    }

    internal open fun returnsValue(): Boolean? = null
}

internal class S_EmptyStatement(pos: S_Pos): S_Statement(pos, pos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean) = C_Statement.EMPTY
}

sealed class S_VarDeclarator {
    internal abstract fun discoverVars(vars: MutableSet<R_Name>)

    internal abstract fun compile(
        ctx: C_StmtContext,
        mutable: Boolean,
        hasExpr: Boolean,
        comment: S_Comment?,
    ): C_VarDeclarator
}

class S_SimpleVarDeclarator(
    private val attrHeader: S_AttrHeader,
): S_VarDeclarator() {
    override fun discoverVars(vars: MutableSet<R_Name>) {
        vars.add(attrHeader.discoverVar())
    }

    override fun compile(ctx: C_StmtContext, mutable: Boolean, hasExpr: Boolean, comment: S_Comment?): C_VarDeclarator {
        val ideKind = if (mutable) IdeSymbolKind.LOC_VAR else IdeSymbolKind.LOC_VAL
        val docLateInit = ctx.lateInit<DocSymbol?>(C_CompilerPass.DOCS, null)
        val ideData = C_LocalAttrHeaderIdeData(ideKind, docLateInit.getter)

        val cAttrHeader = attrHeader.compile(ctx.defCtx, hasExpr, ideData)
        val cName = cAttrHeader.name
        val explicitType = if (cAttrHeader.isExplicitType) cAttrHeader.type else null

        return if (cName.str == "_") {
            if (explicitType != null) {
                ctx.msgCtx.error(cName.pos, "var_wildcard_type", "Name '$cName' is a wildcard, it cannot have a type")
            }
            C_WildcardVarDeclarator(ctx, mutable)
        } else {
            C_SimpleVarDeclarator(ctx, mutable, cAttrHeader, cName, explicitType, comment, cAttrHeader.ideInfo, docLateInit)
        }
    }
}

class S_TupleVarDeclarator(
    val pos: S_Pos,
    private val subDeclarators: ImmList<S_VarDeclarator>,
): S_VarDeclarator() {
    override fun compile(ctx: C_StmtContext, mutable: Boolean, hasExpr: Boolean, comment: S_Comment?): C_VarDeclarator {
        val cSubDeclarators = subDeclarators.mapToImmList {
            it.compile(ctx, mutable, hasExpr, comment)
        }
        return C_TupleVarDeclarator(ctx, mutable, pos, cSubDeclarators)
    }

    override fun discoverVars(vars: MutableSet<R_Name>) {
        for (subDeclarator in subDeclarators) {
            subDeclarator.discoverVars(vars)
        }
    }
}

class S_VarStatement internal constructor(
    startPos: S_Pos,
    endPos: S_Pos,
    val declarator: S_VarDeclarator,
    val expr: S_Expr?,
    val mutable: Boolean,
    private val comment: S_Comment?,
): S_Statement(startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val cDeclarator = declarator.compile(ctx, mutable, hasExpr = expr != null, comment = comment)

        val typeHint = C_TypeHint.ofType(cDeclarator.getHintType())
        val exprHint = C_ExprHint(typeHint)

        val vExpr = expr?.compileSafe(ctx.exprCtx, exprHint)?.vExpr()
        val rExpr = vExpr?.toRExpr()

        val declaratorRes = cDeclarator.compile(rExpr?.type)
        val rStmt = R_VarStatement(declaratorRes.rDeclarator, rExpr)

        val valueVarStates = vExpr?.varStatesDelta?.always ?: C_VarStatesDelta.EMPTY
        val resVarStates = valueVarStates.and(declaratorRes.varStatesDelta)

        return C_Statement(rStmt, false, resVarStates)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val declaredVars = mutableSetOf<R_Name>()
        declarator.discoverVars(declaredVars)
        return C_StatementVars(declaredVars.toImmSet(), immSetOf())
    }
}

internal class S_ReturnStatement(
    startPos: S_Pos,
    endPos: S_Pos,
    private val expr: S_Expr?,
): S_Statement(startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val rStmt = compileInternal(ctx)
        return C_Statement(rStmt, true)
    }

    private fun compileInternal(ctx: C_StmtContext): R_Statement {
        var vExpr: V_Expr? = null

        if (expr != null) {
            val cExpr = expr.compileOpt(ctx, C_ExprHint.ofType(ctx.fnCtx.explicitReturnType))
            vExpr = cExpr?.vExprOrNull(ctx.msgCtx)
            vExpr ?: return C_ExprUtils.ERROR_STATEMENT

            if (!C_Utils.checkUnitType(ctx.msgCtx, startPos, vExpr.type, "stmt_return_unit", "Expression returns nothing")) {
                return C_ExprUtils.ERROR_STATEMENT
            }
        }

        vExpr = processExpr(ctx, vExpr)
        val rExpr = vExpr?.toRExpr()
        return R_ReturnStatement(rExpr)
    }

    private fun processExpr(ctx: C_StmtContext, vExpr: V_Expr?): V_Expr? {
        var vResExpr = vExpr
        when (val defType = ctx.defCtx.definitionType) {
            C_DefinitionType.OPERATION -> {
                if (vExpr != null) {
                    ctx.msgCtx.error(startPos, "stmt_return_op_value", "Operation must return nothing")
                }
            }
            C_DefinitionType.FUNCTION, C_DefinitionType.QUERY -> {
                if (defType == C_DefinitionType.QUERY && vExpr == null) {
                    ctx.msgCtx.error(startPos, "stmt_return_query_novalue", "Query must return a value")
                }

                val rRetType = vExpr?.type ?: R_UnitType
                val adapter = ctx.fnCtx.matchReturnType(startPos, rRetType)

                if (vExpr != null) {
                    vResExpr = adapter.adaptExpr(ctx.exprCtx, vExpr)
                }
            }
            else -> {
                ctx.msgCtx.error(startPos, "stmt_return_disallowed:$defType", "Return is not allowed here")
            }
        }

        return vResExpr
    }

    override fun returnsValue() = expr != null
}

internal class S_BlockStatement(
    private val posRange: S_PosRange,
    private val stmts: ImmList<S_Statement>,
): S_Statement(posRange.start, posRange.end) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val (subCtx, subBlkCtx) = ctx.subBlock(ctx.loop)

        val hasGuardBlock = stmts.any { it is S_GuardStatement }

        val builder = C_BlockCodeBuilder(
            subCtx,
            repl = false,
            hasGuardBlock = hasGuardBlock,
            posRange = posRange,
            proto = C_BlockCodeProto.EMPTY,
        )

        for (stmt in stmts) {
            builder.add(stmt)
        }

        val blockCode = builder.build()

        val frameBlock = subBlkCtx.buildBlock()
        val rStmt = R_BlockStatement(blockCode.rStmts, frameBlock.rBlock)
        return C_Statement(rStmt, blockCode.alwaysReturns, blockCode.varStatesDelta, blockCode.guardBlock)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val block = C_StatementVarsBlock()

        for (stmt in stmts) {
            val vars = stmt.discoverVars(map)
            block.declared(vars.declared)
            block.modified(vars.modified)
        }

        val modified = block.modified()
        return C_StatementVars(immSetOf(), modified)
    }

    override fun returnsValue(): Boolean? {
        for (s in stmts) {
            val rv = s.returnsValue()
            if (rv != null) return rv
        }
        return null
    }
}

internal class S_ExprStatement(
    private val expr: S_Expr,
    endPos: S_Pos,
): S_Statement(expr.startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val vExpr = expr.compile(ctx).vExpr()
        val rExpr = vExpr.toRExpr()
        val rStmt = if (repl) R_ReplExprStatement(rExpr) else R_ExprStatement(rExpr)
        return C_Statement(rStmt, rExpr.type == R_RellErrorType, vExpr.varStatesDelta.always)
    }
}

internal class S_AssignStatement internal constructor(
    private val dstExpr: S_Expr,
    private val op: S_PosValue<S_AssignOpCode>,
    private val srcExpr: S_Expr,
    endPos: S_Pos,
): S_Statement(dstExpr.startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val cDstExpr = dstExpr.compileOpt(ctx)
        val vDstExpr = cDstExpr?.vExpr()

        val srcCtx = ctx.updateVarStates(vDstExpr?.varStatesDelta?.always ?: C_VarStatesDelta.EMPTY)
        val exprHint = C_ExprHint.ofType(vDstExpr?.type)
        val cSrcExpr = srcExpr.compileOpt(srcCtx, exprHint)
        val vSrcExpr = cSrcExpr?.vExpr()

        if (vDstExpr == null || vSrcExpr == null) {
            return C_Statement.EMPTY
        }

        val opCtx = C_BinOpContext(ctx.exprCtx, op.pos)
        return op.value.op.compile(opCtx, vDstExpr, vSrcExpr)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val qName = dstExpr.asName()
        return if (qName == null) C_StatementVars.EMPTY else {
            val rName = qName.parts.first().getRNameSpecial()
            C_StatementVars(immSetOf(), immSetOf(rName))
        }
    }
}

internal class S_IfStatement(
    startPos: S_Pos,
    private val expr: S_Expr,
    private val trueStmt: S_Statement,
    private val falseStmt: S_Statement?,
): S_Statement(startPos, falseStmt?.endPos ?: trueStmt.endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val rExpr: R_Expr
        val exprVarStates: C_ExprVarStatesDelta

        val cExpr = expr.compileOpt(ctx)
        if (cExpr != null) {
            val vExpr = cExpr.vExpr()
            rExpr = vExpr.toRExpr()
            C_Types.matchOpt(ctx.msgCtx, R_BooleanType, rExpr.type, expr.startPos) {
                "stmt_if_expr_type" toCodeMsg "Wrong type of if-expression"
            }
            exprVarStates = vExpr.varStatesDelta
        } else {
            rExpr = C_ExprUtils.errorRExpr(R_BooleanType)
            exprVarStates = C_ExprVarStatesDelta.EMPTY
        }

        val subCtx = ctx.copy(topLevel = false)

        val cTrueStmt = compileBranchStmt(subCtx, trueStmt, exprVarStates.always, exprVarStates.whenTrue)
        val cFalseStmt = compileBranchStmt(subCtx, falseStmt, exprVarStates.always, exprVarStates.whenFalse)
        val rStmt = R_IfStatement(rExpr, cTrueStmt.rStmt, cFalseStmt.rStmt)

        val returns = cTrueStmt.alwaysReturns && cFalseStmt.alwaysReturns

        val branchesVarStates = C_Statement.varStatesDeltaForBranches(listOf(cTrueStmt, cFalseStmt))
        val resVarStates = exprVarStates.always.and(branchesVarStates)

        return C_Statement(rStmt, returns, resVarStates)
    }

    private fun compileBranchStmt(
        ctx: C_StmtContext,
        stmt: S_Statement?,
        always: C_VarStatesDelta,
        whenMatch: C_VarStatesDelta,
    ): C_Statement {
        val cStmt = if (stmt == null) C_Statement.EMPTY else {
            val varStates = always.and(whenMatch)
            stmt.compileWithVarStates(ctx, varStates)
        }
        return cStmt.copy(varStatesDelta = whenMatch.and(cStmt.varStatesDelta))
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val trueVars = trueStmt.discoverVars(map)
        val falseVars = falseStmt?.discoverVars(map) ?: C_StatementVars.EMPTY
        return C_StatementVars(immSetOf(), trueVars.modified + falseVars.modified)
    }

    override fun returnsValue(): Boolean? {
        val t = trueStmt.returnsValue()
        return t ?: falseStmt?.returnsValue()
    }
}

internal class S_WhenStatementCase(val cond: S_WhenCondition, val stmt: S_Statement)

internal class S_WhenStatement(
    startPos: S_Pos,
    endPos: S_Pos,
    private val expr: S_Expr?,
    private val cases: ImmList<S_WhenStatementCase>,
): S_Statement(startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val conds = cases.map { it.cond }

        val chooser = S_WhenExpr.compileChooser(ctx.exprCtx, expr, conds)
        if (chooser == null) {
            cases.forEach { it.stmt.compileSafe(ctx) }
            return C_Statement.ERROR
        }

        val bodyCtx = ctx.copy(exprCtx = chooser.bodyExprCtx)

        val cStmts = cases.mapIndexed { i, case ->
            val delta = chooser.caseVarStatesDeltas[i]
            val cStmt = case.stmt.compileWithVarStates(bodyCtx, delta)
            cStmt.copy(varStatesDelta = delta.and(cStmt.varStatesDelta))
        }
        val returns = chooser.full && cStmts.all { it.alwaysReturns }

        val rStmts = cStmts.mapToImmList { it.rStmt }
        val rStmt = R_WhenStatement(chooser.rChooser, rStmts)

        val fullStmts = if (chooser.full) cStmts else cStmts + C_Statement.empty(chooser.elseVarStatesDelta)
        val stmtState = C_Statement.varStatesDeltaForBranches(fullStmts)
        val resState = chooser.keyVarStatesDelta.and(stmtState)

        return C_Statement(rStmt, returns, resState)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val modified = mutableSetOf<R_Name>()
        for (case in cases) {
            val caseVars = case.stmt.discoverVars(map)
            modified.addAll(caseVars.modified)
        }
        return C_StatementVars(immSetOf(), modified.toImmSet())
    }

    override fun returnsValue(): Boolean? {
        for (case in cases) {
            val rv = case.stmt.returnsValue()
            if (rv != null) return rv
        }
        return null
    }
}

internal class C_LoopStatement(
    val condCtx: C_StmtContext,
    val condExpr: R_Expr,
    val condVarStatesDelta: C_ExprVarStatesDelta,
    val modifiedVars: ImmList<C_LocalVar>,
)

internal class S_WhileStatement(
    startPos: S_Pos,
    private val expr: S_Expr,
    private val stmt: S_Statement,
): S_Statement(startPos, stmt.endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val loop = compileLoop(ctx, this, expr)
        if (loop == null) {
            stmt.compileSafe(ctx)
            return C_Statement.ERROR
        }

        val rExpr = loop.condExpr

        C_Types.matchOpt(ctx.msgCtx, R_BooleanType, rExpr.type, expr.startPos) {
            "stmt_while_expr_type" toCodeMsg "Wrong type of while-expression"
        }

        val loopUid = ctx.fnCtx.nextLoopUid()
        val (loopCtx, loopBlkCtx) = loop.condCtx.subBlock(loopUid)

        val condState = loop.condVarStatesDelta
        val bodyState = condState.always.and(condState.whenTrue)
        val cBodyStmt = stmt.compileWithVarStates(loopCtx, bodyState)
        val rBodyStmt = cBodyStmt.rStmt

        val cBlock = loopBlkCtx.buildBlock()
        val rStmt = R_WhileStatement(rExpr, rBodyStmt, cBlock.rBlock)

        var varStates = calcResVarStatesDelta(condState, cBodyStmt)
        if (!MODIFIED_VAR_COMPATIBILITY_SWITCH.isActive(ctx.globalCtx)) {
            for (modVar in loop.modifiedVars) {
                varStates = varStates.changed(modVar.varKey, C_VarChanged.MAYBE, C_VarNulled.YES)
            }
        }

        return C_Statement(rStmt, false, varStates)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val bodyVars = stmt.discoverVars(map)
        return C_StatementVars(immSetOf(), bodyVars.modified)
    }

    override fun returnsValue() = stmt.returnsValue()

    internal companion object {
        private val MODIFIED_VAR_COMPATIBILITY_SWITCH = C_FeatureSwitch("0.14.0")

        internal fun compileLoop(ctx: C_StmtContext, stmt: S_Statement, expr: S_Expr): C_LoopStatement? {
            val modifiedVars = getModifiedVars(stmt, ctx)
            val condCtx = ctx.updateVarStates(calcUpdatedVarStatesDelta(modifiedVars))

            val condExpr = expr.compileOpt(condCtx) ?: return null

            val vCondExpr = condExpr.vExpr()
            val rExpr = vCondExpr.toRExpr()
            return C_LoopStatement(condCtx, rExpr, vCondExpr.varStatesDelta, modifiedVars)
        }

        private fun getModifiedVars(stmt: S_Statement, ctx: C_StmtContext): ImmList<C_LocalVar> {
            val modVars = stmt.getModifiedVars(ctx.fnCtx)
            val res = ArrayList<C_LocalVar>(modVars.size)

            for (name in modVars) {
                val localVar = ctx.blkCtx.lookupLocalVar(name)
                if (localVar != null) {
                    res.add(localVar.target)
                }
            }

            return res.toImmList()
        }

        private fun calcUpdatedVarStatesDelta(modifiedVars: List<C_LocalVar>): C_VarStatesDelta {
            var res = C_VarStatesDelta.EMPTY
            for (modVar in modifiedVars) {
                res = res.changed(modVar.varKey, C_VarChanged.MAYBE)
            }
            return res
        }

        fun calcResVarStatesDelta(condVarStates: C_ExprVarStatesDelta, cBodyStmt: C_Statement): C_VarStatesDelta {
            val stmts = listOf(cBodyStmt, C_Statement.EMPTY)
            val bodyStates = C_Statement.varStatesDeltaForBranches(stmts)
            return condVarStates.always.and(bodyStates)
        }
    }
}

internal class S_ForStatement(
    startPos: S_Pos,
    private val declarator: S_VarDeclarator,
    private val expr: S_Expr,
    private val stmt: S_Statement,
    private val headerEndPos: S_Pos,
): S_Statement(startPos, stmt.endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val loop = S_WhileStatement.compileLoop(ctx, this, expr)
        if (loop == null) {
            compileBody(ctx)
            return C_Statement.ERROR
        }

        val rExpr = loop.condExpr
        val exprType = rExpr.type

        val cIterableAdapter = C_IterableAdapter.compile(ctx.exprCtx, exprType, false)
        if (cIterableAdapter == null) {
            ctx.msgCtx.error(expr.startPos, "stmt_for_expr_type:[${exprType.strCode()}]",
                    "Wrong type of for-expression: ${exprType.strCode()}")
            compileBody(ctx)
            return C_Statement.ERROR
        }

        val loopUid = ctx.fnCtx.nextLoopUid()
        val (loopCtx, loopBlkCtx) = loop.condCtx.subBlock(loopUid)

        val cDeclarator = declarator.compile(loopCtx, mutable = false, hasExpr = true, comment = null)
        val cDeclaratorRes = cDeclarator.compile(cIterableAdapter.itemType)
        val rDeclarator = cDeclaratorRes.rDeclarator
        val iterFactsCtx = loopCtx.updateVarStates(cDeclaratorRes.varStatesDelta)

        val bodyCtx = iterFactsCtx.updateVarStates(loop.condVarStatesDelta.always)
        val cBodyStmt = compileBody(bodyCtx)
        val rBodyStmt = cBodyStmt.rStmt

        val cBlock = loopBlkCtx.buildBlock()
        val rStmt = R_ForStatement(rDeclarator, rExpr, cIterableAdapter.rAdapter, rBodyStmt, cBlock.rBlock)

        val resVarStates = S_WhileStatement.calcResVarStatesDelta(loop.condVarStatesDelta, cBodyStmt)
        return C_Statement(rStmt, false, resVarStates)
    }

    private fun compileBody(ctx: C_StmtContext): C_Statement {
        ctx.blkCtx.frameCtx.ideCompCtx.trackScope(S_PosRange(headerEndPos, stmt.endPos), ctx.exprCtx)
        return stmt.compileSafe(ctx)
    }

    override fun discoverVars0(map: MutableTypedKeyMap): C_StatementVars {
        val block = C_StatementVarsBlock()

        val declared = mutableSetOf<R_Name>()
        declarator.discoverVars(declared)
        block.declared(declared)

        val bodyVars = stmt.discoverVars(map)
        block.declared(bodyVars.declared)
        block.modified(bodyVars.modified)

        val modified = block.modified()
        return C_StatementVars(immSetOf(), modified)
    }

    override fun returnsValue() = stmt.returnsValue()
}

internal class S_BreakStatement(
    startPos: S_Pos,
    endPos: S_Pos,
): S_Statement(startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        if (ctx.loop == null) {
            throw C_Error.more(startPos, "stmt_break_noloop", "Break without a loop")
        }
        val rStmt = R_BreakStatement()
        return C_Statement(rStmt, false)
    }
}

internal class S_ContinueStatement(
    startPos: S_Pos,
    endPos: S_Pos,
): S_Statement(startPos, endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        if (ctx.loop == null) {
            throw C_Error.more(startPos, "stmt_continue_noloop", "Continue without a loop")
        }
        val rStmt = R_ContinueStatement()
        return C_Statement(rStmt, false)
    }
}

internal class S_GuardStatement(
    startPos: S_Pos,
    private val stmt: S_Statement,
): S_Statement(startPos, stmt.endPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        if (repl) {
            ctx.msgCtx.error(startPos, "stmt_guard_repl", "Guard block not allowed in REPL")
        }

        if (!ctx.topLevel) {
            ctx.msgCtx.error(startPos, "stmt_guard_nested", "Guard block not allowed as a nested statement")
        }

        val defType = ctx.blkCtx.frameCtx.fnCtx.defCtx.definitionType
        if (defType != C_DefinitionType.OPERATION) {
            ctx.msgCtx.error(startPos, "stmt_guard_wrong_def:$defType", "Guard block is allowed only in operations")
        }

        if (ctx.afterGuardBlock) {
            ctx.msgCtx.error(startPos, "stmt_guard_after_guard", "Only one guard block is allowed")
        }

        val cSubStmt = stmt.compileSafe(ctx, repl)
        val rStmt = R_GuardStatement(cSubStmt.rStmt)
        return cSubStmt.copy(rStmt = rStmt, guardBlock = true)
    }
}
