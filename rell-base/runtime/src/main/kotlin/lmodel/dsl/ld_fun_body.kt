/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.lmodel.L_FunctionBody
import net.postchain.rell.base.lmodel.L_FunctionBodyMeta
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.QualifiedName
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.LazyString

abstract class Ld_CommonFunctionBodyDslImpl(
    private val maker: Ld_CommonFunctionBodyMaker,
): Ld_CommonFunctionBodyDsl {
    final override val fnSimpleName: String get() = maker.fnSimpleName.str

    final override fun dbFunction(dbFn: Db_SysFunction) {
        maker.dbFunction(dbFn)
    }

    final override fun dbFunctionSimple(name: String, sql: String) {
        dbFunction(Db_SysFunction.simple(name, sql))
    }

    final override fun dbFunctionTemplate(name: String, arity: Int, template: String) {
        dbFunction(Db_SysFunction.template(name, arity, template))
    }

    final override fun dbFunctionCast(name: String, type: String) {
        dbFunction(Db_SysFunction.cast(name, type))
    }

    final override fun bodyN(rCode: (List<Rt_Value>) -> Rt_Value): Ld_BodyResult {
        return bodyContextN { _, args ->
            rCode(args)
        }
    }

    final override fun body(rCode: () -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkEquals(args.size, 0)
            rCode()
        }
    }

    final override fun body(rCode: (Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkEquals(args.size, 1)
            rCode(args[0])
        }
    }

    final override fun body(rCode: (Rt_Value, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkEquals(args.size, 2)
            rCode(args[0], args[1])
        }
    }

    final override fun body(rCode: (Rt_Value, Rt_Value, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkEquals(args.size, 3)
            rCode(args[0], args[1], args[2])
        }
    }

    final override fun body(rCode: (Rt_Value, Rt_Value, Rt_Value, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkEquals(args.size, 4)
            rCode(args[0], args[1], args[2], args[3])
        }
    }

    final override fun bodyOpt1(rCode: (Rt_Value, Rt_Value?) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkRange(args.size, 1, 2)
            rCode(args[0], args.getOrNull(1))
        }
    }

    final override fun bodyOpt2(rCode: (Rt_Value, Rt_Value, Rt_Value?) -> Rt_Value): Ld_BodyResult {
        return bodyN { args ->
            Rt_Utils.checkRange(args.size, 2, 3)
            rCode(args[0], args[1], args.getOrNull(2))
        }
    }

    final override fun bodyContextN(rCode: (Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_BodyResult {
        return maker.bodyContextN(rCode)
    }

    final override fun bodyContext(rCode: (Rt_CallContext) -> Rt_Value): Ld_BodyResult {
        return bodyContextN { ctx, args ->
            Rt_Utils.checkEquals(args.size, 0)
            rCode(ctx)
        }
    }

    final override fun bodyContext(rCode: (Rt_CallContext, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyContextN { ctx, args ->
            Rt_Utils.checkEquals(args.size, 1)
            rCode(ctx, args[0])
        }
    }

    final override fun bodyContext(rCode: (Rt_CallContext, Rt_Value, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyContextN { ctx, args ->
            Rt_Utils.checkEquals(args.size, 2)
            rCode(ctx, args[0], args[1])
        }
    }

    final override fun bodyContext(rCode: (Rt_CallContext, Rt_Value, Rt_Value, Rt_Value) -> Rt_Value): Ld_BodyResult {
        return bodyContextN { ctx, args ->
            Rt_Utils.checkEquals(args.size, 3)
            rCode(ctx, args[0], args[1], args[2])
        }
    }
}

class Ld_FunctionBodyDslImpl(
    private val maker: Ld_FunctionBodyMaker,
): Ld_CommonFunctionBodyDslImpl(maker), Ld_FunctionBodyDsl {
    override fun validate(validator: (C_SysFunctionCtx) -> Unit) {
        return maker.validator(validator)
    }

    override fun bodyRaw(body: C_SysFunctionBody): Ld_BodyResult {
        return maker.bodyRaw(body)
    }

    override fun bodyMeta(block: Ld_FunctionMetaBodyDsl.() -> Ld_BodyResult): Ld_BodyResult {
        return maker.bodyMeta(block)
    }
}

interface Ld_CommonFunctionBodyMaker {
    val fnSimpleName: Name

    fun validator(validator: (C_SysFunctionCtx) -> Unit)
    fun dbFunction(dbFn: Db_SysFunction)
    fun bodyContextN(rCode: (Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_BodyResult
    fun bodyRaw(body: C_SysFunctionBody): Ld_BodyResult
}

interface Ld_FunctionBodyMaker: Ld_CommonFunctionBodyMaker {
    fun bodyMeta(block: Ld_FunctionMetaBodyDsl.() -> Ld_BodyResult): Ld_BodyResult
}

class Ld_InternalFunctionBody(val fn: C_SysFunction, val pure: Boolean)

class Ld_InternalFunctionBodyState(
    val pure: Boolean?,
    val validator: ((C_SysFunctionCtx) -> Unit)?,
    val dbFunction: Db_SysFunction?,
) {
    fun bodyContextN(rCode: (Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_InternalFunctionBody {
        val rFn = R_SysFunction { ctx, args ->
            rCode(ctx, args)
        }
        val body = C_SysFunctionBody(pure = pure ?: false, rFn, dbFunction)
        return body0(body)
    }

    fun bodyRaw(body: C_SysFunctionBody): Ld_InternalFunctionBody {
        Ld_Exception.check(pure == null || pure == body.pure) {
            "body:pure_diff:$pure:${body.pure}" to "Pure already set and differs"
        }
        Ld_Exception.check(dbFunction == null) { "body:db_fn_already_set" to "DB function already set" }
        return body0(body)
    }

    private fun body0(body: C_SysFunctionBody): Ld_InternalFunctionBody {
        val fn = C_SysFunction.direct(body)
        val fn2 = if (validator == null) fn else C_SysFunction.validating(fn, validator)
        return Ld_InternalFunctionBody(fn2, body.pure)
    }
}

class Ld_InternalFunctionBodyBuilder(
    state: Ld_InternalFunctionBodyState?,
) {
    private var pure: Boolean? = state?.pure
    private var validator: ((C_SysFunctionCtx) -> Unit)? = state?.validator
    private var dbFunction: Db_SysFunction? = state?.dbFunction
    private var built = false

    fun isEmpty(): Boolean {
        // pure is ignored
        return validator == null && dbFunction == null && !built
    }

    fun build(): Ld_InternalFunctionBodyState {
        check(!built)
        built = true
        return Ld_InternalFunctionBodyState(pure, validator, dbFunction)
    }

    fun validator(validator: (C_SysFunctionCtx) -> Unit) {
        require(this.validator == null) { "Validator already set" }
        require(!built) { "Body already created" }
        this.validator = validator
    }

    fun dbFunction(dbFn: Db_SysFunction) {
        require(dbFunction == null) { "DB function already set" }
        require(!built) { "Body already created" }
        dbFunction = dbFn
    }
}

sealed class Ld_FunctionBody(val pure: Boolean) {
    abstract fun finish(qualifiedName: QualifiedName): L_FunctionBody
}

private class Ld_FunctionBody_Direct(pure: Boolean, private val lBody: L_FunctionBody): Ld_FunctionBody(pure) {
    override fun finish(qualifiedName: QualifiedName) = lBody
}

private class Ld_FunctionBody_Meta(
        pure: Boolean,
        private val fnSimpleName: Name,
        private val internalState: Ld_InternalFunctionBodyState,
        private val block: Ld_FunctionMetaBodyDsl.() -> Ld_BodyResult,
): Ld_FunctionBody(pure) {
    override fun finish(qualifiedName: QualifiedName): L_FunctionBody {
        val fnQualifiedName = LazyString.of { qualifiedName.str() }
        return L_FunctionBody.delegating { meta ->
            val metaBuilder = Ld_FunctionMetaBodyBuilder(fnSimpleName, fnQualifiedName, internalState)
            val metaDslBuilder = Ld_FunctionMetaBodyDslImpl(meta, metaBuilder)
            val bodyRes = block(metaDslBuilder)
            metaBuilder.build(bodyRes)
        }
    }
}

class Ld_FunctionBodyBuilder(
        override val fnSimpleName: Name,
        pure: Boolean?,
): Ld_FunctionBodyMaker {
    private val internalBuilder = Ld_InternalFunctionBodyBuilder(Ld_InternalFunctionBodyState(
        pure = pure,
        validator = null,
        dbFunction = null,
    ))

    private var bodyRes: Ld_BodyRes? = null

    fun isEmpty(): Boolean = internalBuilder.isEmpty() && bodyRes == null

    fun build(bodyRes: Ld_BodyResult): Ld_FunctionBody {
        val actualBodyRes = this.bodyRes
        require(actualBodyRes != null) { "Body not set" }
        require(bodyRes === actualBodyRes)
        return actualBodyRes.actualBody
    }

    override fun validator(validator: (C_SysFunctionCtx) -> Unit) {
        internalBuilder.validator(validator)
    }

    override fun dbFunction(dbFn: Db_SysFunction) {
        internalBuilder.dbFunction(dbFn)
    }

    override fun bodyContextN(rCode: (Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_BodyResult {
        val internalState = internalBuilder.build()
        val fn = internalState.bodyContextN(rCode)
        return bodyInternal(fn)
    }

    override fun bodyRaw(body: C_SysFunctionBody): Ld_BodyResult {
        val internalState = internalBuilder.build()
        val fn = internalState.bodyRaw(body)
        return bodyInternal(fn)
    }

    override fun bodyMeta(block: Ld_FunctionMetaBodyDsl.() -> Ld_BodyResult): Ld_BodyResult {
        require(bodyRes == null) { "Body already set" }
        var internalState = internalBuilder.build()
        val pure = internalState.pure ?: false
        internalState = Ld_InternalFunctionBodyState(
            pure = pure,
            validator = internalState.validator,
            dbFunction = internalState.dbFunction
        )
        return body0(Ld_FunctionBody_Meta(pure, fnSimpleName, internalState, block))
    }

    private fun bodyInternal(internalBody: Ld_InternalFunctionBody): Ld_BodyResult {
        val body = L_FunctionBody.direct(internalBody.fn)
        return body0(Ld_FunctionBody_Direct(internalBody.pure, body))
    }

    private fun body0(body: Ld_FunctionBody): Ld_BodyResult {
        require(this.bodyRes == null)
        val res = Ld_BodyRes(body)
        bodyRes = res
        return res
    }

    private class Ld_BodyRes(val actualBody: Ld_FunctionBody): Ld_BodyResult()
}

interface Ld_FunctionMetaBodyMaker: Ld_CommonFunctionBodyMaker {
    val fnQualifiedName: LazyString

    fun validationError(code: String, msg: String)
}

private class Ld_FunctionMetaBodyBuilder(
        override val fnSimpleName: Name,
        override val fnQualifiedName: LazyString,
        internalState: Ld_InternalFunctionBodyState,
): Ld_FunctionMetaBodyMaker {
    private val internalBuilder = Ld_InternalFunctionBodyBuilder(internalState)
    private var validationError: C_CodeMsg? = null
    private var bodyRes: Ld_BodyRes? = null

    fun build(bodyRes: Ld_BodyResult): C_SysFunction {
        val actualBodyRes = this.bodyRes
        require(actualBodyRes != null) { "Body not set" }
        require(bodyRes === actualBodyRes)
        return actualBodyRes.fn
    }

    override fun validator(validator: (C_SysFunctionCtx) -> Unit) {
        internalBuilder.validator(validator)
    }

    override fun dbFunction(dbFn: Db_SysFunction) {
        internalBuilder.dbFunction(dbFn)
    }

    override fun validationError(code: String, msg: String) {
        require(validationError == null) { "Validation error already reported" }
        require(bodyRes == null) { "Body already set" }
        validationError = C_CodeMsg(code, msg)
    }

    override fun bodyContextN(rCode: (Rt_CallContext, List<Rt_Value>) -> Rt_Value): Ld_BodyResult {
        val internalState = internalBuilder.build()
        val internalBody = internalState.bodyContextN(rCode)
        return body0(internalBody)
    }

    override fun bodyRaw(body: C_SysFunctionBody): Ld_BodyResult {
        val internalState = internalBuilder.build()
        val internalBody = internalState.bodyRaw(body)
        return body0(internalBody)
    }

    private fun body0(internalBody: Ld_InternalFunctionBody): Ld_BodyResult {
        require(this.bodyRes == null) { "Body already set" }

        var fn = internalBody.fn

        val err = validationError
        if (err != null) {
            fn = C_SysFunction.validating(fn) { ctx ->
                ctx.exprCtx.msgCtx.error(ctx.callPos, err.code, err.msg)
            }
        }

        val res = Ld_BodyRes(fn)
        bodyRes = res
        return res
    }

    private class Ld_BodyRes(val fn: C_SysFunction): Ld_BodyResult()
}

class Ld_FunctionMetaBodyDslImpl(
    override val fnBodyMeta: L_FunctionBodyMeta,
    private val maker: Ld_FunctionMetaBodyMaker,
): Ld_CommonFunctionBodyDslImpl(maker), Ld_FunctionMetaBodyDsl {
    override val fnQualifiedName: String get() = maker.fnQualifiedName.value

    override fun validationError(code: String, msg: String) {
        maker.validationError(code, msg)
    }
}
