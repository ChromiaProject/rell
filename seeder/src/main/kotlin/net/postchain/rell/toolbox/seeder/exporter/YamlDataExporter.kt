package net.postchain.rell.toolbox.seeder.exporter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import java.nio.file.Path

class YamlDataExporter : BaseDataExporter() {
    private val objectMapper = ObjectMapper(YAMLFactory()).apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path, mountName: String) {
        val outputFile = prepareOutputFile(outputPath)
        objectMapper.writeValue(outputFile, data.entityData)
    }
}
