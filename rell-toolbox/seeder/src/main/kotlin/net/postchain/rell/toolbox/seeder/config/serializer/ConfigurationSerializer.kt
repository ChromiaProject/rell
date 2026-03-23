/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.serializer

import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.ModuleConfig
import java.io.File
import java.nio.file.Path

class ConfigurationSerializer {
    private val moduleConfigurationSerializer = ModuleConfigSerializer()
    fun serialize(config: Configuration, outputDirPath: Path): Path {
        val outputDir = outputDirPath.toFile().apply { mkdirs() }

        val modulePaths = config.modules.map { (modulePath, moduleConfig) ->
            serializeModule(modulePath, moduleConfig, outputDir)
            modulePath
        }

        return serializeMainConfig(modulePaths, outputDir)
    }

    private fun serializeModule(modulePath: String, moduleConfig: ModuleConfig, outputDir: File) {
        val moduleFile = outputDir.resolve(modulePath)
        moduleFile.parentFile.mkdirs()
        moduleFile.writeText(moduleConfigurationSerializer.serialize(moduleConfig))
    }

    private fun serializeMainConfig(modulePaths: List<String>, outputDir: File): Path {
        val configFile = outputDir.resolve("seeder.yml")

        val configString = buildString {
            appendLine("modules:")
            modulePaths.forEach {
                appendLine("  - \"$it\"")
            }
        }
        configFile.writeText(configString)
        return configFile.toPath()
    }
}
