package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.RellDokkaPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.plugability.ConfigurableBlock

/**
 * Global registry for modules that should be excluded from UI natigation.
 *
 * The configuration is created early and serialized, while module discovery happens
 * during processing, so we need this shared state to accumulate all modules that
 * should be hidden.
 *
 * We can't simply use the filteredModules parameter in RellDokkaPluginConfiguration 
 * because that configuration is created and serialized before the RellAnalysis runs.
 * Dokka creates new RellDokkaPluginConfiguration instances for each pipeline stage,
 * which means after initialization any changes made to the configuration in one stage 
 * would be lost in later stages.
 * This registry provides a singleton that persists across the entire documentation 
 * generation process, ensuring consistent hiding of modules regardless of pipeline stage.
 */
object HiddenPackagesRegistry {
    val packages = mutableListOf<String>()
    fun hide(moduleNames: Collection<String>) {
        packages.addAll(moduleNames)
    }
}

@Serializable
data class RellDokkaPluginConfiguration(
        val name: String,
        val modules: List<String>?,
        val system: Boolean = false,
        val filteredModules: List<String> = listOf()
) : ConfigurableBlock {
    fun toPluginConfig() = PluginConfigurationImpl(
            RellDokkaPlugin::class.qualifiedName!!,
            DokkaConfiguration.SerializationFormat.JSON,
            Json.encodeToString(this)
    )

    companion object {
        const val SYSTEM_TITLE = "Rell System Library API Reference"
        val SYSTEM_CONFIG = RellDokkaPluginConfiguration(SYSTEM_TITLE, listOf("rell", "rell.test"), system = true)
    }
}
