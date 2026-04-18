/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.serializer

import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.ModuleConfig
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class ConfigurationSerializer {
    private val moduleConfigurationSerializer = ModuleConfigSerializer()
    fun serialize(config: Configuration, outputDirPath: Path): Path {
        val outputDir = outputDirPath.apply { createDirectories() }

        val modulePaths = config.modules.map { (modulePath, moduleConfig) ->
            serializeModule(modulePath, moduleConfig, outputDir)
            modulePath
        }

        return serializeMainConfig(modulePaths, outputDir)
    }

    private fun serializeModule(modulePath: String, moduleConfig: ModuleConfig, outputDir: Path) {
        val moduleFile = outputDir.resolve(modulePath)
        moduleFile.parent.createDirectories()
        moduleFile.writeText(moduleConfigurationSerializer.serialize(moduleConfig))
    }

    private fun serializeMainConfig(modulePaths: List<String>, outputDir: Path): Path {
        val configFile = outputDir / "seeder.yml"

        configFile.bufferedWriter().use { writer ->
            writer.appendLine("modules:")
            modulePaths.forEach {
                writer.appendLine("  - \"$it\"")
            }
        }

        return configFile
    }
}
