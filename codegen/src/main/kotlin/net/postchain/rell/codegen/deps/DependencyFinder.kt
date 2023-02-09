package net.postchain.rell.codegen.deps

import net.postchain.rell.model.*

object DependencyFinder {

    fun findDependencies(types: Collection<R_Type>): Set<ClassName> {
        return types.flatMap { findDependencies(it) }.toSet()
    }

    fun findDependencies(type: R_Type?): Set<ClassName> {
        return when (type) {
            null -> emptySet()
            is R_TupleType -> findDependencies(type.componentTypes())
            is R_EnumType -> setOf(CamelCaseClassName.fromRellType(type))
            is R_NullableType -> findDependencies(type.valueType)
            is R_CollectionType -> findDependencies(type.elementType)
            is R_StructType -> findDependencies(type.componentTypes()) + setOf(CamelCaseClassName.fromRellType(type)) // Structs and struct<entity>
            is R_MapType -> findDependencies(type.keyType) + findDependencies(type.valueType)
            else -> setOf() // Entities and primitives
        }
    }
}