package net.postchain.rell.toolbox.seeder.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import java.nio.file.Path

class YamlSchemaValidator {
    private val objectMapper = ObjectMapper(YAMLFactory())
    private val jsonObjectMapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    fun validate(yamlFilePath: Path, schemaFileContent: String): Set<ValidationMessage> {
        try {
            require(schemaFileContent.isNotEmpty()) { "Schema cannot be empty" }
            val yamlNode = loadYamlFileAsJsonNode(yamlFilePath)
            val schema = loadJsonSchema(schemaFileContent)

            return schema.validate(yamlNode)
        } catch (e: Exception) {
            throw RuntimeException("Error validating YAML against schema: ${e.message}", e)
        }
    }

    private fun loadJsonSchema(schemaFileContent: String): JsonSchema {
        val schemaNode = jsonObjectMapper.readTree(schemaFileContent)
        val schema = schemaFactory.getSchema(schemaNode)
        return schema
    }

    private fun loadYamlFileAsJsonNode(yamlFilePath: Path): JsonNode {
        val yamlFile = yamlFilePath.toFile()
        if (!yamlFile.exists()) {
            throw IllegalArgumentException("File not found: $yamlFilePath")
        }

        val yamlNode = objectMapper.readTree(yamlFile)
        return yamlNode
    }
}