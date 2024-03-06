package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.dri.withSourceSet
import com.chromia.rell.dokka.model.IsPure
import com.chromia.rell.dokka.model.IsStatic
import com.chromia.rell.dokka.model.IsVararg
import com.chromia.rell.dokka.model.IsZeroOne
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
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
import org.jetbrains.dokka.model.TypeParameter
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
        val dri = DRI.from(this, parent)

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
        return when (parent.classNames) {
            "meta" -> metaTypeConstructor(this, sourceSet, parent)
            else -> TODO("Special type constructor for $parent is not implemented")
        }
    }

    private fun L_TypeDefMember_Function.visit(parent: DRI): DFunction {
        val dri = DRI.from(this, parent)

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
                generics = function.header.typeParams.toGenerics(dri.withSourceSet(sourceSet)),
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
