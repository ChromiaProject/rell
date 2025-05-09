package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import java.nio.file.Path
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DataGenReferencesTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.references"

    @TempDir
    private lateinit var tempDir: Path


    @Test
    fun `should handle references when referenced entity comes first in configuration`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val entityRef = "entity_ref_name"
        val nrOfRefEntity = 5

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityRef {
                    name;
                }
                entity $entityName {
                    $fieldName: $entityRef;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityRef) {
                        count(nrOfRefEntity)
                    }
                    entity(entityName) {
                        count(5)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldReferenceData = entityData.map { it.fields[fieldName]!! }

        assertThat(fieldReferenceData.map { it.value }).each {
            it.isInstanceOf<Long>()
                .isBetween(0, nrOfRefEntity.toLong())
        }
        assertThat(fieldReferenceData.map { it.isReference }).containsOnly(true)
        assertThat(fieldReferenceData.map { it.entityReferenceType }).containsOnly("$moduleName:$entityRef")
    }

    @Test
    fun `should handle references when referenced entity comes last in configuration`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val entityRef = "entity_ref_name"
        val nrOfRefEntity = 5

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityRef {
                    name;
                }
                entity $entityName {
                    $fieldName: $entityRef;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(5)
                    }
                    entity(entityRef) {
                        count(nrOfRefEntity)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldReferenceData = entityData.map { it.fields[fieldName]!! }

        assertThat(fieldReferenceData.map { it.value }).each {
            it.isInstanceOf<Long>()
                .isBetween(0, nrOfRefEntity.toLong())
        }
        assertThat(fieldReferenceData.map { it.isReference }).containsOnly(true)
        assertThat(fieldReferenceData.map { it.entityReferenceType }).containsOnly("$moduleName:$entityRef")
    }

    @Test
    fun `should handle references with one to many reference`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val entityRef = "entity_ref_name"
        val nrOfRefEntity = 5

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityRef {
                    name;
                }
                entity $entityName {
                    $fieldName: $entityRef;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(1)
                    }
                    entity(entityRef) {
                        count(nrOfRefEntity)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldReferenceData = entityData.map { it.fields[fieldName]!! }

        assertThat(fieldReferenceData.map { it.value }).each {
            it.isInstanceOf<Long>()
                .isBetween(0, nrOfRefEntity.toLong())
        }
        assertThat(fieldReferenceData.map { it.isReference }).containsOnly(true)
        assertThat(fieldReferenceData.map { it.entityReferenceType }).containsOnly("$moduleName:$entityRef")
    }

    @Test
    fun `should handle references with many to one reference`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val entityRef = "entity_ref_name"
        val nrOfRefEntity = 1

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityRef {
                    name;
                }
                entity $entityName {
                    $fieldName: $entityRef;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(5)
                    }
                    entity(entityRef) {
                        count(nrOfRefEntity)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldReferenceData = entityData.map { it.fields[fieldName]!! }

        assertThat(fieldReferenceData.map { it.value }).each {
            it.isInstanceOf<Long>()
                .isBetween(0, nrOfRefEntity.toLong())
        }
        assertThat(fieldReferenceData.map { it.isReference }).containsOnly(true)
        assertThat(fieldReferenceData.map { it.entityReferenceType }).containsOnly("$moduleName:$entityRef")
    }

    @Test
    fun `should handle field with reference together with non-reference field`() {
        val entityName = "entity_name"
        val fieldName = "field_name"
        val entityRef = "entity_ref_name"
        val nrOfRefEntity = 5

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
                """
                module;
                entity $entityRef {
                    name;
                }
                entity $entityName {
                    $fieldName: $entityRef;
                    name;
                }
                """.trimIndent()
            )
        }

        val configFilePath = configFile(tempDir) {
            configuration {
                module(moduleName) {
                    entity(entityName) {
                        count(5)
                    }
                    entity(entityRef) {
                        count(nrOfRefEntity)
                    }
                }
            }
        }

        val schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configParser.parseConfiguration(configFilePath, schema)

        val result = dataGenerator.generate(schema, config)

        val entityData = result.entityData["$moduleName:$entityName"]!!
        val fieldReferenceData = entityData.map { it.fields[fieldName]!! }
        val fieldNonReferenceData = entityData.map { it.fields["name"]!! }

        assertThat(fieldReferenceData.map { it.value }).each {
            it.isInstanceOf<Long>()
                .isBetween(0, nrOfRefEntity.toLong())
        }
        assertThat(fieldReferenceData.map { it.isReference }).containsOnly(true)
        assertThat(fieldReferenceData.map { it.entityReferenceType }).containsOnly("$moduleName:$entityRef")

        assertThat(fieldNonReferenceData.map { it.value }).each {
            it.isInstanceOf<String>()
                .prop(String::length)
                .isBetween(DataGenerator.TEXT_MIN_DEFAULT, DataGenerator.TEXT_MAX_DEFAULT)
        }
        assertThat(fieldNonReferenceData.map { it.isReference }).containsOnly(false)
        assertThat(fieldNonReferenceData.map { it.entityReferenceType }).containsOnly(null)
    }
}
