package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.RellDokkaPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl

@Serializable
data class RellConfig(val name: String, val modules: List<String>?) {
    fun toPluginConfig() = PluginConfigurationImpl(
            RellDokkaPlugin::class.qualifiedName!!,
            DokkaConfiguration.SerializationFormat.JSON,
            Json.encodeToString(this)
    )
}
