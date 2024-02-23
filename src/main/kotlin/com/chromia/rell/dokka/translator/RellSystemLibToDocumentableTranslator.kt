@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.translator

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellConfig
import com.chromia.rell.dokka.systemlib.SystemLibVisitor
import kotlinx.serialization.json.Json
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

object RellSystemLibToDocumentableTranslator : SourceToDocumentableTranslator {

    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val pluginConfig = context.configuration.pluginsConfiguration.find { it.fqPluginName == RellDokkaPlugin::class.qualifiedName }
        val rellConfig = pluginConfig?.let {
            Json.decodeFromString<RellConfig>(it.values)
        }
        SystemLibVisitor(sourceSet, context.logger).let {

        return DModule(
                "Rell API Documentation",
                it.visitRellModule(Lib_Rell.MODULE) + it.visitRellModule(Lib_RellTest.MODULE),
                mapOf(sourceSet to DocumentationNode(listOf())),
                sourceSets = setOf(sourceSet)
        )
        }
    }

    val NULL_DESCRIPTOR: DocumentableSource = DescriptorDocumentableSource(RellDeclarationDescriptor())
}
