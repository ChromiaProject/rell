/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern

import assertk.assertThat
import assertk.assertions.isInstanceOf
import io.github.serpro69.kfaker.Faker
import net.postchain.rell.base.model.R_BooleanType
import net.postchain.rell.base.model.R_DecimalType
import net.postchain.rell.base.model.R_IntegerType
import net.postchain.rell.base.model.R_TextType
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.generator.DataGeneratorContext
import net.postchain.rell.toolbox.seeder.generator.UniqueProducer
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PatternGeneratorsTest {
    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `all generators generate correct values`() {
        FakerGeneratorFactory.default.getRegistry().getAllGenerators().forEach { id, generator ->
            val ctx = createDataGeneratorContext(id, generator)
            val value = generator.generate(ctx)
            if (id == "random.enum") {
                assertThat(value).isInstanceOf(Int::class)
                return@forEach
            }
            when (generator.type) {
                is R_TextType -> {
                    assertThat(value).isInstanceOf(String::class)
                }
                is R_BooleanType -> {
                    assertThat(value).isInstanceOf(Boolean::class)
                }
                is R_DecimalType -> {
                    assertThat(value).isInstanceOf(Double::class)
                }
                is R_IntegerType -> {
                    assertThat(value).isInstanceOf(Long::class)
                }
                else -> {
                    fail { "generator type ${generator.type} not supported" }
                }
            }
        }
    }

    private fun createDataGeneratorContext(generatorId: String, generator: DataPatternGenerator): DataGeneratorContext {
        val schemaReader = SchemaReader()
        val moduleName = "test.all"
        val entityName = "article"
        val fieldName = "field"
        val fieldType = if (generatorId == "random.enum") "shirt_size" else generator.type.name

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                enum shirt_size {
                    S,
                    M,
                    L,
                    XL
                }
                entity $entityName {
                    $fieldName: ${fieldType};
                }
                """.trimIndent()
            )
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val entity = schema.entitiesMap["$moduleName:$entityName"]!!
        val attribute = entity.attributes[0]
        return DataGeneratorContext(attribute, entity, AttributeConfig.DataPatternConfig(generatorId), Faker(), UniqueProducer())
    }
}
