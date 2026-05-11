/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.schema

import net.postchain.rell.base.model.Index
import net.postchain.rell.base.model.Key
import net.postchain.rell.base.model.rr.RR_Attribute
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_EnumDefinition
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.ImmList

class RellSchema(
    val entities: List<Entity>
) {
    val entitiesMap: Map<String, Entity> = entities.associateBy { it.uniqueName }

    fun getEntity(moduleName: String, qualifiedName: String): Entity? = entitiesMap["$moduleName:$qualifiedName"]
}

class Entity(
    private val entityDef: RR_EntityDefinition,
    private val allEntities: ImmList<RR_EntityDefinition>,
    private val allEnums: ImmList<RR_EnumDefinition>,
) {
    val simpleName: String get() = entityDef.base.simpleName
    val qualifiedName: String get() = entityDef.base.defName.qualifiedName
    val moduleName: String get() = entityDef.base.defName.module
    val uniqueName: String get() = entityDef.base.appLevelName
    val keys: List<Key> get() = entityDef.keys
    val indices: List<Index> get() = entityDef.indexes
    val attributes: List<Attribute> = entityDef.attributes.map {
        Attribute(it.value, allEntities, allEnums)
    }
}

class Attribute(
    private val rrAttribute: RR_Attribute,
    private val allEntities: ImmList<RR_EntityDefinition>,
    private val allEnums: ImmList<RR_EnumDefinition>,
) {
    val name: String get() = rrAttribute.name
    val isReference: Boolean get() = rrAttribute.type is RR_Type.Entity
    val type: RR_Type get() = rrAttribute.type

    /**
     * Returns the app-level entity name for entity reference types, or `null` for non-entity types.
     */
    val entityTypeName: String?
        get() {
            val t = type
            return if (t is RR_Type.Entity) {
                allEntities[t.defIndex].base.appLevelName
            } else {
                null
            }
        }

    /**
     * Returns the simple entity name for entity reference types, or `null` for non-entity types.
     * Used for checking system entity names like "transaction" and "block".
     */
    val entitySimpleName: String?
        get() {
            val t = type
            return if (t is RR_Type.Entity) {
                allEntities[t.defIndex].base.simpleName
            } else {
                null
            }
        }

    /**
     * Returns the [RR_EnumDefinition] for enum types, or `null` for non-enum types.
     */
    val enumDefinition: RR_EnumDefinition?
        get() {
            val t = type
            return if (t is RR_Type.Enum) {
                allEnums[t.defIndex]
            } else {
                null
            }
        }

    /**
     * Returns a display name for the type, similar to the old `R_Type.name`:
     * - For primitives: lowercase kind name (e.g. "integer", "text")
     * - For entities: app-level name (e.g. "module:entity_name")
     * - For enums: app-level name
     * - For other types: a structural description
     */
    fun typeName(): String = when (val t = type) {
        is RR_Type.Primitive -> t.kind.name.lowercase()
        is RR_Type.Entity -> allEntities[t.defIndex].base.appLevelName
        is RR_Type.Enum -> allEnums[t.defIndex].base.appLevelName
        is RR_Type.Struct -> "struct"
        is RR_Type.Nullable -> "nullable"
        is RR_Type.List -> "list"
        is RR_Type.Set -> "set"
        is RR_Type.Map -> "map"
        is RR_Type.Tuple -> "tuple"
        else -> "other"
    }

    fun typeStr(): String = when (val t = type) {
        is RR_Type.Primitive -> t.kind.name.lowercase()
        is RR_Type.Entity -> "entity"
        is RR_Type.Struct -> "struct"
        is RR_Type.Enum -> "enum"
        is RR_Type.Nullable -> "nullable"
        is RR_Type.List -> "list"
        is RR_Type.Set -> "set"
        is RR_Type.Map -> "map"
        is RR_Type.Tuple -> "tuple"
        else -> "other"
    }

    fun isNumberType(): Boolean {
        return supportedNumberTypes.contains(typeStr())
    }

    fun isTextType(): Boolean {
        return supportedTextTypes.contains(typeStr())
    }

    companion object {
        private val supportedNumberTypes = setOf("integer", "big_integer", "decimal", "rowid")
        private val supportedTextTypes = setOf("text", "byte_array")
    }
}
