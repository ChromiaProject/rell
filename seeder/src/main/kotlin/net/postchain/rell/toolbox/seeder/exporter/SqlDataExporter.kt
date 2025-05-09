package net.postchain.rell.toolbox.seeder.exporter

import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import java.io.FileWriter
import java.nio.file.Path

class SqlDataExporter : BaseDataExporter() {
    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path) {
        val outputFile = prepareOutputFile(outputPath)
        FileWriter(outputFile).use { writer ->
            for ((entityName, records) in data.entityData) {
                for (record in records) {
                    val columns = record.fields.keys.joinToString(", ")
                    val values = record.fields.values.joinToString(", ") { formatSqlValue(it) }

                    writer.write("INSERT INTO $entityName ($columns) VALUES ($values);\n")
                }
                writer.write("\n")
            }
        }
    }

    private fun formatSqlValue(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is String -> "'${value.replace("'", "''")}'"
            is ByteArray -> "X'${value.joinToString("") { "%02x".format(it) }}'"
            else -> value.toString()
        }
    }
}
