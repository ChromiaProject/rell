@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.expr.R_ExtendableFunctionUid
import net.postchain.rell.base.model.expr.R_FunctionExtensions
import net.postchain.rell.base.model.expr.R_FunctionExtensionsTable
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class RellSourceToDocumentableTranslator(context: DokkaContext) : SourceToDocumentableTranslator {
    private val rellConfig = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)


    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val files = sourceSet.sourceRoots
        val config = RellApiCompile.Config.Builder()
                .mountConflictError(false)
                .moduleArgsMissingError(false)
                .build()
        val app = RellApiCompile.compileApp(config, files.first(), rellConfig?.modules)

        val functionExtensions = getFunctionExtensionsByReflection(app)
        return RellModuleVisitor(sourceSet, context.logger, functionExtensions).run {
            app.modules.map { visitRellModule(it) }
        }.let {
            DModule(
                    rellConfig?.name ?: "root",
                    it,
                    mapOf(sourceSet to DocumentationNode(listOf())),
                    sourceSets = setOf(sourceSet),
            )
        }
    }

    private fun getFunctionExtensionsByReflection(app: R_App) =
            R_FunctionExtensionsTable::class.memberProperties.find { it.name == "list" }!!.let {
                it.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (it.get(app.functionExtensions) as List<R_FunctionExtensions>)
            }.associate { extension ->
                R_ExtendableFunctionUid::class.memberProperties.find { it.name == "name" }!!.let {
                    it.isAccessible = true
                    (it.get(extension.uid) as String) to extension.extensions
                }
            }
}
