package com.chromia.rell.dokka.navigation

import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer
import org.jetbrains.dokka.base.renderers.html.NavigationPageInstaller

class RellNavigationPageInstaller(context: DokkaContext): PageTransformer {

    // TODO: Take implementationdetails from [NavigationPageInstaller]
    override fun invoke(input: RootPageNode): RootPageNode {
        return input
    }

    /*override fun visit(page: ContentPage): NavigationNode {
        return NavigationNode(
                name = page.name,
                dri = page.dri.first(),
                sourceSets = page.sourceSets(),
                icon = null,
                styles = emptySet(), // StrikeThrough if deprecated
                children = page.navigableChildren()
        )
    }*/

    /*private fun ContentPage.navigableChildren() = when (this) {
        is ClasslikePage -> {
            this.navigableChildren()
        }

        is ModulePage -> {
            children
                    .filterIsInstance<ContentPage>()
                    .map(::visit)
                    .sortedWith(navigationNodeOrder)
        }

        else -> {
            children
                    .filterIsInstance<ContentPage>()
                    .map(::visit)
                    .sortedWith(navigationNodeOrder)
        }
    }

    private val navigationNodeOrder: Comparator<NavigationNode> =
            compareBy(canonicalAlphabeticalOrder, NavigationNode::name)

    private fun ClasslikePage.navigableChildren(): List<NavigationNode> {
        // Classlikes should only have other classlikes as navigable children
        val navigableChildren = children
                .filterIsInstance<ClasslikePage>()
                .map(::visit)

        val isEnumPage = documentables.any { it is DEnum }
        return if (isEnumPage) {
            // no sorting for enum entries, should be the same order as in source code
            navigableChildren
        } else {
            navigableChildren.sortedWith(navigationNodeOrder)
        }
    }
*/
}
val canonicalAlphabeticalOrder: Comparator<in String> = String.CASE_INSENSITIVE_ORDER.thenBy { it }
