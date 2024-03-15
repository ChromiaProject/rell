package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.DRIWithSourceSet
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.dri.toProjection
import com.chromia.rell.dokka.translators.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.utils.doc.DocSymbol
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeParameter
import org.jetbrains.dokka.model.DefaultValue
import org.jetbrains.dokka.model.Invariance
import org.jetbrains.dokka.model.Star
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.Variance
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.properties.PropertyContainer

fun makeDProperty(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, docSymbol: DocSymbol, name: String, type: M_Type, defaultValue: DefaultValue? = null) =
        DProperty(
                dri = parent.withClass(name),
                name = name,
                isExpectActual = false,
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),
                expectPresentInSet = null,
                sourceSets = setOf(sourceSet),
                sources = mapOf(sourceSet to NULL_DESCRIPTOR),
                type = type.toBound(),
                generics = listOf(),
                modifier = mapOf(),
                visibility = mapOf(),
                receiver = null,
                setter = null,
                getter = null,
                extra = PropertyContainer.withAll(defaultValue)
        )

fun makeDarameter(sourceSet: DokkaConfiguration.DokkaSourceSet,
                  parent: DRI,
                  docSymbol: DocSymbol?,
                  name: String,
                  type: Bound,
                  index: Int,
                  ) =
        DParameter(
                dri = parent.copy(target = PointingToCallableParameters(index)),
                name = name,
                documentation = docSymbol?.let { mapOf(sourceSet to docSymbol.toDocumentationNode()) } ?: mapOf(),
                expectPresentInSet = null,
                sourceSets = setOf(sourceSet),
                type = type,
        )

fun List<M_TypeParam>.toGenerics(dri: DRIWithSourceSet) = mapNotNull {
    val projection = it.bounds.toProjection()
    if (projection is Star)
        DTypeParameter(
                dri.dri,
                name = it.name,
                presentableName = null,
                documentation = mapOf(dri.sourceSet to DocumentationNode(listOf())),
                expectPresentInSet = null,
                sourceSets = setOf(dri.sourceSet),
                bounds = listOf(it.bounds.canonicalOutType().toBound())
        )
        else
    DTypeParameter(
            it.bounds.toProjection(it.name) as Variance<TypeParameter>,
            documentation = mapOf(dri.sourceSet to DocumentationNode(listOf())),
            expectPresentInSet = null,
            sourceSets = setOf(dri.sourceSet),
            bounds = listOf(it.bounds.canonicalOutType().toBound())
    )
}
