/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.DriOfRoot
import com.chromia.rell.dokka.dri.toDRI
import com.chromia.rell.dokka.model.IsFunction
import com.chromia.rell.dokka.translators.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_NamespaceMember_SpecialFunction
import net.postchain.rell.base.lmodel.L_TypeDefMember_SpecialConstructor
import net.postchain.rell.base.model.R_QualifiedName
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DTypeParameter
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.Void
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.properties.PropertyContainer

fun existsAndEmptySpecialFunctions(f: L_NamespaceMember_SpecialFunction, sourceSet: DokkaConfiguration.DokkaSourceSet): DFunction {
    val dri = DriOfRoot.copy(callable = Callable(f.simpleName.str, params = listOf(TypeConstructor("T", listOf()))))

    val param = DParameter(
            dri.copy(target = PointingToCallableParameters(0)),
            name = "arg",
            documentation = mapOf(),
            expectPresentInSet = null,
            sourceSets = setOf(sourceSet),
            type = Nullable(TypeParameter(dri, "T"))
    )
    return DFunction(
            dri,
            name = f.simpleName.str,
            parameters = listOf(param),
            isConstructor = false,
            expectPresentInSet = null,
            visibility = mapOf(),
            receiver = null,
            isExpectActual = false,
            type = TypeParameter(R_QualifiedName.of("boolean").toDRI(), "boolean"),
            sourceSets = setOf(sourceSet),
            generics = listOf(DTypeParameter(
                    dri = dri,
                    name = "T",
                    presentableName = null,
                    documentation = mapOf(sourceSet to DocumentationNode(listOf())),
                    expectPresentInSet = null,
                    sourceSets = setOf(sourceSet),
                    bounds = listOf() // TODO: collection, maps and nullable
            )),
            sources = mapOf(sourceSet to NULL_DESCRIPTOR),
            documentation = mapOf(sourceSet to f.docSymbol.toDocumentationNode()),
            modifier = mapOf(),
            extra = PropertyContainer.withAll(IsFunction)
    )
}

fun metaTypeConstructor(c: L_TypeDefMember_SpecialConstructor, sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI): DFunction {
    val dri = parent.copy(
            callable = Callable(
                    c.docSymbol.symbolName.strCode(),
                    params = listOf(TypeConstructor("T", listOf()))
            )
    )

    val param = DParameter(
            dri = dri.copy(target = PointingToCallableParameters(0)),
            name = "type",
            documentation = mapOf(),
            expectPresentInSet = null,
            sourceSets = setOf(sourceSet),
            type = TypeParameter(dri, "T")
    )

    return DFunction(
            dri = dri,
            name = dri.classNames!!,
            isConstructor = true,
            parameters = listOf(param),
            expectPresentInSet = null,
            visibility = mapOf(),
            receiver = null,
            isExpectActual = false,
            type = Void,
            sourceSets = setOf(sourceSet),
            generics = listOf(DTypeParameter(
                    dri = dri,
                    name = "T",
                    presentableName = null,
                    documentation = mapOf(sourceSet to DocumentationNode(listOf())),
                    expectPresentInSet = null,
                    sourceSets = setOf(sourceSet),
                    bounds = listOf()
            )),
            sources = mapOf(sourceSet to NULL_DESCRIPTOR),
            documentation = mapOf(sourceSet to c.docSymbol.toDocumentationNode()),
            modifier = mapOf(),
            extra = PropertyContainer.withAll(IsFunction)
    )
}
