/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_ByteArrayValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_RowidValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_Constant
import net.postchain.rell.base.lmodel.L_ConstantDocSource
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.rtValueToRRConstant
import net.postchain.rell.base.utils.futures.FcFuture

class Ld_Constant(
    private val type: Ld_Type,
    private val value: Ld_ConstantValue,
) {
    fun finish(ctx: Ld_TypeFinishContext, simpleName: Name): L_Constant {
        val rType = type.finishR(ctx)
        val rValue = value.getValue(rType)
        return makeLConstant(simpleName, rType, rValue)
    }

    fun process(ctx: Ld_NamespaceContext, simpleName: Name): FcFuture<L_Constant> = ctx.fcExec.future()
        .after(ctx.finishCtxFuture)
        .compute { finishCtx ->
            val rType = type.finishR(finishCtx.typeCtx)
            val rValue = value.getValue(rType)
            makeLConstant(simpleName, rType, rValue)
        }

    private fun makeLConstant(simpleName: Name, rType: R_Type, rValue: Rt_Value): L_Constant {
        return L_Constant(
            simpleName,
            rType,
            lazy { rtValueToRRConstant(rType, rValue) },
            lazy { rValue.strCode() },
            rtValueToDocSource(rValue),
        )
    }

    private fun rtValueToDocSource(value: Rt_Value): L_ConstantDocSource = when (value) {
        Rt_NullValue -> L_ConstantDocSource.Null
        Rt_UnitValue -> L_ConstantDocSource.Unit
        is Rt_BooleanValue -> L_ConstantDocSource.Bool(value.value)
        is Rt_IntValue -> L_ConstantDocSource.Int(value.value)
        is Rt_BigIntegerValue -> L_ConstantDocSource.BigInt(value.value)
        is Rt_DecimalValue -> L_ConstantDocSource.Decimal(value.value)
        is Rt_TextValue -> L_ConstantDocSource.Text(value.value)
        is Rt_ByteArrayValue -> L_ConstantDocSource.Bytes(value.asByteArray())
        is Rt_RowidValue -> L_ConstantDocSource.Rowid(value.value)
        else -> L_ConstantDocSource.Complex(lazy { value.str(Rt_Value.StrFormat.V2) })
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

    fun build(block: Ld_ConstantDsl.() -> Ld_BodyResult): Ld_MemberDef<Ld_Constant> {
        val bodyTag = block(this)
        check(bodyTag === bodyRes)

        val memberHeader = memberBuilder.buildMemberHeader()
        val res = bodyRes!!
        val constant = Ld_Constant(type = type, value = res.value)
        return Ld_MemberDef(memberHeader, constant)
    }

    private class Ld_BodyRes(val value: Ld_ConstantValue): Ld_BodyResult()
}
