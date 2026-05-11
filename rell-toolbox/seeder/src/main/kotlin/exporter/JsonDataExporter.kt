/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import java.nio.file.Path

class JsonDataExporter : BaseDataExporter() {
    private val objectMapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path, mountName: String) {
        val outputFile = prepareOutputFile(outputPath)
        objectMapper.writeValue(outputFile, data.entityData)
    }
}
