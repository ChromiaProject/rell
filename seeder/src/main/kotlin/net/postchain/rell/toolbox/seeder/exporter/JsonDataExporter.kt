package net.postchain.rell.toolbox.seeder.exporter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import java.nio.file.Path

class JsonDataExporter : BaseDataExporter() {
    private val objectMapper = ObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path) {
        val outputFile = prepareOutputFile(outputPath)
        objectMapper.writeValue(outputFile, data.entityData)
    }
}
