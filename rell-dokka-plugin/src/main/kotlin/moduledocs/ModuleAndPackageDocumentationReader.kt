
/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package com.chromia.rell.dokka.moduledocs

import net.postchain.rell.base.utils.checkEquals
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.kotlin.internal.ModuleAndPackageDocumentationReader
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.SourceSetDependent
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.model.doc.Deprecated
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.utilities.associateWithNotNull

/**
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/subprojects/analysis-kotlin-symbols/src/main/kotlin/org/jetbrains/dokka/analysis/kotlin/symbols/kdoc/moduledocs/ModuleAndPackageDocumentationReader.kt
 * This is due to its internal dependency on SymbolsAnalysisPlugin which we have removed here
 */
class RellModuleAndPackageDocumentationReader(
        private val context: DokkaContext
) : ModuleAndPackageDocumentationReader {

    //private val kotlinAnalysis = context.plugin<SymbolsAnalysisPlugin>().querySingle { kotlinAnalysis }

    private val documentationFragments: SourceSetDependent<List<ModuleAndPackageDocumentationFragment>> =
            context.configuration.sourceSets.associateWith { sourceSet ->
                sourceSet.includes.flatMap { include -> parseModuleAndPackageDocumentationFragments(include) }
            }

    private fun findDocumentationNodes(
            sourceSets: Set<DokkaConfiguration.DokkaSourceSet>,
            predicate: (ModuleAndPackageDocumentationFragment) -> Boolean
    ): SourceSetDependent<DocumentationNode> {
        return sourceSets.associateWithNotNull { sourceSet ->
            val fragments = documentationFragments[sourceSet].orEmpty().filter(predicate)
            //kotlinAnalysis?.getModule(sourceSet)// test: to throw exception for unknown sourceSet
            val documentations = fragments.map { fragment ->
                parseModuleAndPackageDocumentation(
                        context = ModuleAndPackageDocumentationParsingContext(context.logger, sourceSet),
                        fragment = fragment
                )
            }
            when (documentations.size) {
                0 -> null
                1 -> documentations.single().documentation
                else -> DocumentationNode(documentations.flatMap { it.documentation.children }
                        .mergeDocumentationNodes())
            }
        }
    }

    private val ModuleAndPackageDocumentationFragment.canonicalPackageName: String
        get() {
            checkEquals(classifier, ModuleAndPackageDocumentation.Classifier.Package)
            if (name == "[root]") return ""
            return name
        }

    override fun read(module: DModule): SourceSetDependent<DocumentationNode> {
        return findDocumentationNodes(module.sourceSets) { fragment ->
            fragment.classifier == ModuleAndPackageDocumentation.Classifier.Module && (fragment.name == module.name)
        }
    }

    override fun read(pkg: DPackage): SourceSetDependent<DocumentationNode> {
        return findDocumentationNodes(pkg.sourceSets) { fragment ->
            fragment.classifier == ModuleAndPackageDocumentation.Classifier.Package && fragment.canonicalPackageName == pkg.dri.packageName
        }
    }

    override fun read(module: DokkaConfiguration.DokkaModuleDescription): DocumentationNode? {
        val parsingContext = ModuleAndPackageDocumentationParsingContext(context.logger)

        val documentationFragment = module.includes
                .flatMap { include -> parseModuleAndPackageDocumentationFragments(include) }
                .firstOrNull { fragment -> fragment.classifier == ModuleAndPackageDocumentation.Classifier.Module && fragment.name == module.name }
                ?: return null

        val moduleDocumentation = parseModuleAndPackageDocumentation(parsingContext, documentationFragment)
        return moduleDocumentation.documentation
    }

    private fun List<TagWrapper>.mergeDocumentationNodes(): List<TagWrapper> =
            groupBy { it::class }.values.map {
                it.reduce { acc, tagWrapper ->
                    val newRoot = CustomDocTag(
                            acc.children + tagWrapper.children,
                            name = (tagWrapper as? NamedTagWrapper)?.name.orEmpty()
                    )
                    @Suppress("RemoveRedundantQualifierName")
                    when (acc) {
                        is See -> acc.copy(root = newRoot)
                        is Param -> acc.copy(root = newRoot)
                        is Throws -> acc.copy(root = newRoot)
                        is Sample -> acc.copy(root = newRoot)
                        is Property -> acc.copy(root = newRoot)
                        is CustomTagWrapper -> acc.copy(root = newRoot)
                        is Description -> acc.copy(root = newRoot)
                        is Author -> acc.copy(root = newRoot)
                        is Version -> acc.copy(root = newRoot)
                        is Since -> acc.copy(root = newRoot)
                        is Return -> acc.copy(root = newRoot)
                        is Receiver -> acc.copy(root = newRoot)
                        is Constructor -> acc.copy(root = newRoot)
                        is Deprecated -> acc.copy(root = newRoot)
                        is org.jetbrains.dokka.model.doc.Suppress -> acc.copy(root = newRoot)
                    }
                }
            }
}
