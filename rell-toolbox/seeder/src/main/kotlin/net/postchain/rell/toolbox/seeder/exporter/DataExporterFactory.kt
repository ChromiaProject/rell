package net.postchain.rell.toolbox.seeder.exporter

object DataExporterFactory {
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
