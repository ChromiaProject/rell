@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.page

import org.jetbrains.dokka.base.resolvers.anchors.SymbolAnchorHint
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.base.translators.documentables.sourceSets
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.BasicTabbedContentType
import org.jetbrains.dokka.pages.ContentDivergentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TabbedContentType
import org.jetbrains.dokka.pages.TabbedContentTypeExtra

fun PageContentBuilder.DocumentableContentBuilder.rellFunctionsBlock(
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

fun PageContentBuilder.DocumentableContentBuilder.functionsOrPropertiesBlock(
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

fun PageContentBuilder.DocumentableContentBuilder.divergentBlock(
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
                        sortedElements.map { element ->
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
//                                    contentForBrief(element)
//                                    contentForCustomTagsBrief(element) //TODO: Verify if we can skip
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


fun PageContentBuilder.DocumentableContentBuilder.rellTypesBlock(types: List<Documentable>) {
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

val canonicalAlphabeticalOrder: Comparator<in String> = String.CASE_INSENSITIVE_ORDER.thenBy { it }

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