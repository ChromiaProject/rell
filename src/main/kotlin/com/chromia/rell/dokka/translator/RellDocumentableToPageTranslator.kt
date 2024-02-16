package com.chromia.rell.dokka.translator

import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.transformers.pages.tags.CustomTagContentProvider
import org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.pages.ModulePageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.query
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator
import org.jetbrains.dokka.utilities.DokkaLogger

class RellDocumentableToPageTranslator(context: DokkaContext) : DocumentableToPageTranslator {

    private val configuration = configuration<DokkaBase, DokkaBaseConfiguration>(context)
    private val commentsToContentConverter = context.plugin(DokkaBase::class)!!.querySingle { commentsToContentConverter }
    private val signatureProvider = context.plugin<DokkaBase>(DokkaBase::class)!!.querySingle { signatureProvider }
    private val customTagContentProviders = context.plugin<DokkaBase>(DokkaBase::class)!!.query { customTagContentProvider }
    private val logger = context.logger

    override fun invoke(module: DModule): ModulePageNode =
            RellPageCreator(
                    configuration,
                    commentsToContentConverter,
                    signatureProvider,
                    logger,
                    customTagContentProviders,
            ).pageForModule(module)

    
    @OptIn(InternalDokkaApi::class)
    class RellPageCreator(configuration: DokkaBaseConfiguration?,
                          commentsToContentConverter: CommentsToContentConverter,
                          signatureProvider: SignatureProvider,
                          logger: DokkaLogger,
                          customTagContentProviders: List<CustomTagContentProvider>)
        : DefaultPageCreator(
            configuration,
            commentsToContentConverter,
            signatureProvider,
            logger,
            customTagContentProviders,
            RellLanguageParser(),
    )
}
