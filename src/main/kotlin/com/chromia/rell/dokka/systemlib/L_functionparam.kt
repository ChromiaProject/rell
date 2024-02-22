package com.chromia.rell.dokka.systemlib

import net.postchain.rell.base.lmodel.L_FunctionParam
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text

fun L_FunctionParam.toDParam(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, index: Int): DParameter {
    val dri = parent.copy(classNames = this.name.str, target = PointingToCallableParameters(index)) // TODO: Link to actual type
    return DParameter(
            dri = dri,
            name = name.str,
            documentation = mapOf(
                    sourceSet to DocumentationNode(listOf(Description(Text(docSymbol.comment?.description ?: "Parameter $name"))))
            ),
            type = TypeParameter(dri = dri, name = this.type.toString()),
            sourceSets = setOf(sourceSet),
            expectPresentInSet = null
    )
}