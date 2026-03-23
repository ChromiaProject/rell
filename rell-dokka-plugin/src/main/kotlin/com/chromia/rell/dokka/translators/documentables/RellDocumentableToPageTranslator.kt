/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.translators.documentables

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.page.RellPageCreator
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.pages.ModulePageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.query
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator
import org.jetbrains.dokka.base.translators.documentables.DefaultDocumentableToPageTranslator

/**
 * Injects the [RellDokkaPluginConfiguration] into our [RellPageCreator].
 * @see DefaultDocumentableToPageTranslator
 */
class RellDocumentableToPageTranslator(context: DokkaContext) : DocumentableToPageTranslator {

    private val configuration = configuration<DokkaBase, DokkaBaseConfiguration>(context)
    private val rellConfig = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)
    private val commentsToContentConverter = context.plugin<DokkaBase>().querySingle { commentsToContentConverter }
    private val signatureProvider = context.plugin<DokkaBase>().querySingle { signatureProvider }
    private val customTagContentProviders = context.plugin<DokkaBase>().query { customTagContentProvider }
    private val logger = context.logger

    override fun invoke(module: DModule): ModulePageNode =
            RellPageCreator(rellConfig, configuration, commentsToContentConverter, signatureProvider, logger, customTagContentProviders)
                    .pageForModule(module)
}
