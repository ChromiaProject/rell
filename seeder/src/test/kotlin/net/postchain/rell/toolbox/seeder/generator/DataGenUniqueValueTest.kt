package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.collections.map

class DataGenUniqueValueTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.predefined"

    @TempDir
    private lateinit var tempDir: Path


    @Test
    fun `should generate unique values when attribute is a key`() {
        val personEntity = "person"
        val locationField = "location"
        val recordCount = 200

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $personEntity {
                    key $locationField: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(personEntity) {
                        count(recordCount)
                        attributes {
                            attribute(locationField) {
                                generator("address.country")
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$personEntity"]!!
        val fieldData = entityData.map { it.fields[locationField]!!.value as String }

        assertThat(fieldData.size).isEqualTo(fieldData.distinct().size)
        assertThat(fieldData.size).isEqualTo(recordCount)
    }


    @Test
    fun `should throw an exception in cases it can't generate unique values for given input`() {
        val personEntity = "person"
        val locationField = "location"
        val recordCount = 300

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $personEntity {
                    key $locationField: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(personEntity) {
                        count(recordCount)
                        attributes {
                            attribute(locationField) {
                                generator("address.country")
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val exception = assertThrows<DataGenerationException> {
            dataGenerator.generate(schema, config)
        }

        assertThat(exception.message).isEqualTo("Could not generate unique values for 'location' of entity 'person'")
    }

    @Test
    fun `should throw an exception in cases it can't generate unique values for enum`() {
        val personEntity = "person"
        val shirtField = "shirt"
        val recordCount = 4

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                enum shirt_size {
                    short,
                    medium,
                    long
                }
                entity $personEntity {
                    key $shirtField: shirt_size;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(personEntity) {
                        count(recordCount)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val exception = assertThrows<DataGenerationException> {
            dataGenerator.generate(schema, config)
        }

        assertThat(exception.message).isEqualTo("Could not generate unique values for 'shirt' of entity 'person'")
    }

    @Test
    fun `unique pool isn't shared between different entities`() {
        val customerEntity = "customer"
        val sellerEntity = "seller"
        val shirtField = "shirt"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                enum shirt_size {
                    short,
                    medium,
                    long
                }
                entity $customerEntity {
                    key $shirtField: shirt_size;
                }
                entity $sellerEntity {
                    key $shirtField: shirt_size;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(customerEntity) {
                        count(3)
                    }
                    entity(sellerEntity) {
                        count(3)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val data = dataGenerator.generate(schema, config)

        val customEnumValues = data.entityData["$moduleName:$customerEntity"]!!.map { it.fields[shirtField]!!.value }
        assertThat(customEnumValues).containsExactlyInAnyOrder(0, 1, 2)

        val sellerEnumValues = data.entityData["$moduleName:$sellerEntity"]!!.map { it.fields[shirtField]!!.value }
        assertThat(sellerEnumValues).containsExactlyInAnyOrder(0, 1, 2)
    }

    @Test
    fun `unique reference generation fails when not enough unique entities`() {
        val customerEntity = "customer"
        val cardEntity = "card"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
   
                entity $customerEntity {
                    key $cardEntity;
                }
                entity $cardEntity {
                    name;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(customerEntity) {
                        count(10)
                    }
                    entity(cardEntity) {
                        count(9)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val exception = assertThrows<DataGenerationException> {
            dataGenerator.generate(schema, config)
        }

        assertThat(exception.message).isEqualTo("Could not generate unique values for '$cardEntity' of entity '$customerEntity'")
    }

    @Test
    fun `should throw an exception in cases it can't generate unique values for bytearray`() {
        val personEntity = "person"
        val accessKey = "access_key"
        val recordCount = 257

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $personEntity {
                    key $accessKey: byte_array;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(personEntity) {
                        count(recordCount)
                        attributes {
                            attribute(accessKey) {
                                generator("byte_array")
                                size(1)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val exception = assertThrows<DataGenerationException> {
            dataGenerator.generate(schema, config)
        }

        assertThat(exception.message).isEqualTo("Could not generate unique values for '$accessKey' of entity '$personEntity'")
    }

    @Test
    fun `should throw an exception in cases it can't generate unique values for entity`() {
        val personEntity = "person"
        val bookEntity = "book"
        val bookAttribute = "my_book"
        val personCount = 10
        val bookCount = 9

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $personEntity {
                    name;
                    key $bookAttribute: $bookEntity;
                }
                entity $bookEntity {
                    name;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(personEntity) {
                        count(personCount)
                    }
                    entity(bookEntity) {
                        count(bookCount)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val exception = assertThrows<DataGenerationException> {
            dataGenerator.generate(schema, config)
        }

        assertThat(exception.message).isEqualTo("Could not generate unique values for '$bookAttribute' of entity '$personEntity'")
    }
}