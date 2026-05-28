/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import com.chromia.rell.doc.compose.ModuleDocs
import com.chromia.rell.doc.compose.SourceBuild
import com.chromia.rell.doc.compose.SystemBuild
import com.chromia.rell.doc.model.Doc_Site
import com.chromia.rell.doc.render.Paths
import com.chromia.rell.doc.render.SiteRender
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import kotlin.io.path.isRegularFile

/**
 * Public entry point — chromia-cli's `GenerateDocsSiteCommand` calls `.generate()`.
 *
 * The class name and constructor signature are part of the binary contract; the implementation
 * inside no longer touches Dokka. We build a `Doc_Site` from either the Rell stdlib or the
 * user's compiled `R_Module`s, then drive `SiteRender` to write the output tree.
 */
class RellDokkaGenerator(private val configBuilder: RellDokkaPluginConfigurationBuilder) {
    fun generate() {
        val config = configBuilder.build()
        val site = buildSite(config)
        SiteRender(config.targetFolder).render(site)
    }

    private fun buildSite(config: RellDokkaPluginConfiguration): Doc_Site {
        // For system-lib docs, fold in the bundled `rell.md` so package summaries (root,
        // crypto, chain_context, op_context, rell.test*…) populate without the caller needing
        // to pass `--includes` explicitly.
        val extraTexts = if (config.system) listOfNotNull(ModuleDocs.loadBundledSystemDocsText()) else emptyList()
        val moduleDocs = ModuleDocs.load(config.includes, additionalTexts = extraTexts)
        val module = if (config.system) {
            SystemBuild.build(
                title = config.title,
                // Slug pinned to the legacy Dokka module name, not the page title — see
                // RellDokkaPluginConfiguration.SYSTEM_MODULE_NAME for why URL compatibility needs this.
                slug = Paths.fileSlug(RellDokkaPluginConfiguration.SYSTEM_MODULE_NAME),
                moduleDocs = moduleDocs,
            )
        } else {
            val projectRoot = requireNotNull(config.projectRoot) { "Project root not set for source-mode generation" }
            val modules = config.modules.orEmpty()
            SourceBuild.build(
                title = config.title,
                slug = Paths.fileSlug(config.title),
                projectRoot = projectRoot,
                entryPointModules = modules,
                additionalModules = config.additionalModules,
                moduleDocs = moduleDocs,
                cliEnv = config.cliEnv,
            )
        }
        val hidden = computeHiddenPackages(config)
        return Doc_Site(
            title = config.title,
            footerMessage = config.footerMessage,
            modules = listOf(module),
            customStyleSheets = config.customStyleSheets.filter { it.isRegularFile() },
            customAssets = config.customAssets.filter { it.isRegularFile() },
            sourceLinks = config.sourceLinks,
            hiddenPackages = hidden,
            system = config.system,
        )
    }

    private fun computeHiddenPackages(config: RellDokkaPluginConfiguration): Set<String> {
        if (config.system) return emptySet()
        val explicitlyAllowed = config.additionalModules.toSet()
        return config.filteredModules.filterNot { it in explicitlyAllowed }.toSet()
    }
}
