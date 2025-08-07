/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import com.google.common.collect.Iterables
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.expr.C_AtTypeImplicitAttr
import net.postchain.rell.base.compiler.base.expr.C_TypeValueMember
import net.postchain.rell.base.compiler.base.expr.C_TypeValueMember_Value
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.flatMapToImmList
import net.postchain.rell.base.utils.mapNotNullToImmList

internal class C_LibTypeManager(modules: List<C_LibModule>) {
    private val allExtensionTypes = modules.flatMapToImmList { it.extensionTypes }

    private val typeCache = mutableMapOf<R_Type, C_TypeCacheEntry>()

    fun getConstructor(type: R_Type): C_GlobalFunction? {
        return if (type.isCacheable()) {
            val entry = getCacheEntry(type)
            entry.constructor
        } else {
            getConstructor0(type)
        }
    }

    private fun getConstructor0(type: R_Type): C_GlobalFunction? {
        return type.libType.getConstructor()
    }

    fun getStaticMembers(type: R_Type, name: R_Name): List<C_TypeStaticMember> {
        val staticMembers = getTypeMember(type, this::getStaticMembers0, C_TypeCacheEntry::staticMembers)
        return staticMembers.getByName(name)
    }

    fun getValueMembers(type: R_Type, name: R_Name): List<C_TypeValueMember> {
        val valueMembers = getValueMembersPrivate(type)
        return valueMembers.getByName(name)
    }

    fun getValueMembers(type: R_Type): List<C_TypeValueMember> {
        val valueMembers = getValueMembersPrivate(type)
        return valueMembers.getValues()
    }

    private fun getValueMembersPrivate(type: R_Type): C_LibTypeMembers<C_TypeValueMember> {
        return getTypeMember(type, this::getValueMembers0, C_TypeCacheEntry::valueMembers)
    }

    private fun <T> getTypeMember(type: R_Type, typeGetter: (R_Type) -> T, cacheGetter: (C_TypeCacheEntry) -> T): T {
        return if (type.isCacheable()) {
            val entry = getCacheEntry(type)
            cacheGetter(entry)
        } else {
            typeGetter(type)
        }
    }

    private fun getStaticMembers0(type: R_Type): C_LibTypeMembers<C_TypeStaticMember> {
        val libTypeMembers = type.libType.getStaticMembers()
        val libExtMembers = allExtensionTypes.mapNotNull { it.getExtStaticMembers(type.mType) }
        val allMembers = Iterables.concat(listOf(libTypeMembers), libExtMembers)
        return C_LibTypeMembers.combined(allMembers)
    }

    private fun getValueMembers0(type: R_Type): C_LibTypeMembers<C_TypeValueMember> {
        val libTypeMembers = type.libType.getValueMembers()
        val libExtMembers = allExtensionTypes.mapNotNull { it.getExtValueMembers(type.mType) }
        val allMembers = Iterables.concat(listOf(libTypeMembers), libExtMembers)
        return C_LibTypeMembers.combined(allMembers)
    }

    fun getAtImplicitAttrsByName(selfType: R_Type, attrName: R_Name): List<C_AtTypeImplicitAttr> {
        val members = getValueMembers(selfType, attrName)
        return members
            .mapNotNullToImmList {
                val mem = it as? C_TypeValueMember_Value
                if (mem == null) null else C_AtTypeImplicitAttr(mem, mem.valueType)
            }
    }

    fun getAtImplicitAttrsByType(selfType: R_Type, attrType: R_Type): List<C_AtTypeImplicitAttr> {
        val members = getValueMembersPrivate(selfType)
        return members.getValues()
            .mapNotNullToImmList {
                val valueType = (it as? C_TypeValueMember_Value)?.valueType
                if (valueType != attrType) null else C_AtTypeImplicitAttr(it, valueType)
            }
    }

    private fun getCacheEntry(type: R_Type): C_TypeCacheEntry {
        return typeCache.computeIfAbsent(type) {
            makeCacheEntry(type)
        }
    }

    private fun makeCacheEntry(type: R_Type): C_TypeCacheEntry {
        val constructor = getConstructor0(type)

        val staticMembers = getStaticMembers0(type)
        val staticMembers2 = C_LibTypeMembers.simple(staticMembers.getAll())

        val valueMembers = getValueMembers0(type)
        val valueMembers2 = C_LibTypeMembers.simple(valueMembers.getAll())

        return C_TypeCacheEntry(
            constructor = constructor,
            staticMembers = staticMembers2,
            valueMembers = valueMembers2,
        )
    }

    private class C_TypeCacheEntry(
        val constructor: C_GlobalFunction?,
        val staticMembers: C_LibTypeMembers<C_TypeStaticMember>,
        val valueMembers: C_LibTypeMembers<C_TypeValueMember>,
    )
}
