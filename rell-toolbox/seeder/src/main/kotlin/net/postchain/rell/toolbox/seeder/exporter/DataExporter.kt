/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter

import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import java.nio.file.Path

interface DataExporter {
    fun export(data: GeneratedData, schema: RellSchema, outputPath: Path, mountName: String)
}