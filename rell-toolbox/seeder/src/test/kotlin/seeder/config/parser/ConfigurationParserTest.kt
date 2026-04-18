/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import assertk.assertThat
import assertk.assertions.*
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ConfigurationParserTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `parseConfiguration reads and validates configuration successfully`() {
        val configFilePath = configFile(tempDir) {
            configuration {
                module("game.assets") {
                    entity("location") {
                        count(203)
                        attributes {
                            attribute("name") {
                                generator("text")
                                min(5)
                                max(20)
                            }
                            attribute("description") {
                                generator("text")
                            }
                        }
                    }
                }

                module("team") {
                    entity("position") {
                        count(1)
                        attributes {
                            attribute("rank") {
                                generator("range")
                                min(50)
                                max(100)
                            }
                        }
                    }
                }
            }
        }

        val testDataBuilder = testData(tempDir) {
            addModule(
                "game/assets",
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
            addModule(
                "team",
                """
                module;
                entity position {
                    rank: integer;
                }
                """.trimIndent()
            )
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
        val config = configParser.parseConfiguration(configFilePath, schema)

        assertThat(config.modules.keys).containsExactlyInAnyOrder("modules/game/assets.yml", "modules/team.yml")

        val gameAssetModule = config.modules["modules/game/assets.yml"]!!
        assertThat(gameAssetModule.moduleName).isEqualTo("game.assets")
        assertThat(gameAssetModule.entityConfigs.keys).containsOnly("location")

        val locationEntity = gameAssetModule.entityConfigs["location"]!!
        assertThat(locationEntity.name).isEqualTo("location")
        assertThat(locationEntity.count).isEqualTo(203)

        val locationAttributes = locationEntity.attributes
        assertThat(locationAttributes.keys).containsExactlyInAnyOrder("name", "description")

        val nameConfig = locationAttributes["name"] as AttributeConfig.TextConfig
        assertThat(nameConfig.min).isEqualTo(5)
        assertThat(nameConfig.max).isEqualTo(20)

        val descriptionConfig = locationAttributes["description"] as AttributeConfig.TextConfig
        assertThat(descriptionConfig.min).isNull()
        assertThat(descriptionConfig.max).isNull()

        val team = config.modules["modules/team.yml"]!!
        assertThat(team.moduleName).isEqualTo("team")
        assertThat(team.entityConfigs.keys).containsOnly("position")

        val positionEntity = team.entityConfigs["position"]!!
        assertThat(positionEntity.name).isEqualTo("position")
        assertThat(positionEntity.count).isEqualTo(1)

        val positionAttributes = positionEntity.attributes
        assertThat(positionAttributes.keys).containsOnly("rank")

        val bastConfig = positionAttributes["rank"] as AttributeConfig.Range
        assertThat(bastConfig.min.toInt()).isEqualTo(50)
        assertThat(bastConfig.max.toInt()).isEqualTo(100)
    }

    @Test
    fun `parseConfiguration throws exception when attribute does not exist in schema`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/invalid",
                """
                module;
                entity user {
                    name: text;
                    email: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.invalid") {
                    entity("user") {
                        attributes {
                            attribute("non_existent_attr") {
                                generator("text")
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
        assertThat(
            exception.message
        ).isEqualTo(
            "Invalid seeder configuration for module 'test.invalid'. Attribute 'non_existent_attr' not found in entity 'user'"
        )
    }

    @Test
    fun `parseConfiguration throws exception when entity does not exist in schema`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/invalid",
                """
                module;
                entity user {
                    name: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.invalid") {
                    entity("non_existent_entity") {
                        attributes {
                            attribute("name") {
                                generator("text")
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
        assertThat(
            exception.message
        ).isEqualTo(
            "Invalid seeder configuration for module 'test.invalid'. Entity 'test.invalid:non_existent_entity' is defined in the configuration but not found in the source code schema"
        )
    }

    @Test
    fun `parseConfiguration throws exception when module does not exist in schema`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/valid",
                """
                module;
                entity user {
                    name: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("non.existent.module") {
                    entity("user") {
                        attributes {
                            attribute("name") {
                                generator("text")
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
        assertThat(
            exception.message
        ).isEqualTo(
            "Invalid seeder configuration for module 'non.existent.module'. Entity 'non.existent.module:user' is defined in the configuration but not found in the source code schema"
        )
    }

    @Test
    fun `parseConfiguration throws exception when configuration does not exist`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/valid",
                """
                module;
                entity user {
                    name: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = tempDir.resolve("non-existent.yml")

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val exception = assertThrows<RuntimeException> {
            configParser.parseConfiguration(configFilePath, schema)
        }
        assertThat(exception.message).isEqualTo(
            "Error validating YAML against schema: File not found: ${configFilePath.toAbsolutePath()}"
        )
    }

    @Test
    fun `parseConfiguration skips relation attributes`() {
        val entityName = "order"
        val fieldName = "id"
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/relations",
                """
                module;
                entity customer {
                    id: integer;
                    name: text;
                }

                entity $entityName {
                    $fieldName: text;
                    customer: customer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.relations") {
                    entity("order") {
                        attributes {
                            attribute("id") {
                                generator("text")
                            }
                            // Note: we don't define customer relation here
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/relations.yml"]!!
        val orderEntity = testModule.entityConfigs[entityName]!!

        assertThat(testModule.entityConfigs.size).isEqualTo(1)
        assertThat(orderEntity.attributes.keys).containsOnly(fieldName)
    }

    @Test
    fun `parseConfiguration throws when relation attribute is configured with non-supported generator`() {
        val entityName = "order"
        val refEntityName = "customer"
        val fieldName = "id"
        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/relations",
                """
                module;
                entity $refEntityName {
                    id: integer;
                    name: text;
                }

                entity $entityName {
                    $fieldName: text;
                    $refEntityName;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.relations") {
                    entity("order") {
                        attributes {
                            attribute("id") {
                                generator("text")
                            }
                            attribute(refEntityName) {
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
            "Invalid seeder configuration for module 'test.relations'. Attribute 'order:customer' type is 'test.relations:customer' but generator is as configured as text type"
        )
    }

    @Test
    fun `parseConfiguration throws exception for invalid seeder config`(@TempDir tempDir: Path) {
        val invalidConfigPath = tempDir.resolve("invalid-seeder.yml")
        invalidConfigPath.writeText(
            """
            # Missing required fields
            some_field: some_value
            """.trimIndent()
        )

        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
        }
        val exception = assertThrows(ConfigurationValidationException::class.java) {
            val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
            configParser.parseConfiguration(invalidConfigPath, schema)
        }

        assertTrue(exception.message!!.contains("Seeder config validation failed"))
    }

    @Test
    fun `parseConfiguration throws exception for invalid module config`(@TempDir tempDir: Path) {
        val seederConfigPath = tempDir.resolve("invalid-seeder.yml")
        seederConfigPath.writeText(
            """
            modules:
              - modules/main.yml
            """.trimIndent()
        )
        val invalidModuleConfigPath = tempDir.resolve("modules/main.yml").also { it.toFile().parentFile.mkdirs() }
        invalidModuleConfigPath.writeText(
            """
            # No module name
            location:
            """.trimIndent()
        )
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
        }
        val exception = assertThrows(ConfigurationValidationException::class.java) {
            val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
            configParser.parseConfiguration(seederConfigPath, schema)
        }

        assertThat(exception.message!!).contains("required property 'module' not found")
    }

    @Test
    fun `parseConfiguration handles empty modules list`(@TempDir tempDir: Path) {
        val emptyModulesConfigPath = tempDir.resolve("empty-modules.yml")
        emptyModulesConfigPath.writeText(
            """
            modules: []
            """.trimIndent()
        )
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
        }
        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
        val config = configParser.parseConfiguration(emptyModulesConfigPath, schema)

        assertThat(config.modules).isEmpty()
    }

    @Test
    fun `handling duplication of module name in different module configurations`() {
        val configFilePath = configFile(tempDir) {
            configuration {
                module("game.assets", "assets1") {}
                module("game.assets", "assets2") {}
            }
        }

        val testDataBuilder = testData(tempDir) {
            addModule(
                "game/assets",
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
        }

        val exception = assertThrows(ConfigurationValidationException::class.java) {
            val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(exception.message!!).contains("Duplicate module name found: game.assets")
    }

    @Test
    fun `handling duplication of module path in seeder configuration`() {
        val configFilePath = tempDir.resolve("duplicate-module-paths.yml")
        configFilePath.writeText(
            """
            modules:
              - modules/game/assets.yml
              - modules/game/assets.yml
            """.trimIndent()
        )

        val modulePath = tempDir.resolve("modules/game/assets.yml").also { it.parent.toFile().mkdirs() }
        modulePath.writeText(
            """
            module: game.assets
            """.trimIndent()
        )

        val testDataBuilder = testData(tempDir) {
            addModule(
                "game/assets",
                """
                module;
                entity location {
                    name: text;
                    description: text;
                }
                """.trimIndent()
            )
        }

        val exception = assertThrows(ConfigurationValidationException::class.java) {
            val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
            val x = configParser.parseConfiguration(configFilePath, schema)
            x
        }

        assertThat(exception.message!!).contains("Duplicate module path found: modules/game/assets.yml")
    }

    @Test
    fun `attribute types correctly maps with generators`() {
        checkGeneratorTypes(type = "text", generator = "text", true)
        checkGeneratorTypes(type = "text", generator = "range", false)

        checkGeneratorTypes(type = "integer", generator = "range", true)
        checkGeneratorTypes(type = "integer", generator = "text", false)

        checkGeneratorTypes(type = "decimal", generator = "range", true)
        checkGeneratorTypes(type = "decimal", generator = "text", false)

        checkGeneratorTypes(type = "big_integer", generator = "range", true)
        checkGeneratorTypes(type = "big_integer", generator = "text", false)

        checkGeneratorTypes(type = "byte_array", generator = "text", true)
        checkGeneratorTypes(type = "byte_array", generator = "range", false)

        checkGeneratorTypes(type = "rowid", generator = "range", true)
        checkGeneratorTypes(type = "rowid", generator = "text", false)

        checkGeneratorTypes(type = "boolean", generator = "random.boolean", true)
        checkGeneratorTypes(type = "boolean", generator = "text", false)

        checkGeneratorTypes(type = "json", generator = "random.json", true)
        checkGeneratorTypes(type = "json", generator = "bank.name", false)
        checkGeneratorTypes(type = "json", generator = "predefined", true)
        checkGeneratorTypes(type = "json", generator = "text", false)
    }

    private var generatorTestCaseCounter = 0
    private fun checkGeneratorTypes(type: String, generator: String, success: Boolean) {
        generatorTestCaseCounter++
        val testCaseDir = tempDir.resolve("test_case_$generatorTestCaseCounter").also { it.toFile().mkdirs() }
        val configFilePath = configFile(testCaseDir) {
            configuration {
                module("game.assets") {
                    entity("player") {
                        count(44)
                        attributes {
                            attribute("name") {
                                generator(generator)
                                if (generator == "range") {
                                    min(5)
                                    max(20)
                                }
                                if (generator == "predefined") {
                                    values(listOf("[]"))
                                }
                            }
                        }
                    }
                }
            }
        }

        val testDataBuilder = testData(testCaseDir) {
            addModule(
                "game/assets",
                """
                module;
                entity player {
                    name: $type;
                }
                """.trimIndent()
            )
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
        if (success) {
            val config = configParser.parseConfiguration(configFilePath, schema)
            val playerEntity = config.modules["modules/game/assets.yml"]!!.entityConfigs["player"]!!
            val nameConfig = playerEntity.attributes["name"]
            assertThat(nameConfig).isNotNull()
        } else {
            assertThrows(ConfigurationValidationException::class.java) {
                configParser.parseConfiguration(configFilePath, schema)
            }
        }
    }

    @Test
    fun `predefined value types check`() {
        val configFilePath = configFile(tempDir) {
            configuration {
                module("game.assets") {
                    entity("player") {
                        count(5)
                        attributes {
                            attribute("name") {
                                generator("predefined")
                                values(listOf("Alice", 5, false))
                            }
                        }
                    }
                }
            }
        }

        val testDataBuilder = testData(tempDir) {
            addModule(
                "game/assets",
                """
                module;
                entity player {
                    name: text;
                }
                """.trimIndent()
            )
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder, null)
        val result = assertThrows<ConfigurationValidationException> {
            configParser.parseConfiguration(configFilePath, schema)
        }

        assertThat(
            result.message
        ).isEqualTo("Invalid seeder configuration for module 'game.assets'. Attribute 'player:name' predefined values must be strings, but found: NUMBER")
    }
}
