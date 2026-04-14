/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.lib.C_SysProperty
import net.postchain.rell.base.lmodel.L_NamespaceProperty
import net.postchain.rell.base.lmodel.L_TypeProperty
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

abstract class Ld_PropertyValue {
    internal abstract fun finish(type: R_Type): Finish

    internal class Finish(val prop: C_SysProperty, val pure: Boolean)

    private class Ld_PropertyValue_NamespaceProp(
        private val internalState: Ld_InternalFunctionBodyState,
        private val block: (Rt_CallContext, R_Type) -> Rt_Value,
    ): Ld_PropertyValue() {
        override fun finish(type: R_Type): Finish {
            val internalBody = internalState.bodyContextN { ctx, args ->
                Rt_Utils.checkEquals(args.size, 0)
                block(ctx, type)
            }
            val prop = object: C_SysProperty() {
                override fun getFunction(type: R_Type) = internalBody.fn
            }
            return Finish(prop, internalBody.pure)
        }
    }

    private class Ld_PropertyValue_SimpleTypeProp(
        private val pure: Boolean,
        private val valueGetter: (Rt_Value, R_Type) -> Rt_Value,
    ): Ld_PropertyValue() {
        override fun finish(type: R_Type): Finish {
            val prop = object: C_SysProperty() {
                override fun getFunction(type: R_Type): C_SysFunction {
                    val body = C_SysFunctionBody.simple(pure = pure) { self ->
                        valueGetter(self, type)
                    }
                    return C_SysFunction.direct(body)
                }
            }
            return Finish(prop, pure)
        }
    }

    private class Ld_PropertyValue_SpecialTypeProp(
        private val body: C_SysFunctionBody,
    ): Ld_PropertyValue() {
        override fun finish(type: R_Type): Finish {
            val fn = C_SysFunction.direct(body)
            val prop = object: C_SysProperty() {
                override fun getFunction(type: R_Type) = fn
            }
            return Finish(prop, body.pure)
        }
    }

    companion object {
        internal fun namespaceProp(
            internalState: Ld_InternalFunctionBodyState,
            block: (Rt_CallContext, R_Type) -> Rt_Value,
        ): Ld_PropertyValue {
            return Ld_PropertyValue_NamespaceProp(internalState, block)
        }

        internal fun typeProp(pure: Boolean, valueGetter: (Rt_Value, R_Type) -> Rt_Value): Ld_PropertyValue {
            return Ld_PropertyValue_SimpleTypeProp(pure, valueGetter)
        }

        internal fun typeProp(body: C_SysFunctionBody): Ld_PropertyValue = Ld_PropertyValue_SpecialTypeProp(body)
    }
}

class Ld_NamespaceProperty(
    private val type: Ld_Type,
    private val value: Ld_PropertyValue,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_NamespaceProperty {
        val rType = type.finishR(ctx)
        val valueFin = value.finish(rType)
        return L_NamespaceProperty(rType, valueFin.prop, valueFin.pure)
    }
}

class Ld_TypeProperty(
    val simpleName: R_Name,
    private val type: Ld_Type,
    private val value: Ld_PropertyValue,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_TypeProperty {
        val rType = type.finishR(ctx)
        val valueFin = value.finish(rType)
        return L_TypeProperty(simpleName, rType, valueFin.prop, valueFin.pure)
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

    fun build(block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult): Ld_MemberDef<Ld_NamespaceProperty> {
        val bodyTag = block(this)
        check(bodyTag === buildRes)

        val res = buildRes!!
        val memberHeader = memberBuilder.buildMemberHeader()

        val property = Ld_NamespaceProperty(type = type, value = res.value)
        return Ld_MemberDef(memberHeader, property)
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
        val value = Ld_PropertyValue.typeProp(pure, getter)
        val res = Ld_BodyRes(value)
        bodyRes = res
        return res
    }

    fun build(block: Ld_TypePropertyDsl.() -> Ld_BodyResult): Ld_MemberDef<Ld_TypeProperty> {
        val bodyTag = block(this)
        check(bodyTag === bodyRes)

        val res = bodyRes!!
        val memberHeader = memberBuilder.buildMemberHeader()

        val property = Ld_TypeProperty(
            simpleName,
            type = type,
            value = res.value,
        )

        return Ld_MemberDef(memberHeader, property)
    }

    private class Ld_BodyRes(val value: Ld_PropertyValue): Ld_BodyResult()
}
