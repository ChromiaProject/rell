package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.RellDokkaPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.plugability.ConfigurableBlock

@Serializable
data class RellConfig(val name: String, val modules: List<String>?, val system: Boolean = false) : ConfigurableBlock {
    fun toPluginConfig() = PluginConfigurationImpl(
            RellDokkaPlugin::class.qualifiedName!!,
            DokkaConfiguration.SerializationFormat.JSON,
            Json.encodeToString(this)
    )

    companion object {
        val SYSTEM = RellConfig("Rell-Api-Reference", listOf("rell", "rell.test"), system = true)
        val SYSTEM_SOURCE_SETS = SystemLibSourceSet.entries.map { it.sourceSet }
    }

    enum class SystemLibSourceSet(val scope: String, val sourceSetName: String, val module: C_LibModule) {
        MAIN("<root>", "rell", Lib_Rell.MODULE),
        TEST("rell.test", "test", Lib_RellTest.MODULE);

        val sourceSet = DokkaSourceSetImpl(sourceSetID = DokkaSourceSetID(scope, sourceSetName), displayName = sourceSetName)

        companion object {
            fun findModule(sourceSet: DokkaConfiguration.DokkaSourceSet) = entries.find { sourceSet == it.sourceSet }?.module
        }

    }
}
