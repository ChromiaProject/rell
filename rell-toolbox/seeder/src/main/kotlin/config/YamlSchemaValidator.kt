/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config

import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class YamlSchemaValidator {
    // Rell big_integer/decimal values are arbitrary-precision, so a predefined value may be far longer than
    // Jackson's default 1000-digit cap. Lift the limit on the mappers the schema registry uses to parse input.
    private val relaxedConstraints = StreamReadConstraints.builder().maxNumberLength(Int.MAX_VALUE).build()

    private val jsonMapper: ObjectMapper =
        JsonMapper.builder(JsonFactory.builder().streamReadConstraints(relaxedConstraints).build()).build()

    private val yamlMapper: ObjectMapper =
        YAMLMapper.builder(YAMLFactory.builder().streamReadConstraints(relaxedConstraints).build()).build()

    private val schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
        builder.nodeReader { reader -> reader.jsonMapper(jsonMapper).yamlMapper(yamlMapper) }
    }

    fun validate(yamlFilePath: Path, schemaFileContent: String): List<Error> {
        try {
            require(schemaFileContent.isNotEmpty()) { "Schema cannot be empty" }
            require(yamlFilePath.exists()) { "File not found: $yamlFilePath" }

            val schema = schemaRegistry.getSchema(schemaFileContent, InputFormat.JSON)
            return schema.validate(yamlFilePath.readText(), InputFormat.YAML)
        } catch (e: Exception) {
            throw RuntimeException("Error validating YAML against schema: ${e.message}", e)
        }
    }
}
