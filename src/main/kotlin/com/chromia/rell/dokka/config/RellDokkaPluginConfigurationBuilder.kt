package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration.Companion.SYSTEM_TITLE
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.SourceLinkDefinitionImpl
import org.jetbrains.dokka.base.DokkaBase
import java.io.File
import java.net.URL

class RellDokkaPluginConfigurationBuilder private constructor(
        private val system: Boolean,
        private val title: String,
        private val modules: List<String>?,
        private val projectRoot: File?,
) {
    private var targetFolder: File = File("site")
    private var customStyleSheets: List<String>? = null
    private var customAssets: List<String>? = null
    private var footerMessage: String = ""
    private var includes: List<File> = listOf()
    private val sourceLinks: MutableSet<SourceLinkDefinitionImpl> = mutableSetOf()

    constructor(title: String, modules: List<String>?, projectRoot: File): this(false, title, modules, projectRoot)

    companion object {
        val SYSTEM = RellDokkaPluginConfigurationBuilder(true, SYSTEM_TITLE, null, null)
    }

    fun includes(includes: List<File>) = apply { this.includes = includes }

    fun targetFolder(targetFolder: File) = apply {
        this.targetFolder = targetFolder
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

    fun addSourceLink(localDirectory: String, remoteUrl: URL, remoteLineSuffix: String?) = apply {
        this.sourceLinks.add(SourceLinkDefinitionImpl(localDirectory, remoteUrl, remoteLineSuffix))
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

    fun build() = DokkaConfigurationImpl(
            moduleName = title,
            outputDir = targetFolder,
            suppressInheritedMembers = true,
            sourceSets = if (system) RellModule.entries.map { it.sourceSet(includes) } else rellSourceSets(projectRoot!!, includes, sourceLinks),
            pluginsConfiguration = listOf(configureRellDokkaPlugin().toPluginConfig(), configureDokkaBasePlugin()),
    )
}
