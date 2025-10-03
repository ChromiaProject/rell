/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.immListOf

sealed class R_VirtualCollectionType(val innerCollectionType: R_CollectionType): R_VirtualType(innerCollectionType) {
    val virtualElementType: R_Type = S_VirtualType.virtualMemberType(innerCollectionType.elementType)

    protected abstract fun typeDef(): C_LibTypeDef

    final override fun getTypeArgs() = immListOf(innerCollectionType.elementType, virtualElementType)
    final override fun getLibType0() = C_LibType.make(typeDef(), innerCollectionType.elementType, virtualElementType)
}

sealed class Rt_VirtualCollectionValue(gtv: Gtv): Rt_VirtualValue(gtv) {
    override fun asVirtualCollection() = this
    abstract fun size(): Int
    abstract override fun asIterable(): Iterable<Rt_Value>
}
