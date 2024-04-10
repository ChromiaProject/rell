package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.RellDokkaPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.plugability.ConfigurableBlock
import java.io.File


@Serializable
data class RellDokkaPluginConfiguration(
        val name: String,
        val modules: List<String>?,
        val system: Boolean = false
) : ConfigurableBlock {
    fun toPluginConfig() = PluginConfigurationImpl(
            RellDokkaPlugin::class.qualifiedName!!,
            DokkaConfiguration.SerializationFormat.JSON,
            Json.encodeToString(this)
    )

    fun sourceSets(projectRoot: File, includes: List<File>) = when (system) {
        true -> RellModule.entries.map { it.sourceSet(includes) }
        else -> rellSourceSets(projectRoot, includes)
    }

    companion object {
        const val SYSTEM_TITLE = "Rell System Library API Reference"
        val SYSTEM_CONFIG = RellDokkaPluginConfiguration(SYSTEM_TITLE, listOf("rell", "rell.test"), system = true)
    }
}
