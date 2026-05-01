/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.dsl

import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.ModuleConfig
import net.postchain.rell.toolbox.seeder.config.serializer.ConfigurationSerializer
import java.nio.file.Path

class ConfigurationBuilder {
    private val modules = mutableMapOf<String, ModuleConfig>()

    fun module(moduleName: String, modulePath: String? = null, block: ModuleConfigBuilder.() -> Unit) {
        val builder = ModuleConfigBuilder()
        builder.module(moduleName)
        builder.block()
        val path = resolvePath(modulePath ?: moduleName)
        modules[path] = builder.build()
    }

    private fun resolvePath(moduleName: String): String {
        return "modules/" + moduleName.replace(".", "/") + ".yml"
    }

    fun build(): Configuration {
        return Configuration(modules)
    }
}

fun configuration(block: ConfigurationBuilder.() -> Unit): Configuration {
    val builder = ConfigurationBuilder()
    builder.block()
    return builder.build()
}

fun configFile(
    outputDirPath: Path,
    seederName: String = "test_chromia_seeder",
    block: ConfigurationFileBuilder.() -> Unit
): Path {
    val outputDir = outputDirPath.toFile().resolve(seederName)

    val builder = ConfigurationFileBuilder()
    builder.block()
    val config = builder.build()

    return ConfigurationSerializer().serialize(config, outputDir.toPath())
}

class ConfigurationFileBuilder {
    var configuration: Configuration? = null
    fun configuration(block: ConfigurationBuilder.() -> Unit) {
        val builder = ConfigurationBuilder()
        builder.block()
        configuration = builder.build()
    }

    fun build(): Configuration = checkNotNull(configuration) { "Configuration is not set" }
}
