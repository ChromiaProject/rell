package net.postchain.rell.codegen.deps

import net.postchain.rell.model.*

object DependencyFinder {

    fun findDependencies(types: Collection<R_Type>, includeEntities: Boolean = true): Set<ClassName> {
        return types.flatMap { findDependencies(it, includeEntities) }.toSet()
    }

    fun findDependencies(type: R_Type?, includeEntities: Boolean = true): Set<ClassName> {
        if (type is R_EntityType && !includeEntities) return emptySet()
        return when (type) {
            null -> emptySet()
            is R_TupleType -> findDependencies(type.componentTypes(), false)
            is R_EnumType -> setOf(CamelCaseClassName.fromString(type.name))
            is R_NullableType -> findDependencies(type.valueType, false)
            is R_CollectionType -> findDependencies(type.elementType, false)
            is R_EntityType -> findDependencies(type.componentTypes(), false) + setOf(CamelCaseClassName.fromString(type.name))
            is R_StructType -> findDependencies(type.componentTypes(), false) + setOf(CamelCaseClassName.fromString(type.name))
            is R_MapType -> findDependencies(type.keyType, false) + findDependencies(type.valueType, false)
            else -> setOf()
        }
    }
}