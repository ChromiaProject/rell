/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter

import java.io.File
import java.nio.file.Path
import kotlin.io.path.createParentDirectories

abstract class BaseDataExporter : DataExporter {
    protected fun prepareOutputFile(outputPath: Path): File {
        val absolutePath = outputPath.createParentDirectories().toAbsolutePath()
        return absolutePath.toFile()
    }
}
