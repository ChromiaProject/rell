/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.immListOf

class R_VirtualMapType(val innerType: R_MapType): R_VirtualType(innerType) {
    private val virtualValueType: R_Type = S_VirtualType.virtualMemberType(innerType.valueType)
    val virtualEntryType: R_TupleType = R_TupleType.make(innerType.keyType, virtualValueType)

    override fun equals0(other: R_Type): Boolean = other is R_VirtualMapType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()

    override fun getTypeArgs() = immListOf(innerType.keyType, innerType.valueType, virtualValueType)

    override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[R_VirtualMapType::class]) {
            "C_LibTypeDef not registered for virtual map type"
        }
        return C_LibType.make(def, innerType.keyType, innerType.valueType, virtualValueType)
    }

    companion object {
        val META = R_TypeMeta.make { k, v0, _ -> R_MapType(k, v0).virtualType }
    }
}
