/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.core.C_Types
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_ExprVarStatesDelta
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.compiler.vexpr.V_UnaryExpr
import net.postchain.rell.base.compiler.vexpr.V_UnaryOp_IsNull
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_SysFunction_1
import net.postchain.rell.base.model.expr.Db_ExistsExpr
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

object Lib_Exists {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("exists", C_SysFn_Exists(false), since = "0.6.0") {
            comment("""
                Checks if a value is present, i.e. not `null` and not empty.

                The negation of `empty()`.

                Accepts arguments of nullable type (`T?`), checking that they are not null, and of collection type
                (`collection<T>`), checking that they contain at least one element. Where an argument is a nullable
                collection (`collection<T>?`), it is checked both for non-nullity and non-emptiness.

                Examples:
                - `val x: integer? = null; exists(x)` returns `false`
                - `val y: integer? = 1; exists(y)` returns `true`
                - `val l1: list<integer>? = null; exists(l1)` returns `false`
                - `val l2: list<integer>? = []; exists(l2)` returns `false`
                - `val l3: list<integer>? = [1]; exists(l3)` returns `true`

                Note that when `exists()` is used within a database at-expression, and its argument is also a database
                at-expression, the inner at-expression can refer to entities of the outer one, as long as the inner uses
                `@*` (as opposed to `@`, `@+` or `@?`). Such expressions are efficient as they can be translated into a
                single nested SQL query.

                Inner at-expressions can use `@`, `@+` or `@?`, but in those cases, the inner expression cannot refer to
                entities of the outer one, and such cases are typically slow as they cannot be translated into a single
                nested query. They must instead be translated into multiple SQL queries, with the existence check
                occurring in the Rell runtime rather than in the database, where it could be done more efficiently.
                The translation into multiple queries also prevents the inner expression from referencing the outer
                expression, as this is only possible through a translation into SQL that leverages the scoping rules of
                nested SQL queries.

                Examples:

                - `user @* { exists( company @* { .city == user.city } ) }` returns all `user`s who share a `city` with
                a `company`.
                - `user @* { exists( company @* { user.city } ) }` is equivalent to the above, but uses the more concise
                attribute matching syntax.

                @return `true` if `value` is not `null` and not an empty collection, `false` otherwise
                @see 1. <a href="empty.html"><code>empty</code> - Rell Standard Library</a>
            """)
        }
        function("empty", C_SysFn_Exists(true), since = "0.8.0") {
            comment("""
                Checks if a value is absent, i.e. `null` or empty.

                Equivalent to `not exists(...)`.

                @return `true` if `value` is `null` or an empty collection, `false` otherwise
                @see 1. <a href="exists.html"><code>exists</code> - Rell Standard Library</a>
            """)
        }
    }
}

private class C_SysFn_Exists(private val not: Boolean): C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 1 .. 1

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
        checkEquals(args.size, 1)

        val arg = args[0]

        val atCtx = ctx.atCtx
        val cArg = if (atCtx != null) {
            arg.compileNestedAt(ctx, atCtx)
        } else {
            arg.compile(ctx)
        }

        val vArg = cArg.vExpr()
        val resVarStates = C_ExprVarStatesDelta.forNullCheck(vArg, not)

        val condition = when (C_Types.removeNullable(vArg.type)) {
            is R_CollectionType -> R_RequireCondition_Collection
            is R_MapType -> R_RequireCondition_Map
            else -> null
        }

        if (condition != null) {
            return V_ExistsExpr(ctx, name, vArg, condition, not, resVarStates)
        }

        val vArgN = vArg.asNullable().unwrap()
        if (vArgN.type is R_NullableType) {
            return V_UnaryExpr(ctx, vArg.pos, V_UnaryOp_IsNull(!not), vArg, resVarStates)
        }

        C_LibFuncCaseUtils.errNoMatch(ctx.msgCtx, name.pos, name.str, listOf(null to vArg.type))
        return C_ExprUtils.errorVExpr(ctx, name.pos, R_BooleanType)
    }
}

private class V_ExistsExpr(
    exprCtx: C_ExprContext,
    private val name: LazyPosString,
    private val subExpr: V_Expr,
    private val condition: R_RequireCondition,
    private val not: Boolean,
    private val resVarStates: C_ExprVarStatesDelta,
): V_Expr(exprCtx, name.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(R_BooleanType, subExpr)
    override fun varStatesDelta0() = resVarStates

    override fun toRExpr(): R_Expr {
        val fn = R_SysFn_Exists(condition, not)
        val rArgs = immListOf(subExpr.toRExpr())
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
