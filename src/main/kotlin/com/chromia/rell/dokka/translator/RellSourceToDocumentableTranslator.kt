@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.translator

import com.chromia.rell.dokka.model.definitionsByModule
import com.chromia.rell.dokka.model.toDRI
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_OperationDefinition
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

object RellSourceToDocumentableTranslator : SourceToDocumentableTranslator {

    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val files = sourceSet.sourceRoots
        val config = RellApiCompile.Config.Builder()
                .mountConflictError(false)
                .moduleArgsMissingError(false)
                .build()
        val app = RellApiCompile.compileApp(config, files.first(), null)
        //app.operations
        app.definitionsByModule()
        println(files.map { it.name })
        println(app.operations)
        return DModule(
                context.configuration.moduleName,
                listOf(operationsToPackage(app.operations, sourceSet)),
                mapOf(sourceSet to DocumentationNode(listOf())),
                sourceSets = setOf(sourceSet)
        )
    }

    private fun R_App.packages(sourceSet: DokkaConfiguration.DokkaSourceSet): List<DPackage> {
        val defs = definitionsByModule()
        return defs.map { (m, rellModule) ->
            DPackage(
                    dri = m.toDRI(),
                    documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text(m.docSymbol.comment!!.toString()))))),
                    sourceSets = setOf(sourceSet),
                    properties = listOf(), // Global constants
                    classlikes = listOf(), // Entities/Structs/Objects
                    typealiases = listOf(),
                    // Functions, queries, operations
                    functions = listOf()
            )

        }
    }

    private fun operationsToPackage(operations: Map<R_MountName, R_OperationDefinition>, sourceSet: DokkaConfiguration.DokkaSourceSet): DPackage {
        return DPackage(
                dri = DRI("rell"),
                documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("Hello"))))),
                sourceSets = setOf(sourceSet),
                properties = listOf(), // Global constants
                classlikes = listOf(), // Entities/Structs/Objects
                typealiases = listOf(),
                // Functions, queries, operations
                functions = operations.map {

                    it.value

                    DFunction(
                            dri = DRI(packageName = it.key.parts.dropLast(1).joinToString("."),
                                    classNames = it.value.appLevelName),
                            name = it.key.str(),
                            isConstructor = false,
                            generics = listOf(),
                            sourceSets = setOf(sourceSet),
                            documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("Hi!"))))),
                            modifier = mapOf(),
                            isExpectActual = true, // Operation
                            visibility = mapOf(),
                            expectPresentInSet = null,
                            receiver = null,
                            sources = mapOf(/*sourceSet to object : DocumentableSource {
                                override val path: String
                                    get() = "main.rell"

                                override fun computeLineNumber(): Int? {
                                    return null
                                }
                            },*/
                                  sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())  ),
                            type = Dynamic,
                            parameters = it.value.params().map { p ->
                                DParameter(
                                        DRI(),
                                        p.name.str,
                                        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("Param ${p.name}"))))),
                                        expectPresentInSet = null,
                                        type = TypeParameter(DRI(), p.type.name),
                                        sourceSets = setOf(sourceSet)
                                )
                            }
                    )
                },

        )
    }
}

