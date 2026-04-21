/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

class R_VirtualTupleType(val innerType: R_TupleType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualTupleType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun getTypeMeta0(): R_TypeMeta = Meta()
    override fun getTypeArgs() = immListOf(innerType)
    override fun explicitComponentTypes() = immListOf(innerType)

    override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[R_VirtualTupleType::class]) {
            "C_LibTypeDef not registered for virtual tuple type"
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
                is R_TupleType -> R_VirtualTupleType(argType)
                else -> null
            }
        }
    }
}
