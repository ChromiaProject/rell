/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.L_FunctionBodyMeta
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_PrimitiveFactory
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass

@RellLibDsl
interface Ld_FunctionContextDsl {
    val fnSimpleName: String
}

@RellLibDsl
interface Ld_CommonFunctionDsl: Ld_FunctionContextDsl, Ld_FunctionBodyDsl, Ld_MemberDsl {
    fun deprecated(newName: String, error: Boolean = true)

    fun generic(
        name: String,
        subOf: String? = null,
        superOf: String? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun param(
        name: String,
        type: String,
        arity: L_ParamArity = L_ParamArity.ONE,
        exact: Boolean = false,
        nullable: Boolean = false,
        lazy: Boolean = false,
        implies: L_ParamImplication? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    /**
     * Declare the receiver of a member function as a typed property delegate:
     * `val self by self(Rt_IntValue)`. Must be the first typed parameter declaration.
     */
    fun <T : Rt_Value> self(type: Rt_ValueClass<T>): Ld_ParamRef<T>

    /**
     * Declare a mandatory parameter and bind it to a typed property delegate; the Rell parameter
     * name is the name of the delegating property: `val exponent by param(Rt_IntValue)`.
     */
    fun <T : Rt_Value> param(
        type: Rt_ValueClass<T>,
        exact: Boolean = false,
        implies: L_ParamImplication? = null,
        since: String? = null,
        comment: String? = null,
    ): Ld_ParamProvider<T>

    /**
     * Declare a mandatory parameter whose Rell type is given as a string — for composite or
     * generic types (`collection<-T>`, `list<T>`, ...) that no single value-class token can name —
     * bound to a typed delegate that casts the argument to [cast]; the Rell parameter name is the
     * name of the delegating property.
     */
    fun <T : Rt_Value> param(
        type: String,
        cast: Rt_ValueClass<T>,
        exact: Boolean = false,
        lazy: Boolean = false,
        nullable: Boolean = false,
        implies: L_ParamImplication? = null,
        since: String? = null,
        comment: String? = null,
    ): Ld_ParamProvider<T>

    /**
     * Declare an optional trailing parameter; the delegate yields `null` when the argument is
     * omitted at the call site. The Rell parameter name is the name of the delegating property.
     */
    fun <T : Rt_Value> paramOpt(
        type: Rt_ValueClass<T>,
        exact: Boolean = false,
        implies: L_ParamImplication? = null,
        since: String? = null,
        comment: String? = null,
    ): Ld_OptParamProvider<T>

    /**
     * Declare an optional trailing parameter whose Rell type is given as a string — for composite
     * or generic types that no single value-class token can name — bound to a typed delegate that
     * casts to [cast]; yields `null` when the argument is omitted at the call site. The Rell
     * parameter name is the name of the delegating property.
     */
    fun <T : Rt_Value> paramOpt(
        type: String,
        cast: Rt_ValueClass<T>,
        exact: Boolean = false,
        lazy: Boolean = false,
        nullable: Boolean = false,
        implies: L_ParamImplication? = null,
        since: String? = null,
        comment: String? = null,
    ): Ld_OptParamProvider<T>

    /**
     * Define the function body. Arguments are read through the [self] / [param] / [paramOpt]
     * delegates declared above; the lambda returns the raw payload (`Long`, `String`, ...) and
     * [returns] wraps it into the corresponding [Rt_Value].
     */
    fun <T : Rt_Value, N : Any> body(returns: Rt_PrimitiveFactory<T, N>, rCode: () -> N): Ld_BodyResult
}

@RellLibDsl
interface Ld_FunctionDsl: Ld_CommonFunctionDsl {
    fun result(type: String)

    fun alias(
        name: String,
        deprecated: C_MessageType? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )
}

@RellLibDsl
interface Ld_MethodDsl<T : Rt_Value>: Ld_FunctionDsl {
    /**
     * Declare the receiver of a member function as a typed property delegate without repeating the
     * value-class token: `val self by self()`. Available only inside a typed `type(valueClass, ...)`
     * declaration; fails loudly otherwise.
     */
    fun self(): Ld_ParamRef<T>
}

sealed interface Ld_BodyResult

@RellLibDsl
interface Ld_CommonFunctionBodyDsl: Ld_FunctionContextDsl {
    fun dbFunction(dbFn: Db_SysFunction)
    fun dbFunctionSimple(name: String, sql: String)
    fun dbFunctionTemplate(name: String, arity: Int, template: String)
    fun dbFunctionCast(name: String, type: String)

    fun bodyN(rCode: (List<Rt_Value>) -> Rt_Value): Ld_BodyResult
    fun bodyContextN(rCode: (ctx: Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_BodyResult

    fun body(rCode: () -> Rt_Value): Ld_BodyResult
    fun bodyContext(rCode: (ctx: Rt_CallContext) -> Rt_Value): Ld_BodyResult

    fun constant(value: Rt_Value): Ld_BodyResult = body { value }
}

@RellLibDsl
interface Ld_FunctionBodyDsl: Ld_CommonFunctionBodyDsl {
    fun validate(validator: (C_SysFunctionCtx) -> Unit)
    fun bodyRaw(body: C_SysFunctionBody): Ld_BodyResult
    fun bodyMeta(block: Ld_FunctionMetaBodyDsl.() -> Ld_BodyResult): Ld_BodyResult
}

@RellLibDsl
interface Ld_FunctionMetaBodyDsl: Ld_CommonFunctionBodyDsl {
    val fnQualifiedName: String
    val fnBodyMeta: L_FunctionBodyMeta

    fun validationError(code: String, msg: String)
}
