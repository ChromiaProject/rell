@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.page

import com.chromia.rell.dokka.config.RellConfig
import com.chromia.rell.dokka.translator.RellLanguageParser
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.transformers.pages.tags.CustomTagContentProvider
import org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator
import org.jetbrains.dokka.base.translators.documentables.descriptions
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.utilities.DokkaLogger

@OptIn(InternalDokkaApi::class)
class RellPageCreator(
        private val rellConfig: RellConfig?,
        configuration: DokkaBaseConfiguration?,
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
) {

    override fun contentForModule(m: DModule): ContentGroup {
        return contentBuilder.contentFor(m) {
            group(kind = ContentKind.Cover) {
                cover(m.name)
                if (contentForDescription(m).isNotEmpty()) {
                    sourceSetDependentHint(
                            m.dri,
                            m.sourceSets.toSet(),
                            kind = ContentKind.SourceSetDependentHint,
                            styles = setOf(TextStyle.UnderCoverText)
                    ) {
                        +contentForDescription(m)
                    }
                }
            }

            block(
                    name = rellConfig?.system?.let { "Namespaces" } ?: "Modules",
                    level = 2,
                    kind = ContentKind.Packages,
                    elements = m.packages,
                    sourceSets = m.sourceSets.toSet(),
                    needsAnchors = true,
                    headers = listOf(
                            headers("Name")
                    )
            ) {
                val documentations = it.sourceSets.map { platform ->
                    it.descriptions[platform]?.also { it.root }
                }
                val haveSameContent =
                        documentations.all { it?.root == documentations.firstOrNull()?.root && it?.root != null }

                link(it.name, it.dri)
                if (it.sourceSets.size == 1 || (documentations.isNotEmpty() && haveSameContent)) {
                    documentations.first()?.let { firstParagraphComment(kind = ContentKind.Comment, content = it.root) }
                }
            }
        }
    }

    override fun contentForPackage(p: DPackage): ContentGroup {
        return contentBuilder.contentFor(p) {
            group(kind = ContentKind.Cover) {
                cover( if (rellConfig?.system == true) "Namespace definitions" else "Module-level declarations")
                if (contentForDescription(p).isNotEmpty()) {
                    sourceSetDependentHint(
                            dri = p.dri,
                            sourcesetData = p.sourceSets.toSet(),
                            kind = ContentKind.SourceSetDependentHint,
                            styles = setOf(TextStyle.UnderCoverText)
                    ) {
                        +contentForDescription(p)
                    }
                }
            }
            group(styles = setOf(ContentStyle.TabbedContent), extra = mainExtra) {
                +contentForScope(p, p.dri, p.sourceSets)
            }
        }
    }
}