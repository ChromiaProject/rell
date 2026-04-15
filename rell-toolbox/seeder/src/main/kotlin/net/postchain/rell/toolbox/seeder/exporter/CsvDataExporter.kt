/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter


import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import java.io.File
import java.nio.file.Path

class CsvDataExporter : BaseDataExporter() {
    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path, mountName: String) {
        // For CSV, we'll create a directory and export each entity to a separate file
        val outputFile = outputPath.toFile()
        val outputDir = if (outputFile.isDirectory) {
            outputFile
        } else {
            val dir = File(outputFile.parentFile, outputFile.nameWithoutExtension)
            dir.mkdirs()
            dir
        }

        for ((entityName, records) in data.entityData) {
            val entityFile = File(outputDir, "$entityName.csv")

            entityFile.bufferedWriter().use { writer ->
                if (records.isNotEmpty()) {
                    // Write header
                    val header = records.first().fields.keys.joinToString(",")
                    writer.write("$header\n")

                    // Write records
                    for (record in records) {
                        val values = record.fields.values.joinToString(",") { formatCsvValue(it) }
                        writer.write("$values\n")
                    }
                }
            }
        }
    }

    private fun formatCsvValue(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> "\"${value.replace("\"", "\"\"")}\""
            is ByteArray -> value.joinToString("") { "%02x".format(it) }
            else -> value.toString()
        }
    }
}
