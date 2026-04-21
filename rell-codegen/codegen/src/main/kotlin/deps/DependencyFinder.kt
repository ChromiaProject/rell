/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.deps

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.mapToImmList


object DependencyFinder {

    fun findDependencies(types: Collection<R_Type>): Set<ClassName> {
        return types.flatMap { findDependencies(it) }.toSet()
    }

    fun findDependencies(type: R_Type?): Set<ClassName> {
        return when (type) {
            null -> emptySet()
            is R_TupleType -> findDependencies(type.fields.mapToImmList { it.type })
            is R_EnumType -> setOf(CamelCaseClassName.fromRellType(type))
            is R_NullableType -> findDependencies(type.valueType)
            is R_CollectionType -> findDependencies(type.elementType)
            is R_StructType -> findDependencies(type.struct.attributesList.mapToImmList { it.type }) + setOf(CamelCaseClassName.fromRellType(type)) // Structs and struct<entity>
            is R_MapType -> findDependencies(type.keyType) + findDependencies(type.valueType)
            else -> setOf() // Entities and primitives
        }
    }
}