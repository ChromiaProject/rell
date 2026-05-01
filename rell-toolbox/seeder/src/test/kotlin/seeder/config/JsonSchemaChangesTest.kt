/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import org.junit.jupiter.api.Test

class JsonSchemaChangesTest {

    private val builtinGenerators = listOf("text", "byte_array", "range", "predefined")
    private val patternGenerators = FakerGeneratorFactory.default.getRegistry().getAllGenerators().keys.toList()

    private val allGenerators = builtinGenerators + patternGenerators

    @Test
    fun `check seeder module schema changes`() {
        val realSchema = getResourceAsString("/chromia-seeder-module-schema.json")
        val template = getResourceAsString("/schema/chromia-seeder-module-schema-template.json")
        val templateSchema = template.replace(
            "\"enum\": [\"<generator>\"]",
            "\"enum\": [${allGenerators.joinToString(",") { "\"$it\"" }}]"
        )
        assertThat(templateSchema).isEqualTo(realSchema)
    }

    fun getResourceAsString(path: String): String = this::class.java.getResource(path)?.readText()
        ?: throw IllegalArgumentException("Resource not found: $path")
}