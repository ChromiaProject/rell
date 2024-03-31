/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals

object Lib_RellHidden {
    val MODULE = C_LibModule.make("rell.hidden", Lib_Rell.MODULE) {
        namespace("_test", since = "0.13.2") {
            function("crash", result = "unit", since = "0.13.2") {
                param("message", "text")
                body { a ->
                    val s = a.asString()
                    throw RellInterpreterCrashException(s)
                }
            }

            function("throw", "unit", since = "0.13.2") {
                param("code", "text")
                param("msg", "text")
                body { a, b ->
                    val code = a.asString()
                    val msg = b.asString()
                    throw Rt_Exception.common("throw:$code", msg)
                }
            }

            val fnExternalChain = Lib_Meta.makeMetaGetter(R_NullableType(R_TextType)) { meta ->
                when {
                    meta.externalChain == null -> null
                    meta.externalChain.value == null -> Rt_NullValue
                    else -> Rt_TextValue.get(meta.externalChain.value)
                }
            }

            function("external_chain", fnExternalChain, since = "0.13.2")
        }

        function("_type_of", C_SysFn_TypeOf, since = "0.6.0")

        function("_nullable", pure = true, since = "0.6.0") {
            generic("T")
            result(type = "T?")
            param("value", type = "T")
            body { a -> a }
        }

        function("_nullable_int", "integer?", pure = true, since = "0.6.0") {
            param("value", type = "integer?")
            body { a -> a }
        }

        function("_nullable_text", result = "text?", pure = true, since = "0.6.0") {
            param("value", "text?")
            body { a -> a }
        }

        function("_nop", pure = true, since = "0.6.0") {
            generic("T")
            result("T")
            param("value", "T")
            body { a -> a }
        }

        function("_nop_print", pure = true, since = "0.6.0") {
            generic("T")
            result(type = "T")
            param("value", type = "T")
            bodyContext { ctx, a ->
                ctx.globalCtx.outPrinter.print(a.str())
                a
            }
        }

        function("_strict_str", result = "text", since = "0.6.0") {
            param("value", type = "anything")
            body { a ->
                val s = a.strCode()
                Rt_TextValue.get(s)
            }
        }
    }
}

private object C_SysFn_TypeOf: C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 1 .. 1

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: List<S_Expr>): V_Expr {
        checkEquals(1, args.size)

        val arg = args[0]
        val cArg = arg.compile(ctx)
        val vArg = cArg.value()

        val type = vArg.type
        val str = type.strCode()
        val value = Rt_TextValue.get(str)

        return V_ConstantValueExpr(ctx, name.pos, value, dependsOnAtExprs = vArg.info.dependsOnAtExprs)
    }
}
