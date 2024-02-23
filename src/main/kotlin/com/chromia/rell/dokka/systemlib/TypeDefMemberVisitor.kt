package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_Constructor
import net.postchain.rell.base.lmodel.L_FunctionParam
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.*
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.TypeParam
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.utilities.DokkaLogger

class TypeDefMemberVisitor(
        val sourceSet: DokkaConfiguration.DokkaSourceSet,
        private val logger: DokkaLogger,
) {

    fun List<L_TypeDefMember_Constructor>.visitConstructors(parent: DRI): List<DFunction> = map { it.visit(parent) }
    fun List<L_TypeDefMember_Function>.visitFunctions(parent: DRI) = map { it.visit(parent) }
    fun List<L_TypeDefMember_Property>.visitProperties(parent: DRI): List<DProperty> = map { it.visit(parent) }
    fun List<L_TypeDefMember_Constant>.visitConstants(parent: DRI): List<DProperty> = map { it.visit(parent) }

    private fun L_TypeDefMember_Constructor.visit(parent: DRI): DFunction {
        val dri = parent.copy(callable = Callable.fromConstructor(constructor, this.docSymbol.symbolName.strCode()))

        return DFunction(
                dri = dri,
                name = dri.classNames!!,
                isConstructor = true,
                parameters = constructor.header.params.mapIndexed { index, p -> p.visit(dri, index) },
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = FunctionalTypeConstructor(dri, listOf()),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                modifier = mapOf()
        )
    }

    private fun Callable.Companion.fromConstructor(function: L_Constructor, name: String) = Callable(
            name = name,
            params = listOf(
                    TypeConstructor(
                            function.header.strCode(),
                            params = function.header.params.mapIndexed { index, p -> TypeParam(listOf()) }
                    )
            )
    )

    private fun L_TypeDefMember_Function.visit(parent: DRI): DFunction {
        val dri = parent.withClass(simpleName.str).copy(callable = Callable(
                name = simpleName.str,
                params = List(function.header.params.size) { index -> TypeParam(listOf()) }))

        return DFunction(
                dri = dri,
                name = this.simpleName.str,
                isConstructor = false,
                parameters = this.function.header.params.mapIndexed { index, p -> p.visit(dri, index) },
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = TypeParameter(parent.copy(classNames = function.header.resultType.strMsg()), function.header.resultType.strMsg()), // Return type
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                modifier = mapOf()
        )
    }

    private fun L_FunctionParam.visit(parent: DRI, index: Int): DParameter {
        val dri = parent.copy(target = PointingToCallableParameters(index))
        return DParameter(
                dri = dri,
                name = name.str,
                documentation = mapOf(
                        sourceSet to DocumentationNode(listOf(Description(P(listOf(Text(docSymbol.comment?.description ?: "Parameter $name"))))))
                ),
                // Adds a link of the type to the definition
                type = TypeParameter(dri = DRI(parent.packageName, type.toString()), name = this.type.toString()),
                sourceSets = setOf(sourceSet),
                expectPresentInSet = null
        )
    }

    private fun L_TypeDefMember_Property.visit(parent: DRI): DProperty {
        val dri = parent.withClass(simpleName.str)
        val type = this.property.type.toString()

        return DProperty(
                dri = dri,
                name = simpleName.str,
                isExpectActual = false,
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                expectPresentInSet = null,
                sourceSets = setOf(sourceSet),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                type = TypeParameter(dri = DRI(parent.packageName, type), name = type),
                generics = listOf(),
                modifier = mapOf(),
                visibility = mapOf(),
                receiver = null,
                setter = null,
                getter = null,
        )
    }

    private fun L_TypeDefMember_Constant.visit(parent: DRI): DProperty {
        val dri = parent.withClass(constant.simpleName.str)
        val type = this.constant.type.toString()

        return DProperty(
                dri = dri,
                name = constant.simpleName.str,
                isExpectActual = false,
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                expectPresentInSet = null,
                sourceSets = setOf(sourceSet),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                type = TypeParameter(dri = DRI(parent.packageName, type), name = type),
                generics = listOf(),
                modifier = mapOf(),
                visibility = mapOf(),
                receiver = null,
                setter = null,
                getter = null,
        )
    }
}
