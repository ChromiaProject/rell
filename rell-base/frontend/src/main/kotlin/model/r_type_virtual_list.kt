/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

class R_VirtualListType(val innerType: R_ListType): R_VirtualCollectionType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualListType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()

    companion object {
        val META = R_TypeMeta.make { t, _ ->
            R_ListType(t).virtualType
        }
    }
}
