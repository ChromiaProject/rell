/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.core.C_Types
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_ExprVarFacts
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_ExistsExpr
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals

object Lib_Exists {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("exists", C_SysFn_Exists(false))
        function("empty", C_SysFn_Exists(true))
    }
}

private class C_SysFn_Exists(private val not: Boolean): C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 1 .. 1

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: List<S_Expr>): V_Expr {
        checkEquals(args.size, 1)

        val arg = args[0]

        val atCtx = ctx.atCtx
        val cArg = if (atCtx != null) {
            arg.compileNestedAt(ctx, atCtx)
        } else {
            arg.compile(ctx)
        }

        val vArg = cArg.value()
        val condition = compileCondition(vArg)
        if (condition == null) {
            C_LibFuncCaseUtils.errNoMatch(ctx.msgCtx, name.pos, name.str, listOf(null to vArg.type))
            return C_ExprUtils.errorVExpr(ctx, name.pos, R_BooleanType)
        }

        val preFacts = C_ExprVarFacts.forSubExpressions(listOf(vArg))
        val varFacts = preFacts.and(C_ExprVarFacts.forNullCheck(vArg, not))
        return V_ExistsExpr(ctx, name, vArg, condition, not, varFacts)
    }

    private fun compileCondition(arg: V_Expr): R_RequireCondition? {
        when (C_Types.removeNullable(arg.type)) {
            is R_CollectionType -> return R_RequireCondition_Collection
            is R_MapType -> return R_RequireCondition_Map
        }

        val argN = arg.asNullable().unwrap()
        if (argN.type is R_NullableType) {
            return R_RequireCondition_Nullable
        }

        return null
    }
}

private class V_ExistsExpr(
        exprCtx: C_ExprContext,
        private val name: LazyPosString,
        private val subExpr: V_Expr,
        private val condition: R_RequireCondition,
        private val not: Boolean,
        private val resVarFacts: C_ExprVarFacts
): V_Expr(exprCtx, name.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(R_BooleanType, subExpr)
    override fun varFacts0() = resVarFacts

    override fun toRExpr0(): R_Expr {
        val fn = R_SysFn_Exists(condition, not)
        val rArgs = listOf(subExpr.toRExpr())
        return C_ExprUtils.createSysCallRExpr(R_BooleanType, fn, rArgs, name)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbSubExpr = subExpr.toDbExpr()
        return Db_ExistsExpr(dbSubExpr, not)
    }
}

private class R_SysFn_Exists(private val condition: R_RequireCondition, private val not: Boolean): R_SysFunction_1() {
    override fun call(arg: Rt_Value): Rt_Value {
        val value = condition.calculate(arg)
        val exists = value != null
        val res = if (not) !exists else exists
        return Rt_BooleanValue.get(res)
    }
}
