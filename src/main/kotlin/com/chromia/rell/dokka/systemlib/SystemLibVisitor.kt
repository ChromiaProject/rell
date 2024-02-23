package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.utilities.DokkaLogger

class SystemLibVisitor(
        val sourceSet: DokkaSourceSet,
        private val logger: DokkaLogger,
) {
    private val typeDefVisitor = TypeDefMemberVisitor(sourceSet, logger)

    fun visitRellModule(module: C_LibModule): DPackage {
        val packageName = module.lModule.moduleName.str()
        val dri = DRI(packageName = packageName)

        val doc = module.lModule.docSymbol.toDocumentationNode()
        val namespaceMembers = module.lModule.namespace.getAllDefs()

        val types = namespaceMembers.filterIsInstance<L_NamespaceMember_Type>().visitTypes(dri)

        return DPackage(
                dri = DRI(packageName),
                documentation = mapOf(sourceSet to doc),
                sourceSets = setOf(sourceSet),
                // Global constants
                properties = listOf(),
                // Entities/Structs/Objects
                classlikes = types,
                typealiases = listOf(),
                // Functions, queries, operations
                functions = listOf()
        )
    }

    private fun List<L_NamespaceMember_Type>.visitTypes(parent: DRI): List<DClass> = map { it.visit(parent) }

    private fun L_NamespaceMember_Type.visit(parent: DRI): DClass {
        val dri = parent.withClass(simpleName.str)
        val allTypeDefs = typeDef.members.all
        with(typeDefVisitor) {

            val constructors = allTypeDefs.filterIsInstance<L_TypeDefMember_Constructor>().visitConstructors(dri)
            val functions = allTypeDefs.filterIsInstance<L_TypeDefMember_Function>()
                    .filter { !it.function.flags.isStatic }
                    .visitFunctions(dri)
            val properties = allTypeDefs.filterIsInstance<L_TypeDefMember_Property>().visitProperties(dri)
            val constants = allTypeDefs.filterIsInstance<L_TypeDefMember_Constant>().visitConstants(dri)
            val staticFunctions = allTypeDefs.filterIsInstance<L_TypeDefMember_Function>().filter { it.function.flags.isStatic }.visitFunctions(dri)
            return DClass(
                    dri = dri,
                    name = simpleName.str,
                    documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                    constructors = constructors,
                    properties = properties + constants,
                    functions = functions + staticFunctions,
                    generics = listOf(),
                    classlikes = listOf(),
                    isExpectActual = false,
                    companion = null, //companionObject(dri, listOf(), staticFunctions),
                    expectPresentInSet = null,
                    visibility = mapOf(sourceSet to KotlinVisibility.Public),
                    supertypes = mapOf(sourceSet to listOf()), // TODO: add super types
                    sourceSets = setOf(sourceSet),
                    sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                    modifier = mapOf()
            )
        }
    }

    fun companionObject(parent: DRI, constants: List<DProperty>, staticFunctions: List<DFunction>): DObject? {
        if (constants.isEmpty() && staticFunctions.isEmpty()) return null
        val dri = parent.withClass("static")
        return DObject(
                name = null,
                dri = dri,

                visibility = mapOf(sourceSet to KotlinVisibility.Public),
                supertypes = mapOf(sourceSet to listOf()), // TODO: add super types
                sourceSets = setOf(sourceSet),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                documentation = mapOf(sourceSet to DocumentationNode(listOf())),
                isExpectActual = false,
                expectPresentInSet = null,
                classlikes = listOf(),
                functions = staticFunctions,
                properties = constants
                )
    }
}
