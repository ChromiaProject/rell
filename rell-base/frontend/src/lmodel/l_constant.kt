/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import java.math.BigDecimal
import java.math.BigInteger

class L_Constant(
        val simpleName: Name,
        val rType: R_Type,
        val rrValueLazy: Lazy<RR_ConstantValue>,
        valueStrCode: Lazy<String>,
        val docSource: L_ConstantDocSource,
) {
    private val valueStrCode: String by valueStrCode

    val type: M_Type get() = rType.mType

    fun strCode(): String {
        return "constant $simpleName: ${rType.strCode()} = $valueStrCode"
    }
}

/**
 * Display-oriented projection of a constant value. Produced by the runtime from an `Rt_Value` at
 * stdlib build time and consumed by doc/renderer code paths that must not reach back into runtime
 * internals. Primitive variants hold typed values; [Complex] defers string rendering until first read
 * (stdlib bootstrap populates struct bodies lazily — eager `str()` during construction deadlocks).
 */
sealed interface L_ConstantDocSource {
    data object Null : L_ConstantDocSource
    data object Unit : L_ConstantDocSource
    data class Bool(val value: Boolean) : L_ConstantDocSource
    data class Int(val value: Long) : L_ConstantDocSource
    data class BigInt(val value: BigInteger) : L_ConstantDocSource
    data class Decimal(val value: BigDecimal) : L_ConstantDocSource
    data class Text(val value: String) : L_ConstantDocSource
    data class Bytes(val value: ByteArray) : L_ConstantDocSource {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && value.contentEquals(other.value))
        override fun hashCode(): kotlin.Int = value.contentHashCode()
    }
    data class Rowid(val value: Long) : L_ConstantDocSource
    class Complex(fallbackStr: Lazy<String>) : L_ConstantDocSource {
        val fallbackStr: String by fallbackStr
    }
}

class L_NamespaceMember_Constant(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}

class L_TypeDefMember_Constant(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}
