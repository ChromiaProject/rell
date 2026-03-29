/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.page

import com.chromia.rell.dokka.config.RellDokkaGlobalState
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.customTags
import com.chromia.rell.dokka.descriptions
import com.chromia.rell.dokka.dri
import com.chromia.rell.dokka.model.isFunction
import com.chromia.rell.dokka.model.isNamespace
import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isQuery
import com.chromia.rell.dokka.model.namespaceName
import com.chromia.rell.dokka.renderers.html.RellTabbedContentType
import com.chromia.rell.dokka.sourceSets
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.resolvers.anchors.SymbolAnchorHint
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.transformers.pages.tags.CustomTagContentProvider
import org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.WithScope
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.Property
import org.jetbrains.dokka.model.doc.TagWrapper
import org.jetbrains.dokka.model.firstMemberOfTypeOrNull
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.BasicTabbedContentType
import org.jetbrains.dokka.pages.ContentDivergentGroup
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TabbedContentType
import org.jetbrains.dokka.pages.TabbedContentTypeExtra
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.utilities.DokkaLogger

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
                            contentBuilder.contentFor(mainDRI, mainSourcesetData) { text("Name") }
                    )
            ) { dPackage ->

                if (
                        rellDokkaPluginConfiguration != null &&
                        (RellDokkaGlobalState.getHiddenPackages().none { dPackage.packageName == it })
                ) {

                    val documentations = dPackage.sourceSets.map { platform ->
                        dPackage.descriptions[platform]?.also { it.root }
                    }
                    val haveSameContent =
                            documentations.all { it?.root == documentations.firstOrNull()?.root && it?.root != null }

                    link(dPackage.name, dPackage.dri)
                    if (dPackage.sourceSets.size == 1 || (documentations.isNotEmpty() && haveSameContent)) {
                        documentations.first()?.let { firstParagraphComment(kind = ContentKind.Comment, content = it.root) }
                    }
                }
            }
        }
    }

    override fun contentForPackage(p: DPackage): ContentGroup {
        return contentBuilder.contentFor(p) {
            group(kind = ContentKind.Cover) {
                cover(
                        when {
                            rellDokkaPluginConfiguration?.system == true -> "Namespace definitions"
                            p.isNamespace() -> "Namespace ${p.namespaceName()}"
                            else -> "Module-level declarations"
                        }
                )
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
        require(extensions.isEmpty())
        rellTypesBlock(types)
        propertiesBlock("Properties", properties, BasicTabbedContentType.PROPERTY)
        val (funs, queries, operations) = functions.splitFunctionsTypes()
        rellFunctionsBlock("Functions", funs, listOf(), BasicTabbedContentType.FUNCTION)
        rellFunctionsBlock("Queries", queries, listOf(), RellTabbedContentType.QUERY)
        rellFunctionsBlock("Operations", operations, listOf(), RellTabbedContentType.OPERATION)
    }

    private fun List<DFunction>.splitFunctionsTypes(): Triple<List<DFunction>, List<DFunction>, List<DFunction>> {
        val funs = mutableListOf<DFunction>()
        val queries = mutableListOf<DFunction>()
        val ops = mutableListOf<DFunction>()
        for (element in this) {
            when {
                element.isFunction() -> funs.add(element)
                element.isOperation() -> ops.add(element)
                element.isQuery() -> queries.add(element)
                else -> throw IllegalArgumentException("$element is neither marked as function, query nor operation")
            }
        }
        return Triple(funs, queries, ops)
    }

    private fun PageContentBuilder.DocumentableContentBuilder.propertiesBlock(
            name: String,
            declarations: List<DProperty>,
            contentType: TabbedContentType
    ) {
        functionsOrPropertiesBlock(
                name = name,
                contentKind = ContentKind.Properties,
                contentType = contentType,
                declarations = declarations,
                extensions = listOf()
        )
    }

    private fun PageContentBuilder.DocumentableContentBuilder.rellFunctionsBlock(
            name: String,
            declarations: List<DFunction>,
            extensions: List<DFunction>,
            contentType: TabbedContentType
    ) {
        functionsOrPropertiesBlock(
                name = name,
                contentKind = ContentKind.Functions,
                contentType = contentType,
                declarations = declarations,
                extensions = extensions
        )
    }

    private fun PageContentBuilder.DocumentableContentBuilder.functionsOrPropertiesBlock(
            name: String,
            contentKind: ContentKind,
            contentType: TabbedContentType,
            declarations: List<Documentable>,
            extensions: List<Documentable>
    ) {
        if (declarations.isEmpty() && extensions.isEmpty()) return

        // This groupBy should probably use LocationProvider
        val grouped = declarations.groupBy {
            NameAndIsExtension(it.name, isExtension = false)
        } + extensions.groupBy {
            NameAndIsExtension(it.name, isExtension = true)
        }

        val groups = grouped.entries
                .sortedWith(compareBy(NameAndIsExtension.comparator) { it.key })
                .map { (nameAndIsExtension, elements) ->
                    DivergentElementGroup(
                            name = nameAndIsExtension.name,
                            kind = when {
                                nameAndIsExtension.isExtension -> ContentKind.Extensions
                                else -> contentKind
                            },
                            elements = elements
                    )
                }

        divergentBlock(
                name = name,
                kind = contentKind,
                extra = mainExtra,
                contentType = contentType,
                groups = groups
        )
    }

    private fun PageContentBuilder.DocumentableContentBuilder.divergentBlock(
            name: String,
            kind: ContentKind,
            extra: PropertyContainer<ContentNode>,
            contentType: TabbedContentType,
            groups: List<DivergentElementGroup>,
    ) {
        if (groups.isEmpty()) return

        // be careful: extra here will be applied for children by default
        group(extra = extra + TabbedContentTypeExtra(contentType)) {
            header(2, name, kind = kind, extra = extra) { }
            table(kind, extra = extra, styles = emptySet()) {
                header {
                    group { text("Name") }
                    group { text("Summary") }
                }
                groups.forEach { group ->
                    val elementName = group.name
                    val rowKind = group.kind
                    val sortedElements = sortDivergentElementsDeterministically(group.elements)

                    // This override here is needed to be able to split members and extensions into separate tabs in HTML renderer.
                    // The idea is that `contentType` is set to the `tab group` itself to `FUNCTION` or `PROPERTY` (above in the code),
                    // and then for `extensions` we override it - in this case we are able to create 2 tabs in HTML renderer:
                    // - `Members` - which show ONLY member functions/properties
                    // - `Members & Extensions` - which show BOTH member functions/properties and extensions for this classlike
                    val rowContentTypeOverride = when (rowKind) {
                        ContentKind.Extensions -> when (contentType) {
                            BasicTabbedContentType.FUNCTION -> BasicTabbedContentType.EXTENSION_FUNCTION
                            BasicTabbedContentType.PROPERTY -> BasicTabbedContentType.EXTENSION_PROPERTY
                            else -> null
                        }

                        else -> null
                    }

                    row(
                            dri = sortedElements.map { it.dri }.toSet(),
                            sourceSets = sortedElements.flatMap { it.sourceSets }.toSet(),
                            kind = rowKind,
                            styles = emptySet(),
                            extra = extra.addAll(
                                    listOfNotNull(
                                            rowContentTypeOverride?.let(::TabbedContentTypeExtra),
                                            elementName?.let { name -> SymbolAnchorHint(name, kind) }
                                    )
                            )
                    ) {
                        link(
                                text = elementName.orEmpty(),
                                address = sortedElements.first().dri,
                                kind = rowKind,
                                styles = setOf(ContentStyle.RowTitle),
                                sourceSets = sortedElements.sourceSets.toSet(),
                                extra = extra
                        )
                        divergentGroup(
                                ContentDivergentGroup.GroupID(name),
                                sortedElements.map { it.dri }.toSet(),
                                kind = rowKind,
                                extra = extra
                        ) {
                            for (element in sortedElements) {
                                instance(
                                    setOf(element.dri),
                                    element.sourceSets.toSet()
                                ) {
                                    divergent(extra = PropertyContainer.empty()) {
                                        group {
                                            +buildSignature(element)
                                        }
                                    }
                                    after(
                                        extra = PropertyContainer.empty()
                                    ) {
                                        contentForBrief(element)
                                        contentForCustomTagsBrief(element) //TODO: Verify if we can skip
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun PageContentBuilder.DocumentableContentBuilder.contentForBrief(
            documentable: Documentable
    ) {
        documentable.sourceSets.forEach { sourceSet ->
            documentable.documentation[sourceSet]?.let { node ->
                /*
                    Get description or a tag that holds documentation.
                    This tag can be either property or constructor but constructor tags are handled already in analysis so we
                    only need to keep an eye on property

                    We purposefully ignore all other tags as they should not be visible in brief
                 */

                node.firstMemberOfTypeOrNull<Description>() ?: node.firstMemberOfTypeOrNull<Property>()
                        .takeIf { documentable is DProperty }
            }?.let { tag ->
                group(sourceSets = setOf(sourceSet), kind = ContentKind.BriefComment) {
                    createBriefComment(documentable, sourceSet, tag)
                }
            }
        }
    }


    private fun PageContentBuilder.DocumentableContentBuilder.contentForCustomTagsBrief(documentable: Documentable) {
        val customTags = documentable.customTags
        if (customTags.isEmpty()) return

        documentable.sourceSets.forEach { sourceSet ->
            customTags.forEach { (_, sourceSetTag) ->
                sourceSetTag[sourceSet]?.let { tag ->
                    createBriefCustomTags(sourceSet, tag)
                }
            }
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.createBriefCustomTags(
            sourceSet: DokkaConfiguration.DokkaSourceSet,
            customTag: CustomTagWrapper
    ) {
        customTagContentProviders.filter { it.isApplicable(customTag) }.forEach { provider ->
            with(provider) {
                contentForBrief(sourceSet, customTag)
            }
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.createBriefComment(
        documentable: Documentable,
        sourceSet: DokkaConfiguration.DokkaSourceSet,
        tag: TagWrapper
    ) {
        comment(tag.root)
    }


    private fun PageContentBuilder.DocumentableContentBuilder.rellTypesBlock(types: List<Documentable>) {
        if (types.isEmpty()) return

        val grouped = types
                // This groupBy should probably use LocationProvider
                .groupBy(Documentable::name)
                .mapValues { (_, elements) ->
                    // This hacks displaying actual typealias signatures along classlike ones
                    if (elements.any { it is DClasslike }) elements.filter { it !is DTypeAlias } else elements
                }

        val groups = grouped.entries
                .sortedWith(compareBy(nullsFirst(canonicalAlphabeticalOrder)) { it.key })
                .map { (name, elements) ->
                    DivergentElementGroup(
                            name = name,
                            kind = ContentKind.Classlikes,
                            elements = elements
                    )
                }

        divergentBlock(
                name = "Types",
                kind = ContentKind.Classlikes,
                extra = mainExtra,
                contentType = BasicTabbedContentType.TYPE,
                groups = groups
        )
    }


    class DivergentElementGroup(
            val name: String?,
            val kind: ContentKind,
            val elements: List<Documentable>
    )

    private fun sortDivergentElementsDeterministically(elements: List<Documentable>): List<Documentable> =
            elements.takeIf { it.size > 1 } // the majority are single-element lists, but no real benchmarks done
                    ?.sortedWith(divergentDocumentableComparator)
                    ?: elements

    private val divergentDocumentableComparator =
            compareBy<Documentable, String?>(nullsLast()) { it.dri.packageName }
                    .thenBy(nullsFirst()) { it.dri.classNames } // nullsFirst for top level to be first
                    .thenBy(
                            nullsLast(
                                    compareBy<Callable> { it.params.size }
                                            .thenBy { it.signature() }
                            )
                    ) { it.dri.callable }

    data class NameAndIsExtension(val name: String?, val isExtension: Boolean) {
        companion object {
            val comparator = compareBy(
                    comparator = nullsFirst(canonicalAlphabeticalOrder),
                    selector = NameAndIsExtension::name
            ).thenBy(NameAndIsExtension::isExtension)
        }
    }


}

private val canonicalAlphabeticalOrder: Comparator<in String> = String.CASE_INSENSITIVE_ORDER.thenBy { it }
