@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.chromia.rell.dokka.translator

import com.chromia.rell.dokka.config.RellConfig
import com.chromia.rell.dokka.systemlib.SystemLibVisitor
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

object RellSystemLibToDocumentableTranslator : SourceToDocumentableTranslator {

    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val module = RellConfig.SystemLibSourceSet.find(sourceSet)
                ?: throw IllegalArgumentException("Module not found for source set")
        return SystemLibVisitor(sourceSet, context.logger).run {
            DModule(
                    "Rell",
                    visitRellModule(module),
                    mapOf(sourceSet to DocumentationNode(listOf())),
                    sourceSets = setOf(sourceSet)
            )
        }
    }

    val NULL_DESCRIPTOR: DocumentableSource = DescriptorDocumentableSource(RellDeclarationDescriptor())
}
