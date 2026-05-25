/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import com.chromia.rell.doc.model.Doc_SourceLink
import net.postchain.rell.api.base.RellCliEnv
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Configuration builder. Public API: chromia-cli's `GenerateDocsSiteCommand` constructs this and
 * hands it to `RellDokkaGenerator`. The method shapes here are part of that contract — they
 * cannot change without coordinating a chromia-cli release.
 *
 * The class is no longer Dokka-bound (the previous `build()` produced a `DokkaConfigurationImpl`);
 * the only consumer of `build()` is the generator inside this module, so it now returns a plain
 * `RellDokkaPluginConfiguration` POJO that the new generator consumes directly.
 */
class RellDokkaPluginConfigurationBuilder private constructor(
    private val system: Boolean,
    private val title: String,
    private val modules: List<String>?,
    private val projectRoot: File?,
) {
    private var targetFolder: Path = Path("site")
    private var customStyleSheets: List<String>? = null
    private var customAssets: List<String>? = null
    private var footerMessage: String = ""
    private var includes: List<File> = listOf()
    private val sourceLinks: MutableList<Doc_SourceLink> = mutableListOf()
    private var filteredModules: List<String> = listOf()
    private var additionalModules: List<String> = listOf()
    private var cliEnv: RellCliEnv? = null

    constructor(title: String, modules: List<String>?, projectRoot: File) :
        this(system = false, title = title, modules = modules, projectRoot = projectRoot)

    fun includes(includes: List<File>) = apply { this.includes = includes }
    fun filteredModules(filteredModules: List<String>) = apply { this.filteredModules = filteredModules }
    fun additionalModules(additionalModules: List<String>) = apply { this.additionalModules = additionalModules }
    fun targetFolder(targetFolder: File) = apply { this.targetFolder = targetFolder.toPath() }
    fun customStyleSheets(customStyleSheets: List<String>?) = apply { this.customStyleSheets = customStyleSheets }
    fun customAssets(customAssets: List<String>?) = apply { this.customAssets = customAssets }
    fun footerMessage(footerMessage: String) = apply { this.footerMessage = footerMessage }
    /** Used by chromia-cli to wire its `BuildCliEnv` (a `RellCliEnv` subtype) into the
     *  compiler's IO surface. Suppression: the inspector only sees this module. */
    @Suppress("unused")
    fun cliEnv(cliEnv: RellCliEnv) = apply { this.cliEnv = cliEnv }

    fun addSourceLink(localDirectory: String, remoteUrl: URI, remoteLineSuffix: String?) =
        addSourceLink(localDirectory, remoteUrl.toURL(), remoteLineSuffix)

    fun addSourceLink(localDirectory: String, remoteUrl: URL, remoteLineSuffix: String?) = apply {
        sourceLinks.add(
            Doc_SourceLink(
                localDirectory = File(localDirectory).toPath(),
                remoteUrl = remoteUrl,
                remoteLineSuffix = remoteLineSuffix,
            )
        )
    }

    internal fun build(): RellDokkaPluginConfiguration = RellDokkaPluginConfiguration(
        system = system,
        title = title,
        modules = modules,
        projectRoot = projectRoot?.toPath(),
        targetFolder = targetFolder,
        customStyleSheets = customStyleSheets.orEmpty().map { File(it).toPath() },
        customAssets = customAssets.orEmpty().map { File(it).toPath() },
        footerMessage = footerMessage,
        includes = includes.map { it.toPath() },
        sourceLinks = sourceLinks.toList(),
        filteredModules = filteredModules,
        additionalModules = additionalModules,
        cliEnv = cliEnv,
    )

    companion object {
        @JvmStatic
        val SYSTEM: RellDokkaPluginConfigurationBuilder
            get() = newSystemBuilder()

        @JvmStatic
        fun newSystemBuilder(): RellDokkaPluginConfigurationBuilder =
            RellDokkaPluginConfigurationBuilder(
                system = true,
                title = RellDokkaPluginConfiguration.SYSTEM_TITLE,
                modules = null,
                projectRoot = null,
            )
    }
}
