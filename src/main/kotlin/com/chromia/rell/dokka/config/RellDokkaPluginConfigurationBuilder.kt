package com.chromia.rell.dokka.config

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.DokkaBase
import java.io.File

class RellDokkaPluginConfiguration2(
        private val system: Boolean,
        private val title: String,
        private val sourceSets: List<DokkaSourceSetImpl>,
        private val targetFolder: File,
        private val modules: List<String>,
        private val customStyleSheets: List<String>?,
        private val customAssets: List<String>?,
        private val footerMessage: String
) {
    class Builder(private val system: Boolean = false) {
        private var title = "My Rell Dapp"
        private var sourceSets: List<DokkaSourceSetImpl> = listOf()
        private var targetFolder: File = File("site")
        private var modules: List<String> = listOf()
        private var customStyleSheets: List<String>? = null
        private var customAssets: List<String>? = null
        private var footerMessage: String = ""
        fun title(title: String) = apply { this.title = title }
        fun sourceSets(projectRoot: File, includes: List<File>) = apply {
            sourceSets = when (system) {
                true -> RellModule.entries.map { it.sourceSet(includes) }
                else -> rellSourceSets(projectRoot, includes)
            }
        }

        fun targetFolder(targetFolder: File) = apply {
            this.targetFolder = targetFolder
        }

        fun modules(modules: List<String>) = apply {
            this.modules = modules
        }

        fun customStyleSheets(customStyleSheets: List<String>?) = apply {
            this.customStyleSheets = customStyleSheets
        }

        fun customAssets(customAssets: List<String>?) = apply {
            this.customAssets = customAssets
        }

        fun footerMessage(footerMessage: String) = apply {
            this.footerMessage = footerMessage
        }

        fun build() = RellDokkaPluginConfiguration2(
                system,
                title,
                sourceSets,
                targetFolder,
                modules,
                customStyleSheets,
                customAssets,
                footerMessage
        )
    }

    fun getConfig(): DokkaConfigurationImpl {
        val rellDokkaPluginConfig = configureRellDokkaPlugin()
        val dokkaBaseConf = configureDokkaBasePlugin()

        return DokkaConfigurationImpl(
                moduleName = rellDokkaPluginConfig.name,
                sourceSets = sourceSets,
                outputDir = targetFolder,
                pluginsConfiguration = listOf(rellDokkaPluginConfig.toPluginConfig(), dokkaBaseConf),
        )
    }

    private fun configureRellDokkaPlugin(): RellDokkaPluginConfiguration {
        if (system) {
            return RellDokkaPluginConfiguration.SYSTEM_CONFIG
        }
        return RellDokkaPluginConfiguration(title, modules)
    }

    private fun configureDokkaBasePlugin() =
            PluginConfigurationImpl(
                    DokkaBase::class.qualifiedName!!,
                    DokkaConfiguration.SerializationFormat.JSON,
                    """{ 
                            ${customStyleSheets?.let { styles -> "\"customStyleSheets\": [${styles.joinToString { "\"$it\"" }}]," } ?: ""}
                            ${customAssets?.let { a -> "\"customAssets\":[${a.joinToString { "\"$it\"" }}]," } ?: ""}
                            "footerMessage": "$footerMessage"
                        }"""
            )
}
