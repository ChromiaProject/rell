/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.immListOf

sealed class R_VirtualCollectionType(val innerCollectionType: R_CollectionType): R_VirtualType(innerCollectionType) {
    val virtualElementType: R_Type = S_VirtualType.virtualMemberType(innerCollectionType.elementType)

    final override fun getTypeArgs() = immListOf(innerCollectionType.elementType, virtualElementType)

    final override fun getLibType0(): C_LibType {
        val def = checkNotNull(R_LibUniqueType.genericLibTypeDefRegistry[this::class]) {
            "C_LibTypeDef not registered for virtual collection type: ${this::class.simpleName}"
        }
        return C_LibType.make(def, innerCollectionType.elementType, virtualElementType)
    }
}
