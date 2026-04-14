/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.ValidationMessage
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.ModuleConfig
import net.postchain.rell.toolbox.seeder.config.YamlSchemaValidator
import java.nio.file.Path

class ConfigurationParser {
    companion object {
        private const val DEFAULT_CONFIG_SCHEMA_PATH = "/chromia-seeder-config-schema.json"
    }

    private val yamlSchemaValidator = YamlSchemaValidator()
    private val objectMapper = ObjectMapper(YAMLFactory())
    private val entityConfigParser = EntityConfigParser()
    private val moduleParser = ModuleConfigParser(yamlSchemaValidator, objectMapper, entityConfigParser)

    fun parseConfiguration(
        configFilePath: Path,
        rellSchema: RellSchema,
    ): Configuration {
        validateRootConfiguration(configFilePath)
        val moduleConfigs = parseModuleConfigurations(configFilePath, rellSchema)
        return Configuration(moduleConfigs)
    }

    private fun parseModuleConfigurations(
        configFilePath: Path,
        rellSchema: RellSchema
    ): Map<String, ModuleConfig> {
        val modulePaths = extractModulePaths(configFilePath)
        val moduleConfigs = mutableMapOf<String, ModuleConfig>()
        for (modulePath in modulePaths) {
            val fullModulePath = configFilePath.parent.resolve(modulePath)
            val moduleConfig = moduleParser.createModuleConfig(fullModulePath, rellSchema)
            if (moduleConfigs.containsKey(modulePath)) {
                throw ConfigurationValidationException("Duplicate module path found: $modulePath")
            }
            if (moduleConfigs.values.find { it.moduleName == moduleConfig.moduleName } != null) {
                throw ConfigurationValidationException("Duplicate module name found: ${moduleConfig.moduleName}")
            }
            moduleConfigs[modulePath] = moduleConfig
        }
        return moduleConfigs
    }

    private fun extractModulePaths(configFilePath: Path): List<String> {
        val configNode = objectMapper.readTree(configFilePath.toFile())
        val modulePaths = configNode.get("modules").map { it.asText() }
        return modulePaths
    }

    private fun validateRootConfiguration(configFilePath: Path) {
        val configSchemaPath = ResourceLoader.getResourceContent(DEFAULT_CONFIG_SCHEMA_PATH)
        val validationErrors = yamlSchemaValidator.validate(configFilePath, configSchemaPath)
        if (validationErrors.isNotEmpty()) {
            throw ConfigurationValidationException("Seeder config validation failed", validationErrors)
        }
    }
}

object ResourceLoader {
    fun getResourceContent(resourcePath: String): String {
        val resourceContent = this::class.java.getResourceAsStream(resourcePath)
            ?.bufferedReader()?.use { it.readText() }
        return resourceContent ?: throw ConfigurationValidationException("Failed to load resource: $resourcePath")
    }
}

class ConfigurationValidationException(message: String, validationErrors: Set<ValidationMessage> = emptySet()) :
    RuntimeException(message)
