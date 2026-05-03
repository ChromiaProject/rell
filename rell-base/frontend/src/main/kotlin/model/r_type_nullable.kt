/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_Nullable
import net.postchain.rell.base.compiler.base.lib.C_LibType

import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.immListOf

class R_NullableType(val valueType: R_Type): R_CompositeType(calcName(valueType)) {
    override fun equals0(other: R_Type) = other is R_NullableType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()
    override fun getTypeArgs() = immListOf(valueType)

    override fun isComparable() = valueType.isComparable()
    override fun isReference() = valueType.isReference()
    override fun isError() = valueType.isError()
    override fun isDirectMutable() = false
    override fun isDirectPure() = true

    override fun strCode() = name
    override fun getLibType0() = C_LibType.make(M_Types.nullable(valueType.mType))
    override fun getTypeMeta0() = META

    override fun isAssignableFrom(type: R_Type): Boolean = type == this
            || type == R_NullType
            || (type is R_NullableType && valueType.isAssignableFrom(type.valueType))
            || valueType.isAssignableFrom(type)

    override fun isValid() = valueType != R_UnitType

    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        var adapter = super.getTypeAdapter(sourceType)
        if (adapter != null) {
            return adapter
        }

        if (sourceType is R_NullableType) {
            adapter = valueType.getTypeAdapter(sourceType.valueType)
            return if (adapter == null) null else C_TypeAdapter_Nullable(this, adapter)
        } else {
            return valueType.getTypeAdapter(sourceType)
        }
    }

    override fun docType(): DocType {
        val docValueType = valueType.docType()
        return DocType.nullable(docValueType)
    }

    companion object {
        private val META = R_TypeMeta.make { t -> R_NullableType(t) }

        private fun calcName(valueType: R_Type): String = when (valueType) {
            is R_FunctionType -> "(${valueType.name})?"
            else -> "${valueType.name}?"
        }
    }
}
