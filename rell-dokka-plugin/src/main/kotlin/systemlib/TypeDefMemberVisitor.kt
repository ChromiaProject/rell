/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.deprecation.toAnnotation
import com.chromia.rell.dokka.doc.AliasDocTagProvider
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.dri.withAlias
import com.chromia.rell.dokka.dri.withSourceSet
import com.chromia.rell.dokka.model.*
import com.chromia.rell.dokka.translators.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.mtype.M_ParamArity
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.model.*
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
    fun List<L_TypeDefMember_Alias>.visitAliases(parent: DRI): List<Documentable> = map { it.visit(parent) }

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
                modifier = mapOf(),
                extra = PropertyContainer.withAll(IsPure.takeIf { constructor.pure })
        )
    }

    private fun L_TypeDefMember_Alias.visit(parent: DRI): Documentable {
        val dri = DRI.from(this, parent).withAlias()
        return when (val target = targetMember) {
            is L_TypeDefMember_Function -> target.visit(dri, simpleName.str, deprecated)
            else -> TODO("Alias type not implemented" + target.javaClass)
        }
    }

    private fun L_TypeDefMember_SpecialConstructor.visit(parent: DRI): DFunction {
        return when (parent.classNames) {
            "meta" -> metaTypeConstructor(this, sourceSet, parent)
            else -> TODO("Special type constructor for $parent is not implemented")
        }
    }

    private fun L_TypeDefMember_Function.visit(parent: DRI, alias: String? = null, deprecatedOverride: C_Deprecated? = null): DFunction {
        val dri = parent.copy(callable = Callable(
                name = alias ?: simpleName.str,
                params = function.header.params.map { JavaClassReference(it.type.strCode()) }
        ))
        val deprecated = deprecatedOverride ?: deprecated
        return DFunction(
                dri = dri,
                name = alias ?: simpleName.str,
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
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode(alias?.let { AliasDocTagProvider.aliasDocTag(DRI.from(this, parent), this.simpleName.str) })),
                modifier = mapOf(),
                extra = PropertyContainer.withAll(
                        IsFunction,
                        takeIf { function.flags.isStatic }?.let { IsStatic },
                        takeIf { function.flags.isPure }?.let { IsPure },
                        takeIf { deprecated != null }?.let {
                            Annotations(mapOf(sourceSet to listOf(deprecated!!.toAnnotation())))
                        }
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
        = makeDProperty(sourceSet, parent, docSymbol, constant.simpleName.str, constant.type, DefaultValue(mapOf(sourceSet to constant.value.toExpression())))
}
