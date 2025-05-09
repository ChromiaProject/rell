package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.Entity
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.EntityConfig

class EntityConfigParser {
    private val attributeConfigParser = AttributeConfigParser()


    fun createEntityNode(
        entityName: String,
        entityNode: JsonNode,
        moduleName: String,
        rellSchema: RellSchema
    ): EntityConfig {
        val schemaEntity = findEntityInSchema(entityName, moduleName, rellSchema)
        val count = extractEntityCount(entityNode)
        val attributeConfigs = parseAttributeConfigs(entityNode, entityName, schemaEntity)

        return EntityConfig(entityName, attributeConfigs, count)
    }

    private fun findEntityInSchema(entityName: String, moduleName: String, rellSchema: RellSchema) =
        rellSchema.getEntity(moduleName, entityName)
            ?: throw ConfigurationValidationException("Entity '$moduleName:$entityName' is defined in the configuration but not found in the source code schema")

    private fun extractEntityCount(entityNode: JsonNode): Int =
        if (entityNode.has("count")) entityNode.get("count").asInt() else 10

    private fun parseAttributeConfigs(
        entityNode: JsonNode,
        entityName: String,
        schemaEntity: Entity
    ): Map<String, AttributeConfig> {
        val attributeConfigs = mutableMapOf<String, AttributeConfig>()
        val attributesNode = entityNode.get("attributes") ?: return attributeConfigs

        attributesNode.fields().forEach { (attributeName, attributeNode) ->
            val attribute = findAttributeInEntity(attributeName, entityName, schemaEntity)
            val attributeConfig = try {
                attributeConfigParser.createAttributeConfig(attributeNode, attribute)
            } catch (e: ConfigurationValidationException) {
                throw ConfigurationValidationException("Attribute '$entityName:$attributeName' ${e.message}")
            }
            attributeConfigs[attributeName] = attributeConfig
        }

        return attributeConfigs
    }

    private fun findAttributeInEntity(
        attributeName: String,
        entityName: String,
        schemaEntity: net.postchain.rell.toolbox.seeder.Entity
    ): Attribute =
        schemaEntity.attributes.find { it.name == attributeName }
            ?: throw ConfigurationValidationException(
                "Attribute '$attributeName' not found in entity '$entityName'"
            )
}
