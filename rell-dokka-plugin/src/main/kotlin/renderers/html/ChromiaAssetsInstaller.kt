/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.renderers.html

import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer

class ChromiaAssetsInstaller(private val dokkaContext: DokkaContext) : PageTransformer {

    private val chromiaPages = listOf(
            "images/chromia-symbol.png",
            "images/logo-icon.svg",
            "images/searchIcon.svg",
            "images/searchIcon-night.svg",
            "images/theme-toggle-night.svg",
            "fonts/Battlefin-Black.otf",
            "fonts/NB International Bold Italic.ttf",
            "fonts/NBInternationalBoldWebfont.ttf",
            "fonts/NBInternationalMonoWebfont.ttf",
            "fonts/NBInternationalRegularWebfont.ttf",
            "README.md",
    ).toRenderSpecificResourcePage()

    private val chromiaStyles = listOf(
            "styles/chromia-styles.css",
    ).toRenderSpecificResourcePage()

    override fun invoke(input: RootPageNode): RootPageNode {
        val styles = chromiaStyles.map { it.name }
        val withEmbeddedResources = input.transformContentPagesTree { it.modified(embeddedResources = it.embeddedResources + styles) }

        if (dokkaContext.configuration.delayTemplateSubstitution)
            return withEmbeddedResources
        val (currentResources, otherPages) = withEmbeddedResources.children.partition { it is RendererSpecificResourcePage }
        // Drop any existing resource page whose output name collides with ours. Without this, Dokka's parallel
        // renderer copies two sources (ours + the default) into the same destination file, and
        // File.copyRecursively(overwrite = true) races — the post-copy length() check intermittently observes
        // a truncated destination and throws "Source file wasn't copied completely, length of destination file differs."
        val overriddenNames = (chromiaPages + chromiaStyles).mapTo(mutableSetOf()) { it.name }
        return input.modified(
                children = otherPages + currentResources.filterNot { it.name in overriddenNames } + chromiaPages + chromiaStyles
        )
    }

    private fun List<String>.toRenderSpecificResourcePage(): List<RendererSpecificResourcePage> =
            map { RendererSpecificResourcePage(it, emptyList(), RenderingStrategy.Copy("/chromia/$it")) }
}
