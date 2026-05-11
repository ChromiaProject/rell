/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PrimitiveValueGenTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.primitive"

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `Rell byte_array type results to byte array generator as default with size 32`() {
        val entityName = "article"
        val fieldName = "title"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: byte_array;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(10)
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val articleEntities = result.entityData["$moduleName:$entityName"]

        val fieldData = articleEntities!!.map { it.fields[fieldName]!!.value }

        assertThat(fieldData).each { data ->
            data.isInstanceOf(WrappedByteArray::class)
                .prop(WrappedByteArray::size)
                .isEqualTo(32)
        }
    }

    @Test
    fun `Configured rell byte_array with size results to byte array with configured size`() {
        val entityName = "article"
        val fieldName = "title"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: byte_array;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(10)
                        attributes {
                            attribute(fieldName) {
                                generator("byte_array")
                                size(10)
                            }
                        }
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val articleEntities = result.entityData["$moduleName:$entityName"]

        val fieldData = articleEntities!!.map { it.fields[fieldName]!!.value }

        assertThat(fieldData).each { data ->
            data.isInstanceOf(WrappedByteArray::class)
                .prop(WrappedByteArray::size)
                .isEqualTo(10)
        }
    }
}
