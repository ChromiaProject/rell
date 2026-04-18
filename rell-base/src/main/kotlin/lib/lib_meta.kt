/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_DefinitionMeta
import net.postchain.rell.base.model.R_LibUniqueType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_LibValueType
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals

object Lib_Meta {
    private const val SINCE0 = "0.13.5"

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell") {
            comment("""
                An API facilitating access to Rell definition metadata.

                A `rell.meta` value can be constructed with a reference to a Rell definition - modules, entities,
                objects, operations and queries are supported. The resulting value provides access to various metadata
                about the given definition, such as its name (in short and long formats) and the module in which it is
                declared.

                This API also provides a factory method `rell.meta.current_module()` for creating a `rell.meta` value
                about the current module.
            """)
            type("meta", rType = R_RellMetaType, since = SINCE0) {
                constructor(C_SysFn_Meta, since = SINCE0) {
                    comment("""
                        Construct a value of type `rell.meta`, which describes a definition.

                        `rell.meta` values describe the definition referenced in the argument to their constructor. The
                        referenced definition can be a module, entity, object, operation or query.

                        Given the following definitions:

                        ```rell
                        operation my_op(...) { ... }
                        entity my_entity { ... }
                        object my_object { ... }
                        query my_query(...) { ... }
                        ```

                        one can construct values of type `rell.meta` by writing `rell.meta(my_op)`,
                        `rell.meta(my_entity)`, `rell.meta(my_object)` or `rell.meta(my_query)`.

                        Where an external module is imported:

                        ```rell
                        import other_module;
                        ```

                        one can also construct a value of type `rell.meta` by writing `rell.meta(other_module)`.
                    """)
                }

                property("simple_name", type = "text", pure = true, since = SINCE0) {
                    comment("""
                        The simple name of the definition represented by this meta value.

                        For example, an operation `my_op` defined in a module `a.b.c`, and nested inside namespaces `n`
                        and `m` (where `n` is the outermost), would have the simple name `my_op`.

                        Where the kind of definition described by this meta value is a module, `simple_name` is the
                        last part of the module's path; e.g. for a module `a.b.c`, the `simple_name` is `c`.
                    """)
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.simpleName
                    }
                }

                property("full_name", type = "text", pure = true, since = SINCE0) {
                    comment("""
                        The full name of the definition represented by this meta value, including the names of the
                        module and namespace in which the definition occurs.

                        For example, an operation `my_op` defined in a module `a.b.c`, and nested inside namespaces `n`
                        and `m` (where `n` is the outermost), would have the full name `a.b.c:n.m.my_op`.

                        Where the kind of definition described by this meta value is a module, `full_name` is equal to
                        `module_name`.
                    """)
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.fullName
                    }
                }

                property("module_name", type = "text", pure = true, since = SINCE0) {
                    comment("""
                        The name of the module to which the definition represented by this meta value belongs.

                        For example, an operation `my_op` defined in a module `a.b.c`, would have the module name
                        `a.b.c`.

                        Where the kind of definition described by this meta value is a module, `module_name` is equal
                        to `full_name`.
                    """)
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.moduleName
                    }
                }

                property("mount_name", type = "text", pure = true, since = SINCE0) {
                    comment("""
                        The effective mount name of the definition represented by this meta value, determined by:
                        - the name of the definition
                        - the names of any namespaces in which the definition occurs
                        - any `@mount` annotations on the definition
                        - any `@mount` annotations on any namespaces in which the definition occurs
                        - any `@mount` annotations defined on the module in which the definition occurs, and any of its
                        parent modules
                    """)
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.mountName
                    }
                }

                property("kind_text", type = "text", pure = true, since = "0.13.10") {
                    comment("""
                        Text representing the kind of the definition described by this meta value.

                        Possible values are `module`, `entity`, `object`, `operation` and `query`.

                        For example, an operation `my_op` defined in a module `a.b.c`, would have the kind text
                        `operation`.
                    """)
                    value { a ->
                        val v = Rt_RellMetaValue.get(a)
                        v.kindText
                    }
                }

                staticFunction("current_module", C_SysFn_Meta_CurrentModule, since = "0.13.10") {
                    comment("""
                        Get a meta information value for the current module.

                        @return a value of type `rell.meta` representing the current module
                    """)
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

    final override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
        checkEquals(1, args.size)

        val arg = args[0]
        val cArg = arg.compile(ctx)
        val meta = cArg.getDefMeta()

        val value = if (meta == null) null else getResultValue(meta)

        if (value == null) {
            cArg.vExprOrError() // Process IDE info, error is ignored on purpose
            ctx.msgCtx.error(name.pos, "expr_call:bad_arg:[${name.str}]", "Bad argument for function '${name.str}'")
            return C_ExprUtils.errorVExpr(ctx, name.pos, R_BooleanType)
        }

        return V_ConstantValueExpr(ctx, name.pos, value, resultType)
    }
}

private object C_SysFn_Meta: C_SysFn_BaseMeta(R_RellMetaType) {
    override fun getResultValue(meta: R_DefinitionMeta) = Rt_RellMetaValue(meta)
}

private object C_SysFn_Meta_CurrentModule: C_SpecialLibGlobalFunctionBody() {
    override fun paramCount() = 0 .. 0

    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
        checkEquals(args.size, 0)
        val meta = ctx.modCtx.getModuleDefMeta()
        val value = Rt_RellMetaValue(meta)
        return V_ConstantValueExpr(ctx, name.pos, value, R_RellMetaType)
    }
}

private object R_RellMetaType: R_LibUniqueType("rell.meta", C_DefinitionName("rell", "rell.meta")) {
    override fun isReference() = true
    override fun isDirectPure() = true
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.RELL_META_TYPE
}

private class Rt_RellMetaValue(private val meta: R_DefinitionMeta): Rt_Value() {
    val kindText: Rt_Value by lazy { Rt_TextValue.get(meta.kind) }
    val simpleName: Rt_Value by lazy { Rt_TextValue.get(meta.simpleName) }
    val moduleName: Rt_Value by lazy { Rt_TextValue.get(meta.moduleName) }
    val fullName: Rt_Value by lazy { Rt_TextValue.get(meta.fullName) }
    val mountName: Rt_Value by lazy { Rt_TextValue.get(meta.mountName.str()) }

    override val valueType = VALUE_TYPE
    override fun type(): R_Type = R_RellMetaType
    override fun str(format: StrFormat) = "meta[${meta.fullName}]"
    override fun strCode(showTupleFieldNames: Boolean) = "${R_RellMetaType.name}[${meta.fullName}]"

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("RELL_META")

        fun get(v: Rt_Value): Rt_RellMetaValue {
            return v.asType(Rt_RellMetaValue::class, VALUE_TYPE)
        }
    }
}
