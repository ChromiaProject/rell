package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.RellDokkaPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.plugability.ConfigurableBlock

@Serializable
data class RellDokkaPluginConfiguration(
        val name: String,
        val modules: List<String>?,
        val system: Boolean = false,
        val filteredModules: List<String> = listOf(),
        val additionalModules: List<String> = listOf()
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
