package com.chromia.rell.dokka.doc

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.base.transformers.pages.tags.CustomTagContentProvider
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TextStyle

object AliasDocTagProvider: CustomTagContentProvider {
    override fun isApplicable(customTag: CustomTagWrapper): Boolean {
        return customTag.name == ALIAS_DOC_TAG
    }

    override fun PageContentBuilder.DocumentableContentBuilder.contentForDescription(
            sourceSet: DokkaConfiguration.DokkaSourceSet,
            customTag: CustomTagWrapper
    ) {
        group(sourceSets = setOf(sourceSet), styles = emptySet()) {
            header(4, customTag.name)
            table {
                row(
                        kind = ContentKind.Comment
                ) {
                    comment(customTag.root, styles = setOf(ContentStyle.RowTitle))
                    comment(Text("Alias target"))
                }

            }
        }
    }

    override fun PageContentBuilder.DocumentableContentBuilder.contentForBrief(
            sourceSet: DokkaConfiguration.DokkaSourceSet,
            customTag: CustomTagWrapper
    ) {
        group(sourceSets = setOf(sourceSet), styles = setOf(TextStyle.InlineComment)) {
            table {
                row(
                        kind = ContentKind.Comment
                ) {
                    text(customTag.name + " ", styles = setOf(TextStyle.Bold, ContentStyle.RowTitle))
                    comment(customTag.root, styles = setOf())
                }

            }
        }
    }

    fun aliasDocTag(target: DRI, name: String) = CustomTagWrapper(
            DocumentationLink(target, listOf(Text(name))),
            ALIAS_DOC_TAG
    )

    private const val ALIAS_DOC_TAG = "Alias"
}
