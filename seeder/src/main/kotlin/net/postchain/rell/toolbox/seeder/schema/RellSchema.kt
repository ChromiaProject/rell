package net.postchain.rell.toolbox.seeder

import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Index
import net.postchain.rell.base.model.R_Key
import net.postchain.rell.base.model.R_Type

class RellSchema(
    val entities: List<Entity>
) {
    val entitiesMap: Map<String, Entity> = entities.associateBy { it.uniqueName }

    fun getEntity(moduleName: String, qualifiedName: String): Entity? = entitiesMap["$moduleName:$qualifiedName"]
}

class Entity(private val entityDef: R_EntityDefinition) {
    val simpleName: String get() = entityDef.defName.simpleName
    val qualifiedName: String get() = entityDef.defName.qualifiedName
    val moduleName: String get() = entityDef.defName.module
    val uniqueName: String get() = entityDef.defName.appLevelName
    val keys: List<R_Key> get() = entityDef.keys
    val indices: List<R_Index> get() = entityDef.indexes
    val attributes: List<Attribute> = entityDef.attributes.map {
        Attribute(it.value)
    }
}

class Attribute(private val rAttribute: R_Attribute) {
    val name: String get() = rAttribute.name
    val isReference: Boolean get() = !rAttribute.type.completeFlags().pure
    val type: R_Type get() = rAttribute.type

    fun isNumberType(): Boolean {
        return supportedNumberTypes.contains(type.toString())
    }

    fun isTextType(): Boolean {
        return supportedTextTypes.contains(type.toString())
    }

    companion object {
        private val supportedNumberTypes = setOf("integer", "big_integer", "decimal", "rowid")
        private val supportedTextTypes = setOf("text", "byte_array")
    }
}
