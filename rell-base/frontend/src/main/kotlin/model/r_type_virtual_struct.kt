/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

class R_VirtualStructType(val innerType: R_StructType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualStructType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun isCacheable() = true
    override fun getTypeArgs() = immListOf(innerType)

    override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[R_VirtualStructType::class]) {
            "C_LibTypeDef not registered for virtual struct type"
        }
        return C_LibType.make(
            def,
            innerType,
            valueMembers = R_LibTypeMemberRegistry.getValueMembers(this),
        )
    }

    private class Meta: R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type? {
            checkEquals(args.size, 1)
            return when (val argType = args[0]) {
                is R_StructType -> R_VirtualStructType(argType)
                else -> null
            }
        }
    }
}
