@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.analysis.RellAnalysis
import com.chromia.rell.dokka.config.HiddenPackagesRegistry
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import net.postchain.rell.base.model.R_Module
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

class RellSourceToDocumentableTranslator(context: DokkaContext) : SourceToDocumentableTranslator {
    private val rellConfig = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)

    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val rellAnalysis = RellAnalysis(sourceSet.sourceRoots.first(), rellConfig?.modules, rellConfig?.additionalModules)

        val scopeId = sourceSet.sourceSetID.scopeId
        val isTestSource = scopeId == "test"
        val isMainModule = scopeId == "main"

        val packages = when {
            isTestSource -> rellModuleVisitor(sourceSet, context, rellAnalysis) { it.testModules() }
            isMainModule -> rellModuleVisitor(sourceSet, context, rellAnalysis) { it.modules() }
            else -> emptyList()
        }

        HiddenPackagesRegistry.hide(rellAnalysis.hiddenPackages())

        val moduleName = rellConfig?.name ?: "root"

        return DModule(
                moduleName,
                packages,
                mapOf(sourceSet to DocumentationNode(listOf())),
                sourceSets = setOf(sourceSet),
        )
    }

    private fun rellModuleVisitor(
            sourceSet: DokkaConfiguration.DokkaSourceSet,
            context: DokkaContext,
            rellAnalysis: RellAnalysis,
            moduleFilter: (RellAnalysis) -> List<R_Module>,
    ): List<DPackage> {
        return RellModuleVisitor(sourceSet, context.logger, rellAnalysis).run {
            moduleFilter(rellAnalysis).flatMap { visitRellModule(it) }
        }
    }
}
