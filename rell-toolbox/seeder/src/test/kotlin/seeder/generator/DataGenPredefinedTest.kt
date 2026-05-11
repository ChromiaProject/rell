/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import net.postchain.rell.toolbox.seeder.config.Distribution
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.AttributeConfigParser
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.Ignore

class DataGenPredefinedTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.predefined"

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `predefined values with sequential distribution should follow sequence order`() {
        val entityName = "product"
        val categoryFieldName = "category"
        val predefinedValues = listOf("Electronics", "Clothing", "Food")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
                        count(6)
                        attributes {
                            attribute(categoryFieldName) {
                                generator("predefined")
                                values(predefinedValues)
                                distribution(Distribution.SEQUENTIAL)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val products = result.entityData["$moduleName:$entityName"]!!
        val categories = products.map { it.fields[categoryFieldName]!!.value as String }

        assertThat(categories).isEqualTo(
            listOf(
                "Electronics",
                "Clothing",
                "Food",
                "Electronics",
                "Clothing",
                "Food"
            )
        )
    }

    @Test
    fun `predefined values should use sequential distribution by default`() {
        val entityName = "product"
        val categoryFieldName = "category"
        val predefinedValues = listOf("Electronics", "Clothing", "Food")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
                        count(6)
                        attributes {
                            attribute(categoryFieldName) {
                                generator("predefined")
                                values(predefinedValues)
                                // No distribution specified, should default to sequential
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val products = result.entityData["$moduleName:$entityName"]!!
        val categories = products.map { it.fields[categoryFieldName]!!.value as String }

        assertThat(categories).isEqualTo(
            listOf(
                "Electronics",
                "Clothing",
                "Food",
                "Electronics",
                "Clothing",
                "Food"
            )
        )
    }

    @Test
    fun `predefined values with random distribution should use random selection`() {
        val entityName = "product"
        val categoryFieldName = "category"
        val predefinedValues = listOf("Electronics", "Clothing", "Food")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
                        count(10)
                        attributes {
                            attribute(categoryFieldName) {
                                generator("predefined")
                                values(predefinedValues)
                                distribution(Distribution.RANDOM)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        // define seed to make it deterministic
        val fixedSeedDataGenerator = DataGenerator(randomSeed = 42)
        val result = fixedSeedDataGenerator.generate(schema, config)

        val products = result.entityData["$moduleName:$entityName"]!!
        val categories = products.map { it.fields[categoryFieldName]!!.value as String }

        assertThat(categories).containsOnly(*predefinedValues.toTypedArray())

        assertThat(categories).isNotEqualTo(
            List(20) { predefinedValues[it % predefinedValues.size] }
        )
    }

    // TODO: current implementation doesn't respect weight, would need to add support for it
    @Test
    @Ignore
    fun `predefined values with weighted distribution should select from the list`() {
        val entityName = "product"
        val categoryFieldName = "category"
        val predefinedValues = listOf("Electronics", "Clothing", "Food")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
                        count(10)
                        attributes {
                            attribute(categoryFieldName) {
                                generator("predefined")
                                values(predefinedValues)
                                distribution(Distribution.WEIGHTED)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val products = result.entityData["$moduleName:$entityName"]!!
        products.map { it.fields[categoryFieldName]!!.value as String }

        assertThat(true).isEqualTo(false)
    }

    @Test
    fun `predefined values with single value should always return that value`() {
        val entityName = "product"
        val statusFieldName = "status"
        val singleValue = "Active"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $statusFieldName: text;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(5)
                        attributes {
                            attribute(statusFieldName) {
                                generator("predefined")
                                values(listOf(singleValue))
                                // Distribution doesn't matter with a single value
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val products = result.entityData["$moduleName:$entityName"]!!
        val statuses = products.map { it.fields[statusFieldName]!!.value as String }

        assertThat(statuses).each { it.isEqualTo(singleValue) }
    }

    @Test
    fun `predefined values with enum type should use valid enum values`() {
        val entityName = "task"
        val priorityFieldName = "priority"
        val enumValues = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
        val enumMap = enumValues.mapIndexed { index, value -> value to index }.toMap()
        val predefinedEnumValues = listOf("MEDIUM", "LOW", "CRITICAL", "HIGH")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                enum priority {
                    LOW, MEDIUM, HIGH, CRITICAL
                }

                entity $entityName {
                    $priorityFieldName: priority;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(5)
                        attributes {
                            attribute(priorityFieldName) {
                                generator("predefined")
                                values(predefinedEnumValues)
                                distribution(Distribution.SEQUENTIAL)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val tasks = result.entityData["$moduleName:$entityName"]!!
        val priorities = tasks.map { it.fields[priorityFieldName]!!.value as Int }

        assertThat(priorities).containsOnly(*enumMap.values.toTypedArray())

        val expectedSequence = listOf(1, 0, 3, 2, 1)
        assertThat(priorities).isEqualTo(expectedSequence)
    }

    @Test
    fun `predefined values with enum type using random distribution`() {
        val entityName = "task"
        val statusFieldName = "status"
        val enumValues = listOf("TODO", "IN_PROGRESS", "DONE", "CANCELLED")
        val enumMap = enumValues.mapIndexed { index, value -> value to index }.toMap()

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                enum task_status {
                    TODO, IN_PROGRESS, DONE, CANCELLED
                }

                entity $entityName {
                    $statusFieldName: task_status;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(20) // Generate enough records to ensure randomness
                        attributes {
                            attribute(statusFieldName) {
                                generator("predefined")
                                values(enumValues)
                                distribution(Distribution.RANDOM)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val fixedSeedDataGenerator = DataGenerator(randomSeed = 42)
        val result = fixedSeedDataGenerator.generate(schema, config)

        val tasks = result.entityData["$moduleName:$entityName"]!!
        val statuses = tasks.map { it.fields[statusFieldName]!!.value as Int }

        assertThat(statuses).containsOnly(*enumMap.values.toTypedArray())

        assertThat(statuses).isNotEqualTo(
            List(20) { enumMap[enumValues[it % enumValues.size]] }
        )
    }

    @Test
    fun `data generator should generate correct types for Rell R_Boolean`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(false, true)

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Boolean }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_Integer`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(1, 2)

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Int }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_BigInteger`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(
            AttributeConfigParser.BIG_INTEGER_MIN_VALUE,
            AttributeConfigParser.BIG_INTEGER_MAX_VALUE
        )

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as BigInteger }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_Decimal`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(1.1, 2.2)

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).isEqualTo(predefinedValues.map { it.toString() })
    }

    @Test
    fun `data generator should generate correct types for Rell R_Text`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf("AA", "BB")

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_ByteArray`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(
            "023078E17989BF3C5D152FC18E84DAADACB4EA4D298822C45AF0A6A8F9FFEE5557",
            "F94AAAE80143FFF2FABFD45F1C1B20A22AAF41622D1F05FE2B0FC14345748958"
        )

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_RowId`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf(1, 2)

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
                        count(2)
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

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as Int }

        assertThat(fieldData).isEqualTo(predefinedValues)
    }

    @Test
    fun `data generator should generate correct types for Rell R_Json`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val predefinedValues = listOf("{}", "{a: 1}")

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
                        count(2)
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
            "{\"a\":1}"
        )

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldData = entityData.map { it.fields[fieldName]!!.value as String }

        assertThat(fieldData).isEqualTo(expectedValues)
    }
}
