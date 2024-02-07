/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.model.R_DefinitionMeta
import net.postchain.rell.base.model.R_LibSimpleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals

object Lib_Meta {
    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell") {
            type("meta", rType = R_RellMetaType) {
                constructor(C_SysFn_Meta)

                property("simple_name", type = "text", pure = true) {
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.simpleName
                    }
                }

                property("full_name", type = "text", pure = true) {
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.fullName
                    }
                }

                property("module_name", type = "text", pure = true) {
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.moduleName
                    }
                }

                property("mount_name", type = "text", pure = true) {
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.mountName
                    }
                }
            }
        }
    }

    fun makeMetaGetter(resultType: R_Type, getter: (R_DefinitionMeta) -> Rt_Value?): C_SpecialLibGlobalFunctionBody {
        return object: C_SysFn_BaseMeta(resultType) {
            override fun getResultValue(meta: R_DefinitionMeta): Rt_Value? {
                val res = getter(meta)
                return res
            }
        }
    }
}

private abstract class C_SysFn_BaseMeta(private val resultType: R_Type): C_SpecialLibGlobalFunctionBody() {
    final override fun paramCount() = 1 .. 1

    protected abstract fun getResultValue(meta: R_DefinitionMeta): Rt_Value?

    final override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: List<S_Expr>): V_Expr {
        checkEquals(1, args.size)

        val arg = args[0]
        val cArg = arg.compile(ctx)
        val meta = cArg.getDefMeta()

        val value = if (meta == null) null else getResultValue(meta)

        if (value == null) {
            cArg.valueOrError()
            ctx.msgCtx.error(name.pos, "expr_call:bad_arg:[${name.str}]", "Bad argument for function '${name.str}'")
            return C_ExprUtils.errorVExpr(ctx, name.pos, R_BooleanType)
        }

        return V_ConstantValueExpr(ctx, name.pos, value, resultType)
    }
}

private object C_SysFn_Meta: C_SysFn_BaseMeta(R_RellMetaType) {
    override fun getResultValue(meta: R_DefinitionMeta) = Rt_RellMetaValue(meta)
}

private object R_RellMetaType: R_LibSimpleType("rell.meta", C_DefinitionName("rell", "rell.meta")) {
    override fun isReference() = true
    override fun isDirectPure() = true
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.RELL_META_TYPE
}

private class Rt_RellMetaValue(private val meta: R_DefinitionMeta): Rt_Value() {
    val simpleName: Rt_Value by lazy { Rt_TextValue.get(meta.simpleName) }
    val moduleName: Rt_Value by lazy { Rt_TextValue.get(meta.moduleName) }
    val fullName: Rt_Value by lazy { Rt_TextValue.get(meta.fullName) }
    val mountName: Rt_Value by lazy { Rt_TextValue.get(meta.mountName.str()) }

    override val valueType = VALUE_TYPE
    override fun type(): R_Type = R_RellMetaType
    override fun str() = "meta[${meta.fullName}]"
    override fun strCode(showTupleFieldNames: Boolean) = "${R_RellMetaType.name}[${meta.fullName}]"

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("RELL_META")

        fun get(v: Rt_Value): Rt_RellMetaValue {
            return v.asType(Rt_RellMetaValue::class, VALUE_TYPE)
        }
    }
}
