package net.postchain.rell.toolbox.seeder.generator

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DataGenTextTest {
    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()
    private val moduleName = "test.text"

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `data generator generates correct string length for range`() {
        val entityName = "article"
        val titleFieldName = "title"
        val contentFieldName = "content"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
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

        val result = dataGenerator.generate(schema, config)

        val articleEntities = result.entityData["$moduleName:$entityName"]

        val titles = articleEntities!!.map { it.fields[titleFieldName]!!.value }
        val contents = articleEntities.map { it.fields[contentFieldName]!!.value }

        assertThat(titles).each { title ->
            title.isInstanceOf(String::class)
                .prop(titleFieldName) { it.length }
                .isBetween(10, 100)
        }

        assertThat(contents).each { content ->
            content.isInstanceOf(String::class)
                .prop(contentFieldName) { it.length }
                .isBetween(100, 1000)
        }
    }


    @Test
    fun `data generator should generate default values for string generator with missing min_max values`() {
        val entityName = "article"
        val titleFieldName = "title"
        val contentFieldName = "content"

        val testDataBuilder = testData(tempDir) {
            addModule(
                moduleName.replace(".", "/"),
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
                module(moduleName) {
                    entity(entityName) {
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

        val result = dataGenerator.generate(schema, config)

        val articleEntities = result.entityData["$moduleName:$entityName"]

        val titles = articleEntities!!.map { it.fields[titleFieldName]!!.value }
        val contents = articleEntities.map { it.fields[contentFieldName]!!.value }

        assertThat(titles).each { title ->
            title.isInstanceOf(String::class)
                .prop(titleFieldName) { it.length }
                .isBetween(DataGenerator.TEXT_MIN_DEFAULT, DataGenerator.TEXT_MAX_DEFAULT)
        }

        assertThat(contents).each { content ->
            content.isInstanceOf(String::class)
                .prop(contentFieldName) { it.length }
                .isBetween(DataGenerator.TEXT_MIN_DEFAULT, DataGenerator.TEXT_MAX_DEFAULT)
        }
    }

}