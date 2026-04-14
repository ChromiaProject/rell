/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConfigParserRangeTest {

    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `parse configuration reads and validates integer range generator correctly with Rell integer type`() {
        val entityName = "product"
        val priceFieldName = "price"
        val stockFieldName = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: integer;
                    $stockFieldName: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(100)
                                max(1000)
                            }
                            attribute(stockFieldName) {
                                generator("range")
                                min(0)
                                max(50)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/range.yml"]!!
        assertThat(testModule.moduleName).isEqualTo("test.range")

        val productEntity = testModule.entityConfigs[entityName]!!
        val priceAttr = productEntity.attributes[priceFieldName] as AttributeConfig.Range
        val stockAttr = productEntity.attributes[stockFieldName] as AttributeConfig.Range

        assertThat(priceAttr.min.toInt()).isEqualTo(100)
        assertThat(priceAttr.max.toInt()).isEqualTo(1000)
        assertThat(stockAttr.min.toInt()).isEqualTo(0)
        assertThat(stockAttr.max.toInt()).isEqualTo(50)
    }

    @Test
    fun `parse configuration reads and validates integer range generator correctly with Rell big_integer type`() {
        val entityName = "product"
        val priceFieldName = "price"
        val stockFieldName = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: big_integer;
                    $stockFieldName: big_integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(100)
                                max(1000)
                            }
                            attribute(stockFieldName) {
                                generator("range")
                                min(0)
                                max(50)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/range.yml"]!!
        assertThat(testModule.moduleName).isEqualTo("test.range")

        val productEntity = testModule.entityConfigs[entityName]!!
        val priceAttr = productEntity.attributes[priceFieldName] as AttributeConfig.Range
        val stockAttr = productEntity.attributes[stockFieldName] as AttributeConfig.Range

        assertThat(priceAttr.min.toInt()).isEqualTo(100)
        assertThat(priceAttr.max.toInt()).isEqualTo(1000)
        assertThat(stockAttr.min.toInt()).isEqualTo(0)
        assertThat(stockAttr.max.toInt()).isEqualTo(50)
    }

    @Test
    fun `parse configuration reads and validates integer range generator correctly with Rell decimal type`() {
        val entityName = "product"
        val priceFieldName = "price"
        val stockFieldName = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: decimal;
                    $stockFieldName: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(100)
                                max(1000)
                            }
                            attribute(stockFieldName) {
                                generator("range")
                                min(0)
                                max(50)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/range.yml"]!!
        assertThat(testModule.moduleName).isEqualTo("test.range")

        val productEntity = testModule.entityConfigs[entityName]!!
        val priceAttr = productEntity.attributes[priceFieldName] as AttributeConfig.Range
        val stockAttr = productEntity.attributes[stockFieldName] as AttributeConfig.Range

        assertThat(priceAttr.min.toInt()).isEqualTo(100)
        assertThat(priceAttr.max.toInt()).isEqualTo(1000)
        assertThat(stockAttr.min.toInt()).isEqualTo(0)
        assertThat(stockAttr.max.toInt()).isEqualTo(50)
    }

    @Test
    fun `parse configuration reads and validates integer range generator correctly with Rell rowid type`() {
        val entityName = "product"
        val priceFieldName = "price"
        val stockFieldName = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: rowid;
                    $stockFieldName: rowid;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(100)
                                max(1000)
                            }
                            attribute(stockFieldName) {
                                generator("range")
                                min(1)
                                max(50)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/range.yml"]!!
        assertThat(testModule.moduleName).isEqualTo("test.range")

        val productEntity = testModule.entityConfigs[entityName]!!
        val priceAttr = productEntity.attributes[priceFieldName] as AttributeConfig.Range
        val stockAttr = productEntity.attributes[stockFieldName] as AttributeConfig.Range

        assertThat(priceAttr.min.toInt()).isEqualTo(100)
        assertThat(priceAttr.max.toInt()).isEqualTo(1000)
        assertThat(stockAttr.min.toInt()).isEqualTo(1)
        assertThat(stockAttr.max.toInt()).isEqualTo(50)
    }

    @Test
    fun `parse configuration with range generator throws on rowid type when min value is less than min row id`() {
        val entityName = "product"
        val priceFieldName = "price"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: rowid;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(0)
                                max(100)
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
            "Invalid seeder configuration for module 'test.range'. Attribute 'product:price' range generator is not in range [1, 9223372036854775807]"
        )
    }

    @Test
    fun `parse configuration reads and throws on integer range generator when min value is greater than max value`() {
        val entityName = "product"
        val priceFieldName = "price"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(1000)
                                max(100)
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
            "Invalid seeder configuration for module 'test.range'. Attribute 'product:price' min value must be less than or equal to max value. min: 1000, max: 100"
        )
    }

    @Test
    fun `parse configuration reads and throws on integer range generator when min or max is set with decimal`() {
        val entityName = "product"
        val priceFieldName = "price"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $priceFieldName: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(2.1)
                                max(2.5)
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

        assertThat(res.message!!).contains("product.attributes.price.min: must be multiple of 1")
    }

    @Test
    fun `parse configuration throws on integer range generator used on non-number Rell type`() {
        val entityName = "product"
        val stockFieldName = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $stockFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(stockFieldName) {
                                generator("range")
                                min(50)
                                max(0)
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
            "Invalid seeder configuration for module 'test.range'. Attribute 'product:stock' type is 'text' but generator is configured as a numeric range type"
        )
    }

    @Test
    fun `parse configuration range with uneven decimal`() {
        val entityName = "product"
        val stockPrice = "stock"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/range",
                """
                module;
                entity $entityName {
                    $stockPrice: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.range") {
                    entity(entityName) {
                        attributes {
                            attribute(stockPrice) {
                                generator("range")
                                min(-1.1)
                                max(2.3)
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
        assertThat(
            res.message!!.contains(
                "\$.product.attributes.stock.min: must be multiple of 1, \$.product.attributes.stock.max: must be multiple of 1"
            )
        ).isTrue()
    }
}
