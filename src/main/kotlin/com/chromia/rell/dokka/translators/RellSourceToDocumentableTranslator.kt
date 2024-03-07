@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.model.definitionsByModule
import com.chromia.rell.dokka.model.toDClasslike
import com.chromia.rell.dokka.model.toDFunction
import com.chromia.rell.dokka.model.toDProperty
import com.chromia.rell.dokka.model.toDRI
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

class RellSourceToDocumentableTranslator(context: DokkaContext) : SourceToDocumentableTranslator {
    private val rellConfig = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)


    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val files = sourceSet.sourceRoots
        val config = RellApiCompile.Config.Builder()
                .mountConflictError(false)
                .moduleArgsMissingError(false)
                .build()
        val app = RellApiCompile.compileApp(config, files.first(), rellConfig?.modules)
        return DModule(
                rellConfig?.name ?: "root",
                app.packages(sourceSet),
                mapOf(sourceSet to DocumentationNode(listOf())),
                sourceSets = setOf(sourceSet)
        )
    }

    private fun R_App.packages(sourceSet: DokkaConfiguration.DokkaSourceSet): List<DPackage> {
        val defs = definitionsByModule()
        return defs.map { (m, rellModule) ->
            DPackage(
                    dri = m.toDRI(),
                    documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text(m.docSymbol.comment?.toString() ?: ""))))),
                    sourceSets = setOf(sourceSet),
                    properties = rellModule.constants.map { it.toDProperty(sourceSet) }, // Global constants
                    // Entities/Structs/Objects
                    classlikes = rellModule.entities.map { it.toDClasslike(sourceSet) }
                    + rellModule.objects.map { it.toDClasslike(sourceSet) }
                    + rellModule.structs.map { it.toDClasslike(sourceSet) }
                    + rellModule.enums.map { it.toDClasslike(sourceSet) },
                    typealiases = listOf(),
                    // Functions, queries, operations
                    functions = rellModule.functions.map { it.toDFunction(sourceSet) }
                            + rellModule.queries.map { it.toDFunction(sourceSet) }
                            + rellModule.operations.map { it.toDFunction(sourceSet) }
            )
        }
    }
}
