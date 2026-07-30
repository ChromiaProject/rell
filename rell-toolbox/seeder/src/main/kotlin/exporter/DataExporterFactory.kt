/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter

internal object DataExporterFactory {
    fun createExporter(format: OutputFormat): DataExporter {
        return when (format) {
            OutputFormat.RELL -> RellDataExporter()
            OutputFormat.JSON -> JsonDataExporter()
            OutputFormat.YAML -> YamlDataExporter()
            OutputFormat.SQL -> SqlDataExporter()
            OutputFormat.CSV -> CsvDataExporter()
        }
    }
}
