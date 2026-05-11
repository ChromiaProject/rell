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

class ConfigParserTextTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()

    @TempDir
    private lateinit var tempDir: Path

    /* Other possible cases worth adding:
     * Add test for when min/max is set with a lower/higher number than what is supported
     *  - Needs different setup with writing to config yml file directly to avoid overflow
     */

    @Test
    fun `parse configuration reads text generator with min and max length values correctly for Rell text type`() {
        val entityName = "article"
        val titleFieldName = "title"
        val contentFieldName = "content"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/text",
                """
                module;
                entity $entityName {
                    $titleFieldName: text;
                    $contentFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.text") {
                    entity("article") {
                        attributes {
                            attribute(titleFieldName) {
                                generator("text")
                                min(10)
                                max(100)
                            }
                            attribute(contentFieldName) {
                                generator("text")
                                min(100)
                                max(1000)
                            }
                        }
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/text.yml"]!!
        val articleEntity = testModule.entityConfigs[entityName]!!
        val titleAttr = articleEntity.attributes[titleFieldName] as AttributeConfig.TextConfig
        val contentAttr = articleEntity.attributes[contentFieldName] as AttributeConfig.TextConfig

        assertThat(titleAttr.min).isEqualTo(10)
        assertThat(titleAttr.max).isEqualTo(100)
        assertThat(contentAttr.min).isEqualTo(100)
        assertThat(contentAttr.max).isEqualTo(1000)
    }

    @Test
    fun `should throw when text generator with min and max length is out of range`() {
        val entityName = "article"
        val titleFieldName = "title"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/text",
                """
                module;
                entity $entityName {
                    $titleFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.text") {
                    entity("article") {
                        attributes {
                            attribute(titleFieldName) {
                                generator("text")
                                min(0)
                                max(100)
                            }
                        }
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val exception = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }
        assertThat(exception.message!!).contains("\$.article.attributes.title.min: must have a minimum value of 1")
    }

    @Test
    fun `parse configuration reads text generator with min and max length values correctly for Rell byte_array type`() {
        val entityName = "article"
        val titleFieldName = "title"
        val contentFieldName = "content"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/text",
                """
                module;
                entity $entityName {
                    $titleFieldName: byte_array;
                    $contentFieldName: byte_array;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.text") {
                    entity("article") {
                        attributes {
                            attribute(titleFieldName) {
                                generator("text")
                                min(10)
                                max(100)
                            }
                            attribute(contentFieldName) {
                                generator("text")
                                min(100)
                                max(1000)
                            }
                        }
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/text.yml"]!!
        val articleEntity = testModule.entityConfigs[entityName]!!
        val titleAttr = articleEntity.attributes[titleFieldName] as AttributeConfig.TextConfig
        val contentAttr = articleEntity.attributes[contentFieldName] as AttributeConfig.TextConfig

        assertThat(titleAttr.min).isEqualTo(10)
        assertThat(titleAttr.max).isEqualTo(100)
        assertThat(contentAttr.min).isEqualTo(100)
        assertThat(contentAttr.max).isEqualTo(1000)
    }

    @Test
    fun `parse configuration reads text generator without min and max length values correctly`() {
        val entityName = "article"
        val titleFieldName = "title"
        val contentFieldName = "content"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/text",
                """
                module;
                entity $entityName {
                    $titleFieldName: text;
                    $contentFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.text") {
                    entity("article") {
                        attributes {
                            attribute(titleFieldName) {
                                generator("text")
                            }
                            attribute(contentFieldName) {
                                generator("text")
                            }
                        }
                    }
                }
            }
        }


        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/text.yml"]!!
        val articleEntity = testModule.entityConfigs[entityName]!!
        val titleAttr = articleEntity.attributes[titleFieldName] as AttributeConfig.TextConfig
        val contentAttr = articleEntity.attributes[contentFieldName] as AttributeConfig.TextConfig

        assertThat(titleAttr.min).isEqualTo(null)
        assertThat(titleAttr.max).isEqualTo(null)
        assertThat(contentAttr.min).isEqualTo(null)
        assertThat(contentAttr.max).isEqualTo(null)
    }

    @Test
    fun `parse configuration throws with text generator on non-text type`() {
        val entityName = "article"
        val titleFieldName = "title"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/text",
                """
                module;
                entity $entityName {
                    $titleFieldName: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.text") {
                    entity("article") {
                        attributes {
                            attribute(titleFieldName) {
                                generator("text")
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
            "Invalid seeder configuration for module 'test.text'. Attribute 'article:title' type is 'integer' but generator is as configured as text type"
        )
    }
}
