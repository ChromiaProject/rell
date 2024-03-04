package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.DriOfRoot
import com.chromia.rell.dokka.dri.toDRI
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_NamespaceMember_SpecialFunction
import net.postchain.rell.base.model.R_QualifiedName
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound

fun existsAndEmptySpecialFunctions(f: L_NamespaceMember_SpecialFunction, sourceSet: DokkaConfiguration.DokkaSourceSet): DFunction {
    val dri = DriOfRoot.copy(callable = Callable(f.simpleName.str, params = listOf(TypeConstructor("T", listOf()))))

    val param = DParameter(
            dri.copy(target = PointingToCallableParameters(0)),
            name = "arg",
            documentation = mapOf(),
            expectPresentInSet = null,
            sourceSets = setOf(sourceSet),
            type = Nullable(UnresolvedBound("T"))
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
            generics = listOf(),
            sources = mapOf(sourceSet to NULL_DESCRIPTOR),
            documentation = mapOf(sourceSet to f.docSymbol.toDocumentationNode()),
            modifier = mapOf(),
    )
}
