package com.chromia.rell.dokka.navigation

import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationNodeIcon
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer
import org.jetbrains.dokka.base.renderers.sourceSets
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.annotations
import org.jetbrains.dokka.base.transformers.documentables.isDeprecated
import org.jetbrains.dokka.base.transformers.documentables.isException
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.JavaModifier
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.ClasslikePage
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.MultimoduleRootPage
import org.jetbrains.dokka.pages.Style
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.pages.WithDocumentables

val canonicalAlphabeticalOrder: Comparator<in String> = String.CASE_INSENSITIVE_ORDER.thenBy { it }

/**
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/plugins/base/src/main/kotlin/renderers/html/htmlPreprocessors.kt#L18
 * This is due to an internal dependency on InternalKotlinAnalysisPlugin found in [NavigationDataProvider]
 */
class RellNavigationPageInstaller(private val context: DokkaContext, filterModules: List<String>?) : NavigationDataProvider(filterModules), PageTransformer {

    override fun invoke(input: RootPageNode): RootPageNode = input.modified(
            children = input.children
                    + NavigationPage(
                    root = navigableChildren(input),
                    moduleName = context.configuration.moduleName,
                    context = context
            )
    )
}

/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/plugins/base/src/main/kotlin/renderers/html/NavigationDataProvider.kt
 * This is due to an internal dependency of InternalKotlinAnalysisPlugin which is removed here
 */
public abstract class NavigationDataProvider(private val filterModules: List<String>? = listOf()) {
    // Always false (previously dependent on InternalKotlinAnalysisPlugin
    private fun Documentable.hasAnyJavaSources(): Boolean {
        return false
    }

    public open fun navigableChildren(input: RootPageNode): NavigationNode = input.withDescendants()
            .first { it is ModulePage || it is MultimoduleRootPage }.let { visit(it as ContentPage) }

    public open fun visit(page: ContentPage): NavigationNode =
            NavigationNode(
                    name = page.displayableName(),
                    dri = page.dri.first(),
                    sourceSets = page.sourceSets(),
                    icon = chooseNavigationIcon(page),
                    styles = chooseStyles(page),
                    children = page.navigableChildren().filter { !filterModules?.any { filtered -> it.name.contains(filtered) }!! }
            )

    /**
     * Parenthesis is applied in 1 case:
     *  - page only contains functions (therefore documentable from this page is [DFunction])
     */
    private fun ContentPage.displayableName(): String =
            if (this is WithDocumentables && documentables.all { it is DFunction }) {
                "$name()"
            } else {
                name
            }

    private fun chooseNavigationIcon(contentPage: ContentPage): NavigationNodeIcon? =
            if (contentPage is WithDocumentables) {
                val documentable = contentPage.documentables.firstOrNull()
                val isJava = documentable?.hasAnyJavaSources() ?: false

                when (documentable) {
                    is DTypeAlias -> NavigationNodeIcon.TYPEALIAS_KT
                    is DClass -> when {
                        documentable.isException -> NavigationNodeIcon.EXCEPTION
                        documentable.isAbstract() -> {
                            if (isJava) NavigationNodeIcon.ABSTRACT_CLASS else NavigationNodeIcon.ABSTRACT_CLASS_KT
                        }

                        else -> if (isJava) NavigationNodeIcon.CLASS else NavigationNodeIcon.CLASS_KT
                    }

                    is DFunction -> NavigationNodeIcon.FUNCTION
                    is DProperty -> {
                        val isVar = documentable.extra[IsVar] != null
                        if (isVar) NavigationNodeIcon.VAR else NavigationNodeIcon.VAL
                    }

                    is DInterface -> if (isJava) NavigationNodeIcon.INTERFACE else NavigationNodeIcon.INTERFACE_KT
                    is DEnum,
                    is DEnumEntry -> if (isJava) NavigationNodeIcon.ENUM_CLASS else NavigationNodeIcon.ENUM_CLASS_KT

                    is DAnnotation -> {
                        if (isJava) NavigationNodeIcon.ANNOTATION_CLASS else NavigationNodeIcon.ANNOTATION_CLASS_KT
                    }

                    is DObject -> NavigationNodeIcon.OBJECT
                    else -> null
                }
            } else {
                null
            }


    private fun DClass.isAbstract() =
            modifier.values.all { it is KotlinModifier.Abstract || it is JavaModifier.Abstract }

    private fun chooseStyles(page: ContentPage): Set<Style> =
            if (page.containsOnlyDeprecatedDocumentables()) setOf(TextStyle.Strikethrough) else emptySet()

    private fun ContentPage.containsOnlyDeprecatedDocumentables(): Boolean {
        if (this !is WithDocumentables) {
            return false
        }
        return this.documentables.isNotEmpty() && this.documentables.all { it.isDeprecatedForAllSourceSets() }
    }

    private fun Documentable.isDeprecatedForAllSourceSets(): Boolean {
        val sourceSetAnnotations = this.annotations()
        return sourceSetAnnotations.isNotEmpty() && sourceSetAnnotations.all { (_, annotations) ->
            annotations.any { it.isDeprecated() }
        }
    }

    private val navigationNodeOrder: Comparator<NavigationNode> =
            compareBy(canonicalAlphabeticalOrder, NavigationNode::name)

    private val navigationModuleNodeOrder: Comparator<NavigationNode> = navigationNodeOrder

    private fun ContentPage.navigableChildren() = when (this) {
        is ClasslikePage -> {

            this.navigableChildren()

        }

        is ModulePage -> {
            children
                    .filterIsInstance<ContentPage>()
                    .map(::visit)
                    .sortedWith(navigationModuleNodeOrder)
        }

        else -> {
            children
                    .filterIsInstance<ContentPage>()
                    .map(::visit)
                    .sortedWith(navigationNodeOrder)
        }
    }

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
}

