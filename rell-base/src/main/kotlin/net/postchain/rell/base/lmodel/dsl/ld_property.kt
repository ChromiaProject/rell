/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.lmodel.L_NamespaceProperty
import net.postchain.rell.base.lmodel.L_TypeProperty
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

abstract class Ld_PropertyValue {
    abstract fun finish(type: M_Type): Finish

    class Finish(val fn: C_SysFunction, val pure: Boolean)

    private class Ld_PropertyValue_NamespaceProp(
        private val internalState: Ld_InternalFunctionBodyState,
        private val block: (Rt_CallContext, R_Type) -> Rt_Value,
    ): Ld_PropertyValue() {
        override fun finish(type: M_Type): Finish {
            val rType = L_TypeUtils.getRTypeNotNull(type)
            val internalBody = internalState.bodyContextN { ctx, args ->
                Rt_Utils.checkEquals(args.size, 0)
                block(ctx, rType)
            }
            return Finish(internalBody.fn, internalBody.pure)
        }
    }

    private class Ld_PropertyValue_TypeProp(
        private val fnGetter: (R_Type) -> C_SysFunctionBody,
    ): Ld_PropertyValue() {
        override fun finish(type: M_Type): Finish {
            val rType = L_TypeUtils.getRTypeNotNull(type)
            val body = fnGetter(rType)
            val fn = C_SysFunction.direct(body)
            return Finish(fn, body.pure)
        }
    }

    companion object {
        internal fun namespaceProp(
            internalState: Ld_InternalFunctionBodyState,
            block: (Rt_CallContext, R_Type) -> Rt_Value,
        ): Ld_PropertyValue {
            return Ld_PropertyValue_NamespaceProp(internalState, block)
        }

        internal fun typeProp(fnGetter: (R_Type) -> C_SysFunctionBody): Ld_PropertyValue {
            return Ld_PropertyValue_TypeProp(fnGetter)
        }
    }
}

class Ld_NamespaceProperty(
    val memberHeader: Ld_MemberHeader,
    private val type: Ld_Type,
    private val value: Ld_PropertyValue,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_NamespaceProperty {
        val mType = type.finish(ctx)
        val valueFin = value.finish(mType)
        return L_NamespaceProperty(mType, valueFin.fn, valueFin.pure)
    }
}

class Ld_TypeProperty(
    val simpleName: R_Name,
    val memberHeader: Ld_MemberHeader,
    private val type: Ld_Type,
    private val value: Ld_PropertyValue,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_TypeProperty {
        val mType = type.finish(ctx)
        val valueFin = value.finish(mType)
        return L_TypeProperty(simpleName, mType, valueFin.fn, valueFin.pure)
    }
}

class Ld_NamespacePropertyDslImpl(
    hdr: Ld_MemberHeader,
    private val type: Ld_Type,
    pure: Boolean,
    private val memberBuilder: Ld_MemberHeaderBuilder = Ld_MemberHeaderBuilder(hdr),
): Ld_NamespacePropertyDsl, Ld_MemberDsl by Ld_MemberDslImpl(memberBuilder) {
    private val bodyBuilder = Ld_InternalFunctionBodyBuilder(Ld_InternalFunctionBodyState(
        pure = pure,
        validator = null,
        dbFunction = null,
    ))

    private var buildRes: Ld_BodyRes? = null

    override fun validate(validator: (C_SysFunctionCtx) -> Unit) {
        check(buildRes == null) { "Body already set" }
        bodyBuilder.validator(validator)
    }

    override fun value(block: (Rt_CallContext) -> Rt_Value): Ld_BodyResult {
        return value { ctx, _ ->
            block(ctx)
        }
    }

    override fun value(block: (Rt_CallContext, R_Type) -> Rt_Value): Ld_BodyResult {
        check(buildRes == null) { "Body already set" }

        val internalState = bodyBuilder.build()
        val propValue: Ld_PropertyValue = Ld_PropertyValue.namespaceProp(internalState) { ctx, rType ->
            block(ctx, rType)
        }

        val res = Ld_BodyRes(propValue)
        buildRes = res
        return res
    }

    fun build(block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult): Ld_NamespaceProperty {
        val bodyTag = block(this)
        check(bodyTag === buildRes)

        val res = buildRes!!
        return Ld_NamespaceProperty(
            memberHeader = memberBuilder.buildMemberHeader(),
            type = type,
            value = res.value,
        )
    }

    private class Ld_BodyRes(val value: Ld_PropertyValue): Ld_BodyResult()
}

class Ld_TypePropertyDslImpl(
    hdr: Ld_MemberHeader,
    private val simpleName: R_Name,
    private val type: Ld_Type,
    private val pure: Boolean,
    private val memberBuilder: Ld_MemberHeaderBuilder = Ld_MemberHeaderBuilder(hdr),
): Ld_TypePropertyDsl, Ld_MemberDsl by Ld_MemberDslImpl(memberBuilder) {
    private var bodyRes: Ld_BodyRes? = null

    override fun value(getter: (Rt_Value) -> Rt_Value): Ld_BodyResult {
        return value { self, _ ->
            getter(self)
        }
    }

    override fun value(getter: (Rt_Value, R_Type) -> Rt_Value): Ld_BodyResult {
        require(bodyRes == null)
        val value = Ld_PropertyValue.typeProp { rType ->
            C_SysFunctionBody.simple(pure = pure) { self -> getter(self, rType) }
        }
        val res = Ld_BodyRes(value)
        bodyRes = res
        return res
    }

    fun build(block: Ld_TypePropertyDsl.() -> Ld_BodyResult): Ld_TypeProperty {
        val bodyTag = block(this)
        check(bodyTag === bodyRes)

        val res = bodyRes!!
        return Ld_TypeProperty(
            simpleName,
            memberHeader = memberBuilder.buildMemberHeader(),
            type = type,
            value = res.value,
        )
    }

    private class Ld_BodyRes(val value: Ld_PropertyValue): Ld_BodyResult()
}
