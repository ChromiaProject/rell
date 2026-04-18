/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder

import net.postchain.rell.toolbox.seeder.config.dsl.configuration
import net.postchain.rell.toolbox.seeder.exporter.DataExporterFactory
import net.postchain.rell.toolbox.seeder.exporter.OutputFormat
import net.postchain.rell.toolbox.seeder.generator.DataGenerator
import net.postchain.rell.toolbox.seeder.schema.SchemaReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class SeederTest {

    @Test
    fun testSeeder(@TempDir tempDir: File) {
        val schemaReader = SchemaReader()
        val schema = schemaReader.readSchema(
            File(this::class.java.getResource("/seeder-test/src")!!.toURI()),
            listOf("main")
        )

        val config = configuration {
            module("main") {

                entity("ns1.ns2.employee") {
                    count(10)
                    dataPattern("first_name", "first_name")
                    dataPattern("last_name", "last_name")
                    dataPattern("personal_email", "email")
                    dataPattern("work_email", "email")
                    dataPattern("work_phone", "phone_number")
                    range("age", 18, 65)
                }

                entity("ns3.department") {
                    count(5)
                    dataPattern("name", "company.department")
                }
            }
        }

        val dataGenerator = DataGenerator()
        val data = dataGenerator.generate(schema, config)

        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)
//        val outputFile = File("seeder.rell").toPath()
        val outputFile = tempDir.resolve("seeder.rell").toPath()
        dataExporter.export(data, schema, outputFile, outputFile.nameWithoutExtension)

        println(outputFile.readText())

        println()
        println(outputFile.absolutePathString())
    }
}
