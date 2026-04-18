/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.seeder.generator.PrimitiveValueGenerator.Companion.ROWID_MAX_VALUE
import net.postchain.rell.toolbox.seeder.generator.PrimitiveValueGenerator.Companion.ROWID_MIN_VALUE
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path

class DataGenRangeTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.range"

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `range data generator generates integers within specified range`() {
        val entityName = "numbers"
        val firstIntegerField = "first"
        val secondIntegerField = "second"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $firstIntegerField: integer;
                    $secondIntegerField: integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100)
                        attributes {
                            attribute(firstIntegerField) {
                                generator("range")
                                min(-100)
                                max(1)
                            }
                            attribute(secondIntegerField) {
                                generator("range")
                                min(-1)
                                max(100)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)
        val result = dataGenerator.generate(schema, config)

        val entitiesData = result.entityData["$moduleName:$entityName"]!!
        val first = entitiesData.map { it.fields[firstIntegerField]!!.value as Long }
        val second = entitiesData.map { it.fields[secondIntegerField]!!.value as Long }

        assertThat(first).all {
            each { it.isBetween(-100, 1) }
        }

        assertThat(second).all {
            each { it.isBetween(-1, 100) }
        }
    }

    @Test
    fun `range data generator generates big integers within specified range`() {
        val entityName = "numbers"
        val firstBigIntegerField = "first"
        val secondBigIntegerField = "second"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $firstBigIntegerField: big_integer;
                    $secondBigIntegerField: big_integer;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100)
                        attributes {
                            attribute(firstBigIntegerField) {
                                generator("range")
                                min(BigInteger.valueOf(-2000000))
                                max(BigInteger.valueOf(1))
                            }
                        }
                        attributes {
                            attribute(secondBigIntegerField) {
                                generator("range")
                                min(BigInteger.valueOf(-1))
                                max(BigInteger.valueOf(2000000))
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)
        val result = dataGenerator.generate(schema, config)

        val entitiesData = result.entityData["$moduleName:$entityName"]!!
        val first = entitiesData.map { it.fields[firstBigIntegerField]!!.value as BigInteger }
        val second = entitiesData.map { it.fields[secondBigIntegerField]!!.value as BigInteger }

        assertThat(first).all {
            each {
                it.isGreaterThanOrEqualTo(BigInteger.valueOf(-2000000))
                it.isLessThanOrEqualTo(BigInteger.valueOf(1))
            }
        }
        assertThat(second).all {
            each {
                it.isGreaterThanOrEqualTo(BigInteger.valueOf(-1))
                it.isLessThanOrEqualTo(BigInteger.valueOf(2000000))
            }
        }
    }

    @Test
    fun `range data generator generates decimals within specified range defined with decimal`() {
        val entityName = "numbers"
        val firstDecimalField = "first"
        val secondDecimalField = "second"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $firstDecimalField: decimal;
                    $secondDecimalField: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100)
                        attributes {
                            attribute(firstDecimalField) {
                                generator("range")
                                min(-10.0)
                                max(1.0)
                            }
                            attribute(secondDecimalField) {
                                generator("range")
                                min(-1.0)
                                max(10.0)
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
        val first = entityData.map { it.fields[firstDecimalField]!!.value as BigDecimal }
        val second = entityData.map { it.fields[secondDecimalField]!!.value as BigDecimal }

        assertThat(first).all {
            each {
                it.isGreaterThanOrEqualTo(BigDecimal("-10.0"))
                it.isLessThanOrEqualTo(BigDecimal("1.0"))
            }
        }
        assertThat(second).all {
            each {
                it.isGreaterThanOrEqualTo(BigDecimal("-1.0"))
                it.isLessThanOrEqualTo(BigDecimal("10.0"))
            }
        }
    }

    @Test
    fun `range data generator generates decimals within specified range defined with integers`() {
        val entityName = "numbers"
        val firstDecimalField = "first"
        val secondDecimalField = "second"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $firstDecimalField: decimal;
                    $secondDecimalField: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100)
                        attributes {
                            attribute(firstDecimalField) {
                                generator("range")
                                min(-10)
                                max(1)
                            }
                            attribute(secondDecimalField) {
                                generator("range")
                                min(-1)
                                max(10)
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
        val first = entityData.map { it.fields[firstDecimalField]!!.value as BigDecimal }
        val second = entityData.map { it.fields[secondDecimalField]!!.value as BigDecimal }

        assertThat(first).all {
            each {
                it.isGreaterThanOrEqualTo(BigDecimal("-10.0"))
                it.isLessThanOrEqualTo(BigDecimal("1.0"))
            }
        }
        assertThat(second).all {
            each {
                it.isGreaterThanOrEqualTo(BigDecimal("-1.0"))
                it.isLessThanOrEqualTo(BigDecimal("10.0"))
            }
        }
    }

    @Test
    fun `range data generator generates long range when used on rowid type`() {
        val entityName = "entityName"
        val idFieldName = "id"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $idFieldName: rowid;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100) // Generate enough samples for good test coverage
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)
        val entityData = result.entityData["$moduleName:$entityName"]!!
        val first = entityData.map { it.fields[idFieldName]!!.value as Long }

        assertThat(first).all {
            each {
                it.isGreaterThanOrEqualTo(ROWID_MIN_VALUE.toLong())
                it.isLessThanOrEqualTo(ROWID_MAX_VALUE.toLong())
            }
        }
    }

    @Test
    fun `data generator generates multiple range fields in same entity`() {
        val entityName = "product"
        val priceFieldName = "price"
        val stockFieldName = "stock"
        val ratingFieldName = "rating"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityName {
                    $priceFieldName: big_integer;
                    $stockFieldName: integer;
                    $ratingFieldName: decimal;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(100)
                        attributes {
                            attribute(priceFieldName) {
                                generator("range")
                                min(BigInteger.valueOf(-2000000))
                                max(BigInteger.valueOf(1))
                            }
                            attribute(stockFieldName) {
                                generator("range")
                                min(-10)
                                max(50)
                            }
                            attribute(ratingFieldName) {
                                generator("range")
                                min(-1.0)
                                max(5.0)
                            }
                        }
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)
        val result = dataGenerator.generate(schema, config)

        val productEntities = result.entityData["$moduleName:$entityName"]!!

        val prices = productEntities.map { it.fields[priceFieldName]!!.value as BigInteger }
        val stocks = productEntities.map { it.fields[stockFieldName]!!.value as Long }
        val ratings = productEntities.map { it.fields[ratingFieldName]!!.value as BigDecimal }

        assertThat(prices).each {
            it.isGreaterThanOrEqualTo(BigInteger.valueOf(-2000000))
            it.isLessThanOrEqualTo(BigInteger.valueOf(1))
        }

        assertThat(stocks).each {
            it.isBetween(-10, 50)
        }

        assertThat(ratings).each {
            it.isGreaterThanOrEqualTo(BigDecimal("-1.0"))
            it.isLessThanOrEqualTo(BigDecimal("5.0"))
        }
    }
}
