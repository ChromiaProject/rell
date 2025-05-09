package net.postchain.rell.toolbox.seeder.config.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConfigParserNamedTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `named generator validates predefined values correctly`() {
        val entityName = "user"
        val userEmailField = "name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/named",
                """
                module;
                entity $entityName {
                    $userEmailField: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.named") {
                    entity(entityName) {
                        attributes {
                            attribute(userEmailField) {
                                generator("email")
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/named.yml"]!!
        val userEntity = testModule.entityConfigs[entityName]!!
        val emailAttr = userEntity.attributes[userEmailField] as AttributeConfig.DataPatternConfig
        assertThat(emailAttr.pattern).isEqualTo("email")
    }

    @Test
    fun `named generator throws when named generator type doesn't translate into Rell type`() {
        val entityName = "user"
        val userEmailField = "name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/named",
                """
                module;
                entity $entityName {
                    $userEmailField: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.named") {
                    entity(entityName) {
                        attributes {
                            attribute(userEmailField) {
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
        assertThat(
            res.message
        ).isEqualTo("Invalid seeder configuration for module 'test.named'. Attribute 'user:name' type is 'integer' but generator is as configured as text type")
    }

    @Test
    fun `patternGenerator should validate compatible types correctly`() {
        val entityName = "user"
        val points = "points"
        val salary = "salary"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/named",
                """
                module;
                entity $entityName {
                    $points: big_integer;
                    $salary: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.named") {
                    entity(entityName) {
                        attributes {
                            attribute(points) {
                                generator("random.integer")
                            }
                            attribute(salary) {
                                generator("random.integer")
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val testModule = config.modules["modules/test/named.yml"]!!
        val userEntity = testModule.entityConfigs[entityName]!!
        val pointsAttr = userEntity.attributes[points] as AttributeConfig.DataPatternConfig
        assertThat(pointsAttr.pattern).isEqualTo("random.integer")
        val salaryAttr = userEntity.attributes[salary] as AttributeConfig.DataPatternConfig
        assertThat(salaryAttr.pattern).isEqualTo("random.integer")
    }

    @Test
    fun `patternGenerator should throw if types are incompatible`() {
        val entityName = "user"
        val userEmailField = "name"

        val testDataBuilder = testData(tempDir) {
            addModule(
                "test/named",
                """
                module;
                entity $entityName {
                    $userEmailField: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module("test.named") {
                    entity(entityName) {
                        attributes {
                            attribute(userEmailField) {
                                generator("random.integer")
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
            "Invalid seeder configuration for module 'test.named'. Attribute 'user:name' is type 'text' but generator 'text' returns type 'integer'"
        )
    }
}
