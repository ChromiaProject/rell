package net.postchain.rell.toolbox.seeder.exporter

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isTrue
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.dsl.configuration
import net.postchain.rell.toolbox.seeder.generator.DataGenerator
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

// TODO: We do not have a handling if a user uses predefined value generator with %REF as a value
//  Also make sure to test json format when actually executing the exported rell files

class RellDataExporterTest {

    private lateinit var exporter: RellDataExporter
    private lateinit var schema: RellSchema
    private lateinit var testDataBuilder: TestDataBuilder

    @TempDir
    private lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        exporter = RellDataExporter()
    }

    @Test
    fun `seeder module file gets generated in source folder and app compilers after`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity foo { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configuration {}
        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        assertThat(outputFile.exists()).isTrue()
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `generates correct data for transaction type`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                    entity foo { transaction; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configuration {}
        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        assertThat(outputFile.exists()).isTrue()
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }


    @Test
    fun `generates correct data for block type`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                    entity foo { block; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configuration {}
        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        assertThat(outputFile.exists()).isTrue()
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `generates correct data for transaction type`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                    entity foo { transaction; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configuration {}
        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }


    @Test
    fun `generates correct data for block type`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                    entity foo { block; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)
        val config = configuration {}
        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `seeder module file resolves entities correctly`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                namespace ns1 {
                    entity employee { name; }
                }
                namespace ns2 {
                    namespace ns3 {
                        entity department { name; }
                    }
                }
                """.trimIndent()
            )
            addModule(
                "foo_module",
                """
                module;
                entity foo { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration {}

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        val fileContent = outputFile.readText()

        assertThat(fileContent).contains("import mod1: main;")
        assertThat(fileContent).contains("import mod2: foo_module;")
        assertThat(fileContent).contains("mod1.ns1.employee")
        assertThat(fileContent).contains("mod1.ns2.ns3.department")
        assertThat(fileContent).contains("mod2.foo")
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `seeder module file defines functions correctly`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                namespace ns1 {
                    entity employee { name; }
                }
                namespace ns2 {
                    namespace ns3 {
                        entity department { name; }
                    }
                }
                """.trimIndent()
            )
            addModule(
                "foo_module",
                """
                module;
                entity foo { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration {}

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        val fileContent = outputFile.readText()

        assertThat(fileContent).contains("function seed_main_ns1_employee")
        assertThat(fileContent).contains("function seed_main_ns2_ns3_department")
        assertThat(fileContent).contains("function seed_foo_module_foo")
        assertThat(fileContent).contains("function get_entity_id(name: text)")
        assertThat(fileContent).contains("operation seed_data(batch_size: integer = 1000)")
        assertThat(fileContent).contains("function text_insert_rowid(value: text, state: iteration_state)")
        assertThat(
            fileContent
        ).contains("function resolve_refs(existing_data: map<integer, gtv>, json_data: text, ref_data: text)")
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `every entity gets exported when only one entity is configured`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                namespace ns1 {
                    entity employee { name; }
                }
                namespace ns2 {
                    namespace ns3 {
                        entity department { name; }
                    }
                }
                """.trimIndent()
            )
            addModule(
                "foo_module",
                """
                module;
                entity foo { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration {
            module("foo_module") {
                entity("foo") { count(2) }
            }
        }

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        val fileContent = outputFile.readText()

        assertThat(fileContent).contains("function seed_main_ns1_employee")
        assertThat(fileContent).contains("function seed_main_ns2_ns3_department")
        assertThat(fileContent).contains("function seed_foo_module_foo")
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `when top level entity is configured which contains a reference entity, both gets exported`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity employee { department; }
                entity department { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration {
            module("main") {
                entity("employee") { count(2) }
            }
        }

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        val fileContent = outputFile.readText()

        assertThat(fileContent).contains("function seed_main_employee")
        assertThat(fileContent).contains("function seed_main_department")
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `when predefined value are using text with value reference placeholder something`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                entity employee { department; }
                entity department { name; }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration {
            module("main") {
                entity("employee") { count(2) }
                entity("department") {
                    count(2)
                    attributes {
                        attribute("name") {
                            generator("predefined")
                            values(listOf("%REF"))
                        }
                    }
                }
            }
        }

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        val fileContent = outputFile.readText()

        assertThat(fileContent).contains("function seed_main_employee")
        assertThat(fileContent).contains("function seed_main_department")
        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    @Test
    fun `All supported entity types are exported and app compiles`() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                enum shirt_size {
                    small,
                    medium,
                    large
                }
                
                entity waste_level {
                    name: text;
                }
                
                entity some {
                    name;
                    last_name: text;
                    age: integer;
                    waste_level;
                    my_enum: shirt_size;
                    salary: decimal;
                    active: boolean;
                    meta: json;
                    pubkey;
                    tuid;
                    image: byte_array;
                    some_big_number: big_integer;
                    some_row_id: rowid;
                }
                """.trimIndent()
            )
        }

        val schemaReader = SchemaReader()
        schema = schemaReader.readSchema(testDataBuilder.sourceFolder)

        val config = configuration { }

        val dataGenerator = DataGenerator()
        val testData = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
        val outputFile = testDataBuilder.sourceFolder.resolve("seeder.rell").toPath()
        dataExporter.export(testData, schema, outputFile, outputFile.nameWithoutExtension)

        assertDoesNotThrow { compileApp(testDataBuilder.sourceFolder) }
    }

    private fun compileApp(sourceDir: File) {
        val conf = RellApiCompile.Config.Builder()
            .moduleArgsMissingError(false)
            .mountConflictError(true)
            .docSymbolsEnabled(false)
            .build()
        RellApiCompile.compileApp(conf, sourceDir, null)
    }
}
