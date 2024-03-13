@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.analysis.RellAnalysis
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.reflection.getFunctionExtensionsByReflection
import com.chromia.rell.dokka.reflection.getNameByReflection
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

class RellSourceToDocumentableTranslator(context: DokkaContext) : SourceToDocumentableTranslator {
    private val rellConfig = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)


    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val files = sourceSet.sourceRoots

        val rellAnalysis = RellAnalysis(files.first(), rellConfig?.modules)
        return RellModuleVisitor(sourceSet, context.logger, rellAnalysis).run {
            rellAnalysis.modules().map { visitRellModule(it) }
        }.let {
            DModule(
                    rellConfig?.name ?: "root",
                    it,
                    mapOf(sourceSet to DocumentationNode(listOf())),
                    sourceSets = setOf(sourceSet),
            )
        }
    }
}
