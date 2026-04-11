/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration.Companion.SYSTEM_TITLE
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.SourceLinkDefinitionImpl
import org.jetbrains.dokka.base.DokkaBase
import net.postchain.rell.api.base.RellCliEnv
import java.io.File
import java.net.URI
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
    private var filteredModules: List<String> = listOf()
    private var additionalModules: List<String> = listOf()
    private var cliEnv: RellCliEnv? = null

    constructor(title: String, modules: List<String>?, projectRoot: File): this(false, title, modules, projectRoot)

    companion object {
        val SYSTEM = RellDokkaPluginConfigurationBuilder(true, SYSTEM_TITLE, null, null)

        fun newSystemBuilder() = RellDokkaPluginConfigurationBuilder(true, SYSTEM_TITLE, null, null)
    }

    fun includes(includes: List<File>) = apply { this.includes = includes }

    fun filteredModules(filteredModules: List<String>) = apply { this.filteredModules = filteredModules }

    fun additionalModules(additionalModules: List<String>) = apply { this.additionalModules = additionalModules }

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

    fun addSourceLink(localDirectory: String, remoteUrl: URI, remoteLineSuffix: String?) = apply {
        this.sourceLinks.add(SourceLinkDefinitionImpl(localDirectory, remoteUrl.toURL(), remoteLineSuffix))
    }

    fun addSourceLink(localDirectory: String, remoteUrl: URL, remoteLineSuffix: String?) = apply {
        this.sourceLinks.add(SourceLinkDefinitionImpl(localDirectory, remoteUrl, remoteLineSuffix))
    }

    fun cliEnv(cliEnv: RellCliEnv) = apply {
        this.cliEnv = cliEnv
        // store it in the singleton holder to bypass serialization
        RellDokkaGlobalState.setCliEnv(cliEnv)
    }

    private fun configureRellDokkaPlugin(): RellDokkaPluginConfiguration {
        if (system) {
            return RellDokkaPluginConfiguration.SYSTEM_CONFIG
        }
        return RellDokkaPluginConfiguration(
            name = title,
            modules = modules,
            filteredModules = filteredModules,
            additionalModules = additionalModules
        )
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
            finalizeCoroutines = false,
    )
}
