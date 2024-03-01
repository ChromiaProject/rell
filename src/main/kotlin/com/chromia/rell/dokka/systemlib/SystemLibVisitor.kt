package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.config.RellModule
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.DriOfRoot
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.dri.toDRI
import com.chromia.rell.dokka.dri.withAlias
import com.chromia.rell.dokka.model.IsVararg
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_FunctionParam
import net.postchain.rell.base.lmodel.L_NamespaceMember_Alias
import net.postchain.rell.base.lmodel.L_NamespaceMember_Constant
import net.postchain.rell.base.lmodel.L_NamespaceMember_Function
import net.postchain.rell.base.lmodel.L_NamespaceMember_Namespace
import net.postchain.rell.base.lmodel.L_NamespaceMember_Property
import net.postchain.rell.base.lmodel.L_NamespaceMember_Struct
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import net.postchain.rell.base.lmodel.L_TypeDefMember_SpecialConstructor
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.TypeParam
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.utilities.DokkaLogger

class SystemLibVisitor(
        val sourceSet: DokkaSourceSet,
        private val logger: DokkaLogger,
) {
    private val typeDefVisitor = TypeDefMemberVisitor(sourceSet, logger)

    companion object {
        val blacklistedTypes = listOf("guid", "signer")
        val blacklistedNamespaces = listOf("")
    }

    fun visitRellModule(rellModule: RellModule): List<DPackage> {
        val dri = rellModule.dri
        val module = rellModule.module

        val doc = module.lModule.docSymbol.toDocumentationNode()
        val namespaceMembers = module.lModule.namespace.members

        val types = namespaceMembers.filterIsInstance<L_NamespaceMember_Type>()
                .filterNot { it.typeDef.abstract }
                .filterNot { it.typeDef.hidden }
                .filterNot { blacklistedTypes.contains(it.simpleName.str) }
                .visitTypes(dri)
        val structs = namespaceMembers.filterIsInstance<L_NamespaceMember_Struct>()
                .visitStructs(dri)
        val functions = namespaceMembers.filterIsInstance<L_NamespaceMember_Function>()
                .visitFunctions(dri)
        val properties = namespaceMembers.filterIsInstance<L_NamespaceMember_Property>()
                .visitProperties(dri)
        val alias = namespaceMembers.filterIsInstance<L_NamespaceMember_Alias>()
                .visitAliases(dri)
        val namespaces = module.lModule.namespace.getAllDefs().filterIsInstance<L_NamespaceMember_Namespace>()
                .filterNot { blacklistedNamespaces.contains(it.simpleName.str) }
                .visitNamespaces(dri)
                .filterNot { it.children.isEmpty() }

        val basePackage = DPackage(
                dri = dri,
                documentation = mapOf(sourceSet to doc),
                sourceSets = setOf(sourceSet),
                // Global constants
                properties = properties + alias.filterIsInstance<DProperty>(),
                // Entities/Structs/Objects
                classlikes = types + structs,
                typealiases = listOf(),
                // Functions, queries, operations
                functions = functions
        )

        val aliasPackage = DPackage(
                dri = DriOfRoot,
                documentation = mapOf(),
                sourceSets = setOf(sourceSet),
                classlikes = listOf(),
                properties = listOf(),
                typealiases = listOf(),
                functions = alias.filterIsInstance<DFunction>(),
        )
        return namespaces + basePackage + aliasPackage
    }

    private fun List<L_NamespaceMember_Type>.visitTypes(parent: DRI): List<DClass> = map { it.visit(parent) }

    private fun L_NamespaceMember_Type.visit(parent: DRI): DClass {
        val dri = parent.withClass(simpleName.str)
        val allTypeDefs = typeDef.members.all
        with(typeDefVisitor) {

            val specialConstructors = allTypeDefs.filterIsInstance<L_TypeDefMember_SpecialConstructor>().visitSpecialConstructors(dri)
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
                    constructors = constructors + specialConstructors,
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

    private fun List<L_NamespaceMember_Struct>.visitStructs(parent: DRI): List<DClass> = map { it.visit(parent) }

    private fun L_NamespaceMember_Struct.visit(parent: DRI): DClass {
        val dri = parent.withClass(simpleName.str)
        return DClass(
                dri = dri,
                name = simpleName.str,
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                constructors = listOf(),
                properties = struct.attributesMap.map { makeDProperty(sourceSet, dri, it.value.docSymbol, it.key, it.value.type) },
                functions = listOf(),
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

private fun List<L_NamespaceMember_Namespace>.visitNamespaces(parent: DRI) = map { it.visit(parent) }

private fun L_NamespaceMember_Namespace.visit(parent: DRI): DPackage {
    val dri = DRI(qualifiedName.str())
    val members = namespace.members

    val types = members.filterIsInstance<L_NamespaceMember_Type>().visitTypes(dri)
    val structs = members.filterIsInstance<L_NamespaceMember_Struct>().visitStructs(dri)
    val functions = members.filterIsInstance<L_NamespaceMember_Function>().visitFunctions(dri)
    val properties = members.filterIsInstance<L_NamespaceMember_Property>().visitProperties(dri)
    val constants = members.filterIsInstance<L_NamespaceMember_Constant>().visitConstants(dri)

    return DPackage(
            dri = dri,
            documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
            classlikes = types + structs,
            properties = properties + constants,
            functions = functions,
            sourceSets = setOf(sourceSet),
            typealiases = listOf()
    )
}

private fun List<L_NamespaceMember_Alias>.visitAliases(parent: DRI): List<Documentable> = map { it.visit(parent) }
private fun L_NamespaceMember_Alias.visit(parent: DRI): Documentable {

    val dri = DriOfRoot.withClass(simpleName.str).withAlias()
    val target = finalTargetMember
    return when (target) {
        is L_NamespaceMember_Type -> target.visit(dri)
        is L_NamespaceMember_Function -> target.visit(dri)
        else -> TODO("Alias type not implemented")
    }
}

private fun List<L_NamespaceMember_Function>.visitFunctions(parent: DRI) = map { it.visit(parent) }

private fun L_NamespaceMember_Function.visit(parent: DRI): DFunction {
    val dri = parent.withClass(simpleName.str)

    return DFunction(
            dri = dri,
            name = parent.classNames ?: simpleName.str,
            isConstructor = false,
            parameters = function.header.params.mapIndexed { index, p -> p.visit(dri, index) },
            expectPresentInSet = null,
            visibility = mapOf(),
            receiver = null,
            isExpectActual = false,
            type = function.header.resultType.toBound(),
            sourceSets = setOf(sourceSet),
            generics = listOf(),
            sources = NULL_DESCRIPTOR.toSourceSetDependent(),
            documentation = docSymbol.toDocumentationNode().toSourceSetDependent(),
            modifier = mapOf()
    )
}

private fun L_FunctionParam.visit(parent: DRI, index: Int): DParameter {
    val dri = parent.copy(target = PointingToCallableParameters(index))
    return DParameter(
            dri = dri,
            name = name.str,
            documentation = mapOf(
                    sourceSet to DocumentationNode(listOf(Description(P(listOf(Text(docSymbol.comment?.description
                            ?: "Parameter $name"))))))
            ),
            // Adds a link of the type to the definition
            type = type.toBound(),
            sourceSets = setOf(sourceSet),
            expectPresentInSet = null,
            extra = PropertyContainer.withAll(
                    takeIf { arity.many }?.let { IsVararg }
            )
    )
}

private fun List<L_NamespaceMember_Property>.visitProperties(parent: DRI): List<DProperty> = map { it.visit(parent) }
private fun List<L_NamespaceMember_Constant>.visitConstants(parent: DRI): List<DProperty> = map { it.visit(parent) }

private fun L_NamespaceMember_Property.visit(parent: DRI) = makeDProperty(sourceSet, parent, docSymbol, simpleName.str, property.type)

private fun L_NamespaceMember_Constant.visit(parent: DRI) = makeDProperty(sourceSet, parent, docSymbol, simpleName.str, constant.type)

private fun <T> T.toSourceSetDependent() = if (this != null) mapOf(sourceSet to this) else mapOf()
}

