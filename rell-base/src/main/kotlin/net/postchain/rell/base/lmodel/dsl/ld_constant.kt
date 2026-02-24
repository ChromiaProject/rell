/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.L_Constant
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.futures.FcFuture

internal class Ld_Constant(
    private val type: Ld_Type,
    private val value: Ld_ConstantValue,
) {
    fun finish(ctx: Ld_TypeFinishContext, simpleName: R_Name): L_Constant {
        val rType = type.finishR(ctx)
        val rValue = value.getValue(rType)
        return L_Constant(simpleName, rType, rValue)
    }

    fun process(ctx: Ld_NamespaceContext, simpleName: R_Name): FcFuture<L_Constant> {
        return ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finishCtx ->
            val rType = type.finishR(finishCtx.typeCtx)
            val rValue = value.getValue(rType)
            L_Constant(simpleName, rType, rValue)
        }
    }
}

sealed class Ld_ConstantValue {
    abstract fun strCode(): String
    abstract fun getValue(type: R_Type): Rt_Value

    companion object {
        fun make(value: Rt_Value): Ld_ConstantValue = Ld_ConstantValue_Value(value)
        fun make(getter: (R_Type) -> Rt_Value): Ld_ConstantValue = Ld_ConstantValue_Getter(getter)
    }
}

private class Ld_ConstantValue_Value(private val value: Rt_Value): Ld_ConstantValue() {
    override fun strCode() = value.strCode()
    override fun getValue(type: R_Type) = value
}

private class Ld_ConstantValue_Getter(private val getter: (R_Type) -> Rt_Value): Ld_ConstantValue() {
    override fun strCode() = "?"

    override fun getValue(type: R_Type): Rt_Value {
        return getter(type)
    }
}

class Ld_ConstantDslImpl(
    hdr: Ld_MemberHeader,
    private val type: Ld_Type,
    private val memberBuilder: Ld_MemberHeaderBuilder = Ld_MemberHeaderBuilder(hdr),
): Ld_ConstantDsl, Ld_MemberDsl by Ld_MemberDslImpl(memberBuilder) {
    private var bodyRes: Ld_BodyRes? = null

    override fun value(getter: (R_Type) -> Rt_Value): Ld_BodyResult {
        require(bodyRes == null)
        val res = Ld_BodyRes(Ld_ConstantValue_Getter(getter))
        bodyRes = res
        return res
    }

    internal fun build(block: Ld_ConstantDsl.() -> Ld_BodyResult): Ld_MemberDef<Ld_Constant> {
        val bodyTag = block(this)
        check(bodyTag === bodyRes)

        val memberHeader = memberBuilder.buildMemberHeader()
        val res = bodyRes!!
        val constant = Ld_Constant(type = type, value = res.value)
        return Ld_MemberDef(memberHeader, constant)
    }

    private class Ld_BodyRes(val value: Ld_ConstantValue): Ld_BodyResult()
}
