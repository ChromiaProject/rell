/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_TextType
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf
import org.jooq.impl.DSL

internal object Lib_RellHidden {
    val MODULE = C_LibModule.make("rell.hidden", Lib_Rell.MODULE) {
        namespace("_test", since = "0.13.2") {
            function("crash", result = "unit", since = "0.13.2") {
                val message by param(Rt_TextValue)
                body {
                    throw RellInterpreterCrashException(message.value)
                }
            }

            function("throw", "unit", since = "0.13.2") {
                val code by param(Rt_TextValue)
                val msg by param(Rt_TextValue)
                body {
                    throw Rt_Exception.common("throw:${code.value}", msg.value)
                }
            }

            val fnExternalChain = Lib_Meta.makeMetaGetter(R_NullableType(R_TextType)) { meta ->
                val ec = meta.externalChain
                when {
                    ec == null -> null
                    ec.value == null -> RR_ConstantValue.Null
                    else -> RR_ConstantValue.Text(ec.value!!)
                }
            }

            function("external_chain", fnExternalChain, since = "0.13.2")

            function("fake_assert", result = "unit", since = "0.14.0") {
                param("value", "boolean", implies = L_ParamImplication.TRUE)
                constant(Rt_UnitValue)
            }

            function("sleep", result = "unit", since = "0.15.1") {
                val ms by param(Rt_IntValue)
                bodyContext { ctx ->
                    val seconds = ms.value.toDouble() / 1000.0

                    val pgSleep = DSL.function("pg_sleep", Any::class.java, DSL.value(seconds))
                    val sql = JOOQ_CTX.renderInlined(DSL.select(pgSleep))
                    ctx.exeCtx.userSqlExec.execute(ParameterizedSql(sql, immListOf()))

                    Rt_UnitValue
                }
            }

            function("get_nulled", C_SysFn_GetNulled, since = "0.14.0")
        }

        function("_type_of", C_SysFn_TypeOf, since = "0.6.0")

        function("_nullable", pure = true, since = "0.6.0") {
            generic("T")
            result(type = "T?")
            val value by param("T", cast = Rt_Value)
            body { value }
        }

        function("_nullable_int", "integer?", pure = true, since = "0.6.0") {
            val value by param("integer?", cast = Rt_Value)
            body { value }
        }

        function("_nullable_text", result = "text?", pure = true, since = "0.6.0") {
            val value by param("text?", cast = Rt_Value)
            body { value }
        }

        function("_nop", pure = true, since = "0.6.0") {
            generic("T")
            result("T")
            val value by param("T", cast = Rt_Value)
            body { value }
        }

        function("_nop_print", pure = true, since = "0.6.0") {
            generic("T")
            result(type = "T")
            val value by param("T", cast = Rt_Value)
            bodyContext { ctx ->
                ctx.globalCtx.outPrinter.print(value.str())
                value
            }
        }

        function("_strict_str", result = "text", since = "0.6.0") {
            val value by param("anything", cast = Rt_Value)
            body {
                Rt_TextValue.get(value.strCode())
            }
        }
    }
}

private object C_SysFn_TypeOf: C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 1 .. 1

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
        checkEquals(1, args.size)

        val arg = args[0]
        val cArg = arg.compile(ctx)
        val vArg = cArg.vExpr()

        val type = vArg.type
        val str = type.strCode()
        val value = RR_ConstantValue.Text(str)

        return V_ConstantValueExpr(ctx, name.pos, value, dependsOnAtExprs = vArg.info.dependsOnAtExprs)
    }
}

private object C_SysFn_GetNulled: C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 1 .. 1

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
        checkEquals(1, args.size)

        val arg = args[0]
        val cArg = arg.compile(ctx)
        val vArg = cArg.vExpr()

        val varKey = vArg.varKey()
        if (varKey == null) {
            ctx.msgCtx.error(name.pos, "lib:$name:no_var_key", "No variable state key")
            return C_ExprUtils.errorVExpr(ctx, name.pos)
        }

        val nulled = ctx.varStates.getNulled(varKey)
        val res = when (nulled) { null -> "maybe"; true -> "yes"; false -> "no" }
        return V_ConstantValueExpr(ctx, name.pos, RR_ConstantValue.Text(res))
    }
}
