@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.page

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.model.isFunction
import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isQuery
import com.chromia.rell.dokka.renderers.html.RellTabbedContentType
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.transformers.pages.tags.CustomTagContentProvider
import org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator
import org.jetbrains.dokka.base.translators.documentables.descriptions
import org.jetbrains.dokka.base.translators.documentables.dri
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.WithScope
import org.jetbrains.dokka.pages.BasicTabbedContentType
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.utilities.DokkaLogger

@OptIn(InternalDokkaApi::class)
class RellPageCreator(
        private val rellDokkaPluginConfiguration: RellDokkaPluginConfiguration?,
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
                    name = rellDokkaPluginConfiguration?.system?.let { "Namespaces" } ?: "Modules",
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
                cover( if (rellDokkaPluginConfiguration?.system == true) "Namespace definitions" else "Module-level declarations")
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

    override fun contentForScopes(
            scopes: List<WithScope>,
            sourceSets: Set<DokkaConfiguration.DokkaSourceSet>,
            extensions: List<Documentable>
    ): ContentGroup = contentForScope(
            dri = @Suppress("UNCHECKED_CAST") (scopes as List<Documentable>).dri,
            sourceSets = sourceSets,
            types = scopes.flatMap { it.classlikes } +
                    scopes.filterIsInstance<DPackage>().flatMap { it.typealiases },
            functions = scopes.flatMap { it.functions },
            properties = scopes.flatMap { it.properties },
            extensions = extensions,
    )

    override fun contentForScope(
            s: WithScope,
            dri: DRI,
            sourceSets: Set<DokkaConfiguration.DokkaSourceSet>,
            extensions: List<Documentable>
    ): ContentGroup = contentForScopes(listOf(s), sourceSets, extensions)

    private fun contentForScope(
            dri: Set<DRI>,
            sourceSets: Set<DokkaConfiguration.DokkaSourceSet>,
            types: List<Documentable>,
            functions: List<DFunction>,
            properties: List<DProperty>,
            extensions: List<Documentable>, //TODO: Remove if not needed
    ) = contentBuilder.contentFor(dri, sourceSets) {
        rellTypesBlock(types)
        rellFunctionsBlock("Functions", functions.filter { it.isFunction() }, listOf(), BasicTabbedContentType.FUNCTION)
        rellFunctionsBlock("Queries", functions.filter { it.isQuery() }, listOf(), RellTabbedContentType.QUERY)
        rellFunctionsBlock("Operations", functions.filter { it.isOperation() }, listOf(), RellTabbedContentType.OPERATION)
    }

    data class NameAndIsExtension(val name: String?, val isExtension: Boolean) {
        companion object {
            val comparator = compareBy(
                    comparator = nullsFirst(canonicalAlphabeticalOrder),
                    selector = NameAndIsExtension::name
            ).thenBy(NameAndIsExtension::isExtension)
        }
    }
}
