package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.TypeParam
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.TypeParameter

fun L_TypeDefMember_Constructor.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI): DFunction {
    val dri = parent.copy(classNames = parent.classNames + this.strCode(), callable = Callable(
            "constructor",
            null,
            List(constructor.header.params.size) { i -> TypeParam(listOf()) })
    )

    return DFunction(
            dri = dri,
            name = parent.classNames!!,
            isConstructor = true,
            parameters = this.constructor.header.params.mapIndexed { index, p -> p.toDParam(sourceSet, dri, index) },
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

fun L_TypeDefMember_Function.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI): DFunction {
    val dri = parent.copy(classNames = parent.classNames + this.strCode(), callable = Callable(
            simpleName.str,
            null,
            List(function.header.params.size) { i -> TypeParam(listOf()) })
    )

    return DFunction(
            dri = dri,
            name = this.simpleName.str,
            isConstructor = false,
            parameters = this.function.header.params.mapIndexed { index, p -> p.toDParam(sourceSet, dri, index) },
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
