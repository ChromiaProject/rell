/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.rell.toolbox.seeder.config.EntityConfig
import net.postchain.rell.toolbox.seeder.config.ModuleConfig
import net.postchain.rell.toolbox.seeder.config.YamlSchemaValidator
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import java.nio.file.Path

class ModuleConfigParser(
    private val yamlSchemaValidator: YamlSchemaValidator,
    private val objectMapper: ObjectMapper,
    private val entityConfigParser: EntityConfigParser
) {
    companion object {
        private const val DEFAULT_MODULE_SCHEMA_PATH = "/chromia-seeder-module-schema.json"
    }

    private val moduleSchemaPath = ResourceLoader.getResourceContent(DEFAULT_MODULE_SCHEMA_PATH)

    fun createModuleConfig(moduleFilePath: Path, rellSchema: RellSchema): ModuleConfig {
        validateModuleConfig(moduleFilePath, moduleSchemaPath)
        return parseModuleConfig(moduleFilePath, rellSchema)
    }

    private fun parseModuleConfig(
        moduleFilePath: Path,
        rellSchema: RellSchema
    ): ModuleConfig {
        val entityNode = objectMapper.readTree(moduleFilePath.toFile())
        val moduleName = entityNode.get("module").asText()
        val entityConfigs = try {
            parseEntityConfigs(entityNode, moduleName, rellSchema)
        } catch (e: Exception) {
            throw ConfigurationValidationException("Invalid seeder configuration for module '$moduleName'. ${e.message}")
        }
        return ModuleConfig(moduleName, entityConfigs)
    }

    private fun parseEntityConfigs(
        entityNode: JsonNode,
        moduleName: String,
        rellSchema: RellSchema
    ): MutableMap<String, EntityConfig> {
        val entityConfigs = mutableMapOf<String, EntityConfig>()
        for ((fieldName, fieldObject) in entityNode.properties()) {
            if (fieldName != "module") {
                val entityConfig = entityConfigParser.createEntityNode(fieldName, fieldObject, moduleName, rellSchema)
                entityConfigs[fieldName] = entityConfig
            }
        }
        return entityConfigs
    }

    private fun validateModuleConfig(moduleFilePath: Path, moduleSchemaContent: String) {
        val validationErrors = yamlSchemaValidator.validate(moduleFilePath, moduleSchemaContent)
        if (validationErrors.isNotEmpty()) {
            throw ConfigurationValidationException(
                "Module config validation failed for $moduleFilePath: ${validationErrors.joinToString(", ")}",
                validationErrors
            )
        }
    }
}
