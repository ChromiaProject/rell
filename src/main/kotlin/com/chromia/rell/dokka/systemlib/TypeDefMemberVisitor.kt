package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.dri.toDRI
import com.chromia.rell.dokka.model.IsPure
import com.chromia.rell.dokka.model.IsStatic
import com.chromia.rell.dokka.model.IsVararg
import com.chromia.rell.dokka.model.IsZeroOne
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_Constructor
import net.postchain.rell.base.lmodel.L_FunctionParam
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import net.postchain.rell.base.lmodel.L_TypeDefMember_SpecialConstructor
import net.postchain.rell.base.mtype.M_ParamArity
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.*
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.TypeParam
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeParameter
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.utilities.DokkaLogger

class TypeDefMemberVisitor(
        val sourceSet: DokkaConfiguration.DokkaSourceSet,
        private val logger: DokkaLogger,
) {

    fun List<L_TypeDefMember_Constructor>.visitConstructors(parent: DRI): List<DFunction> = map { it.visit(parent) }
    fun List<L_TypeDefMember_SpecialConstructor>.visitSpecialConstructors(parent: DRI): List<DFunction> = map { it.visit(parent) }
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
                type = TypeParameter(dri, dri.classNames!!),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                modifier = mapOf()
        )
    }

    private fun L_TypeDefMember_SpecialConstructor.visit(parent: DRI): DFunction {
        if (parent.classNames == "meta") return metaTypeConstructor(this, sourceSet, parent)
        val dri = parent.copy(callable = Callable(docSymbol.symbolName.strCode(), params = List(this.fn.paramCount()?.max()
                ?: 0) { TypeParam(listOf()) }))

        return DFunction(
                dri = dri,
                name = dri.classNames!!,
                isConstructor = true,
                parameters = fn.paramCount()?.map {
                    DParameter(
                            dri = dri.copy(target = PointingToCallableParameters(it)),
                            name = "arg",
                            documentation = mapOf(),
                            expectPresentInSet = null,
                            sourceSets = setOf(sourceSet),
                            type = UnresolvedBound("T")
                    )
                } ?: listOf(),
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = TypeParameter(dri.parent, dri.parent.classNames!!),
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
                            params = function.header.params.map { JavaClassReference(it.type.strCode()) }
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
                type = function.header.resultType.toBound(), // Return type
                sourceSets = setOf(sourceSet),
                generics = function.header.typeParams.map { DTypeParameter(
                        dri = DRI(classNames = it.name),
                        name = it.name,
                        presentableName = null,
                        documentation = mapOf(sourceSet to DocumentationNode(listOf())),
                        expectPresentInSet = null,
                        sourceSets = setOf(sourceSet),
                        bounds = listOf()
                ) },
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                modifier = mapOf(),
                extra = PropertyContainer.withAll(
                        takeIf { function.flags.isStatic }?.let { IsStatic },
                        takeIf { function.flags.isPure }?.let { IsPure }
                )
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
                        takeIf { arity == M_ParamArity.ZERO_ONE }?.let { IsZeroOne },
                        takeIf { arity.many }?.let { IsVararg }
                )
        )
    }

    private fun L_TypeDefMember_Property.visit(parent: DRI)
        = makeDProperty(sourceSet, parent, docSymbol, simpleName.str, property.type)

    private fun L_TypeDefMember_Constant.visit(parent: DRI)
        = makeDProperty(sourceSet, parent, docSymbol, constant.simpleName.str, constant.type)
}
