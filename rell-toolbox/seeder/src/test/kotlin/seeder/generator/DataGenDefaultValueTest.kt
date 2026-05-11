/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path

class DataGenDefaultValueTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.predefined"

    @TempDir
    private lateinit var tempDir: Path

    companion object {
        private val objectMapper = ObjectMapper()
    }

    @Test
    fun `default values for integer type should be within expected range`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: integer;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Long }

        assertThat(fieldData).each {
            it.isInstanceOf<Long>()
                .isBetween(
                    PrimitiveValueGenerator.NUMBER_MIN_VALUE.toLong(),
                    PrimitiveValueGenerator.NUMBER_MAX_VALUE.toLong()
                )
        }
    }

    @Test
    fun `default values for big integer type should be within expected range`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: big_integer;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as BigInteger }

        assertThat(fieldData).each {
            it.isInstanceOf<BigInteger>()
                .prop(BigInteger::toLong)
                .isBetween(
                    PrimitiveValueGenerator.NUMBER_MIN_VALUE.toLong(),
                    PrimitiveValueGenerator.NUMBER_MAX_VALUE.toLong(),
                )
        }
    }

    @Test
    fun `default values for decimal type should be within expected range`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: decimal;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as BigDecimal }

        assertThat(fieldData).each {
            it.isInstanceOf<BigDecimal>()
                .prop(BigDecimal::toDouble)
                .isBetween(
                    PrimitiveValueGenerator.NUMBER_MIN_VALUE.toDouble(),
                    PrimitiveValueGenerator.NUMBER_MAX_VALUE.toDouble(),
                )
        }
    }

    @Test
    fun `default values for boolean type should be either true or false`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: boolean;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Boolean }

        assertThat(fieldData).each { field ->
            field.isInstanceOf<Boolean>()
        }
    }

    @Test
    fun `default values for text type should be within default length range`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: text;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).each { field ->
            field.isInstanceOf<String>().prop(String::length)
                .isBetween(DataGenerator.TEXT_MIN_DEFAULT, DataGenerator.TEXT_MAX_DEFAULT)
        }
    }

    @Test
    fun `default values for byte array type should have expected length`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as WrappedByteArray }

        assertThat(fieldData).each {
            it.isInstanceOf<WrappedByteArray>()
                .prop(WrappedByteArray::size)
                .isBetween(
                    PrimitiveValueGenerator.BYTE_ARRAY_MIN_SIZE,
                    PrimitiveValueGenerator.BYTE_ARRAY_MAX_SIZE
                )
        }
    }

    @Test
    fun `default values for rowid type should be within expected range`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: rowid;
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

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Long }

        assertThat(fieldData).each {
            it.isInstanceOf<Long>()
                .isBetween(
                    PrimitiveValueGenerator.ROWID_MIN_VALUE.toLong(),
                    PrimitiveValueGenerator.ROWID_MAX_VALUE.toLong()
                )
        }
    }

    @Test
    fun `should handle both default values and defined ones correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val anotherFieldName = "another_field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $fieldName: integer;
                    $anotherFieldName: text;
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
                            attribute(anotherFieldName) {
                                generator("text")
                                min(1)
                                max(3)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Long }
        val anotherFieldData = entityData.map { it.fields[anotherFieldName]!!.value as String }

        assertThat(fieldData).each {
            it.isInstanceOf<Long>()
                .isBetween(
                    PrimitiveValueGenerator.NUMBER_MIN_VALUE.toLong(),
                    PrimitiveValueGenerator.NUMBER_MAX_VALUE.toLong()
                )
        }
        assertThat(anotherFieldData).each {
            it.isInstanceOf<String>()
                .prop(String::length)
                .isBetween(
                    1,
                    3
                )
        }
    }

    @Test
    fun `should generate default unique & valid json when the attribute is key`() {
        val entityName = "entity_name"
        val fieldName = "field_name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    key $fieldName: json;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(10_000)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).each {
            it.transform { it.isValidJson() }.isEqualTo(true)
        }
        assertThat(fieldData).size().isEqualTo(fieldData.distinct().size)
    }

    private fun String.isValidJson(): Boolean = runCatching {
        objectMapper.readTree(this)
        true
    }.getOrDefault(false)
}
