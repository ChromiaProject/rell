/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConfigParserPredefinedTest {

    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()

    @TempDir
    private lateinit var tempDir: Path


    @Test
    fun `parseConfiguration handles enum attributes with named generator`() {
        val entityName = "product"
        val fieldName = "status"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/enums",
                """
                module;
                enum product_status { ACTIVE, INACTIVE, SOLD_OUT }

                entity $entityName {
                    name: text;
                    $fieldName: product_status;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.enums") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(listOf("ACTIVE", "INACTIVE", "SOLD_OUT"))
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/enums.yml"]!!
        val productEntity = testModule.entityConfigs["product"]!!
        val statusAttr = productEntity.attributes["status"] as AttributeConfig.PredefinedValues

        assertThat(statusAttr.values).isEqualTo(listOf("ACTIVE", "INACTIVE", "SOLD_OUT"))
    }

    @Test
    fun `parseConfiguration throws when predefined values don't match Rell enum values`() {
        val entityName = "product"
        val fieldName = "status"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/enums",
                """
                module;
                enum product_status { ACTIVE, INACTIVE, SOLD_OUT }

                entity $entityName {
                    name: text;
                    $fieldName: product_status;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.enums") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(listOf("value_dont_exist"))
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val res = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }
        assertThat(res.message).isEqualTo(
            "Invalid seeder configuration for module 'test.enums'. Attribute 'product:status' enum value 'value_dont_exist' is not in enum set [ACTIVE, INACTIVE, SOLD_OUT]"
        )
    }

    @Test
    fun `parseConfiguration predefined handles boolean values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(false, true)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }


    @Test
    fun `parseConfiguration predefined handles integer values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(10, 20, 30)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }


    // TODO: Verify that this is proper configuration for big_integer when we know Rell code format
    @Test
    fun `parseConfiguration predefined handles big_integer values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(AttributeConfigParser.BIG_INTEGER_MAX_VALUE)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }

    @Test
    fun `parseConfiguration predefined handles decimal values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(Double.MIN_VALUE, Double.MAX_VALUE)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }

    @Test
    fun `parseConfiguration predefined handles decimal values correctly when defined as float`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(Float.MAX_VALUE, Float.MIN_VALUE)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        // Need to caste the fieldAttr to float, doing other way around changes precision
        for (value in fieldAttr.values) {
            val casted = (value as Double).toFloat()
            assertThat(predefinedValues).contains(casted)
        }
    }

    @Test
    fun `parseConfiguration predefined handles text values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf("abc", "def")

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }

    // TODO: Make sure to write documentation on how to write proper json. Or add custom validator for it.
    @Test
    fun `parseConfiguration predefined handles json values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf("{}", "{a: 1}", "{'b': 3}", "[a, 4]", "['a', 5]")

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
                """
                module;

                entity $entityName {
                    $fieldName: json;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val expectedValues = listOf(
            "{}",
            "{\"a\":1}",
            "{\"b\":3}",
            "[\"a\",4]",
            "[\"a\",5]"
        )

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(expectedValues)
    }

    // TODO: Is this the way we want to define hex string? Or do we want to do it with the style: "x'adad...'"
    //  If we are able to define it in such a way that it gets parsed as hex directly, it would be nice. Then we do
    //  not need to handle it as text inside parsePredefinedValuesConfig
    @Test
    fun `parseConfiguration predefined handles byte_array values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(
            "023078E17989BF3C5D152FC18E84DAADACB4EA4D298822C45AF0A6A8F9FFEE5557",
            "F94AAAE80143FFF2FABFD45F1C1B20A22AAF41622D1F05FE2B0FC14345748958"
        )

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }

    @Test
    fun `parseConfiguration predefined should throw when uneven value for byte_array`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf("12a", "45a")

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val res = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(res.message).isEqualTo(
            "Invalid seeder configuration for module 'test.predefined'. Attribute 'entity_name:field_name' invalid hexadecimal value. Length must be even, but found 3"
        )
    }

    @Test
    fun `parseConfiguration predefined should throw when unsupported character value for byte_array`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(
            "G23078E17989BF3C5D152FC18E84DAADACB4EA4D298822C45AF0A6A8F9FFEE5557",
            "G94AAAE80143FFF2FABFD45F1C1B20A22AAF41622D1F05FE2B0FC14345748958"
        )

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val res = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(res.message).isEqualTo(
            "Invalid seeder configuration for module 'test.predefined'. Attribute 'entity_name:field_name' invalid hexadecimal value. Only characters 0-9, A-F, and a-f are allowed, but found 'G23078E17989BF3C5D152FC18E84DAADACB4EA4D298822C45AF0A6A8F9FFEE5557'"
        )
    }

    @Test
    fun `parseConfiguration predefined handles row_id values correctly`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(1, 2)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/predefined.yml"]!!
        val entityConfig = testModule.entityConfigs[entityName]!!
        val fieldAttr = entityConfig.attributes[fieldName] as AttributeConfig.PredefinedValues

        assertThat(fieldAttr.values).isEqualTo(predefinedValues)
    }

    @Test
    fun `parseConfiguration predefined handles throws when negative value is used for row_id`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(-1, 2)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
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
                module("test.predefined") {
                    entity(entityName) {
                        attributes {
                            attribute(fieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val res = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(res.message).isEqualTo("Invalid seeder configuration for module 'test.predefined'. Attribute 'entity_name:field_name' predefined values must be greater or equal to 0 value was '-1'")
    }

    @Test
    fun `parseConfiguration predefined should throw if types value type is not compatible with entity field type`() {
        val entityName = "product"
        val categoryFieldName = "category"
        val predefinedValues = listOf(1, 2, 3)

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/predefined",
                """
                module;
                entity $entityName {
                    $categoryFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.predefined") {
                    entity(entityName) {
                        count(6)
                        attributes {
                            attribute(categoryFieldName) {
                                generator("predefined")
                                values(predefinedValues)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val res = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(res.message).isEqualTo(
            "Invalid seeder configuration for module 'test.predefined'. Attribute 'product:category' predefined values must be strings, but found: NUMBER"
        )
    }

}
