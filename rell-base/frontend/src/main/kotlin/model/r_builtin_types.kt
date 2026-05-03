/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_BigIntegerToDecimal
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_IntegerToBigInteger
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_IntegerToDecimal
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.immListOf

object R_IntegerType: R_PrimitiveType("integer") {
    override fun isComparable() = true
}

object R_BigIntegerType: R_PrimitiveType("big_integer") {
    override fun isComparable() = true
    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        return when (sourceType) {
            R_IntegerType -> C_TypeAdapter_IntegerToBigInteger
            else -> super.getTypeAdapter(sourceType)
        }
    }

}

object R_DecimalType: R_PrimitiveType("decimal") {
    override fun isComparable() = true
    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        return when (sourceType) {
            R_IntegerType -> C_TypeAdapter_IntegerToDecimal
            R_BigIntegerType -> C_TypeAdapter_BigIntegerToDecimal
            else -> super.getTypeAdapter(sourceType)
        }
    }

}

object R_TextType: R_PrimitiveType("text") {
    override fun isComparable() = true
}

object R_ByteArrayType: R_PrimitiveType("byte_array") {
    override fun isComparable() = true
}

object R_BooleanType: R_PrimitiveType("boolean") {
    override fun isComparable() = true
}

object R_RowidType: R_PrimitiveType("rowid") {
    override fun isComparable() = true
}

object R_RangeType: R_PrimitiveType("range") {
    override fun isComparable() = true
    override fun isDirectVirtualable() = false
    override fun isDirectPure() = true
    override fun isReference() = true
}

object R_JsonType: R_PrimitiveType("json")

object R_GtvType: R_PrimitiveType("gtv") {
    override fun isReference() = true
    override fun isDirectPure() = true
}

object R_UnitType: R_PrimitiveType("unit")
object R_SignerType: R_PrimitiveType("signer")
object R_GUIDType: R_PrimitiveType("guid")

// --- Collection types ---

sealed class R_CollectionType(
    val elementType: R_Type,
    private val baseName: String,
): R_LibGenericType(baseName, immListOf(elementType)) {
    private val isError = elementType.isError()

    final override fun isReference() = true
    final override fun isError() = isError
    final override fun isDirectMutable() = true
    final override fun getTypeArgs() = immListOf(elementType)
    final override fun strCode() = name

    final override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[this::class]) {
            "C_LibTypeDef not registered for collection type: $baseName"
        }
        return C_LibType.make(def, elementType)
    }

}

class R_ListType(elementType: R_Type): R_CollectionType(elementType, "list") {
    val virtualType = R_VirtualListType(this)

    override fun equals0(other: R_Type): Boolean = other is R_ListType && elementType == other.elementType
    override fun hashCode0() = elementType.hashCode()
    override fun isComparable() = elementType.isComparable()

    override fun getTypeMeta0() = META
    override fun docType() = DocUtils.docTypeGeneric("list", elementType)
    override fun isAssignableFrom(type: R_Type) = type is R_ListType && elementType.isAssignableArg(type.elementType)

    companion object {
        val META = R_TypeMeta.make { t -> R_ListType(t) }
    }
}

class R_SetType(elementType: R_Type): R_CollectionType(elementType, "set") {
    val virtualType = R_VirtualSetType(this)

    override fun equals0(other: R_Type): Boolean = other is R_SetType && elementType == other.elementType
    override fun hashCode0() = elementType.hashCode()

    override fun getTypeMeta0() = META
    override fun docType() = DocUtils.docTypeGeneric("set", elementType)
    override fun isAssignableFrom(type: R_Type) = type is R_SetType && elementType.isAssignableArg(type.elementType)

    companion object {
        val META = R_TypeMeta.make { t -> R_SetType(t) }
    }
}

// --- Map types ---

data class R_MapKeyValueTypes(val key: R_Type, val value: R_Type)

class R_MapType(
    val keyValueTypes: R_MapKeyValueTypes
): R_LibGenericType("map", immListOf(keyValueTypes.key, keyValueTypes.value)) {
    constructor(keyType: R_Type, valueType: R_Type): this(R_MapKeyValueTypes(keyType, valueType))

    val keyType = keyValueTypes.key
    val valueType = keyValueTypes.value
    val virtualType = R_VirtualMapType(this)

    val legacyEntryType: R_TupleType by lazy {
        R_TupleType.makeNamed("k" to keyType, "v" to valueType)
    }

    private val isError = keyType.isError() || valueType.isError()

    override fun equals0(other: R_Type) = other is R_MapType && keyValueTypes == other.keyValueTypes
    override fun hashCode0() = keyValueTypes.hashCode()

    override fun isReference() = true
    override fun isError() = isError
    override fun isDirectMutable() = true
    override fun isDirectVirtualable() = keyType == R_TextType

    override fun strCode() = name
    override fun getTypeArgs() = immListOf(keyType, valueType)
    override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[R_MapType::class]) {
            "C_LibTypeDef not registered for map type"
        }
        return C_LibType.make(def, keyType, valueType)
    }
    override fun getTypeMeta0() = META
    override fun docType() = DocUtils.docTypeGeneric("map", keyType, valueType)

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type is R_MapType
                && keyType.isAssignableArg(type.keyType)
                && valueType.isAssignableArg(type.valueType)
    }

    companion object {
        val META = R_TypeMeta.make { k, v -> R_MapType(k, v) }
    }
}

// --- Operation type ---

class R_OperationType(
    val rOperation: R_OperationDefinition,
): R_SimpleType(rOperation.appLevelName, rOperation.cDefName) {
    init {
        checkNull(rOperation.type) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_OperationType && other.rOperation == rOperation
    override fun hashCode0(): Int = rOperation.hashCode()

    override fun isDirectVirtualable() = false
    override fun getLibType0() = C_LibType.make(this, DocCode.link(rOperation.moduleLevelName))
    override fun calcParentType() = R_GenericType("operation")
}

object R_RellErrorType: R_PrimitiveType("rell.error_type")
