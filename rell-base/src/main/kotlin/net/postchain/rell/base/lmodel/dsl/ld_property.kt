/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.lmodel.L_NamespaceProperty
import net.postchain.rell.base.lmodel.L_TypeProperty
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

class Ld_NamespaceProperty(
    val memberHeader: Ld_MemberHeader,
    private val type: Ld_Type,
    private val fn: C_SysFunction,
    private val pure: Boolean,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_NamespaceProperty {
        val mType = type.finish(ctx)
        return L_NamespaceProperty(mType, fn, pure)
    }
}

class Ld_TypeProperty(
    val simpleName: R_Name,
    val memberHeader: Ld_MemberHeader,
    private val type: Ld_Type,
    private val body: C_SysFunctionBody,
) {
    fun finish(ctx: Ld_TypeFinishContext): L_TypeProperty {
        val mType = type.finish(ctx)
        return L_TypeProperty(simpleName, mType, body)
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
        check(buildRes == null) { "Body already set" }

        val internalState = bodyBuilder.build()
        val internalBody = internalState.bodyContextN { ctx, args ->
            Rt_Utils.checkEquals(args.size, 0)
            block(ctx)
        }

        val res = Ld_BodyRes(internalBody)
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
            fn = res.body.fn,
            pure = res.body.pure,
        )
    }

    private class Ld_BodyRes(val body: Ld_InternalFunctionBody): Ld_BodyResult()
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
        require(bodyRes == null)
        val res = Ld_BodyRes(C_SysFunctionBody.simple(pure = pure, rCode = getter))
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
            body = res.body,
        )
    }

    private class Ld_BodyRes(val body: C_SysFunctionBody): Ld_BodyResult()
}
