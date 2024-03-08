package com.chromia.rell.dokka.dapp

import com.chromia.rell.dokka.descriptors.NULL_DESCRIPTOR
import com.chromia.rell.dokka.doc.simpleDocumentationNode
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_Module
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility

class RellProjectAnalysis(
        private val dapp: R_App,
        private val sourceSet: DokkaConfiguration.DokkaSourceSet
) {

    fun modules() = dapp.modules

    fun List<R_Module>.visitModules() = map { it.visit() }

    private fun R_Module.visit(): DPackage {

        val globalConstants = dapp.constants.map { it.visit() }

        return DPackage(
                dri = DRI(name.str()),
                properties = globalConstants,
                classlikes = listOf(),
                functions = listOf(),
                typealiases = listOf(),
                sourceSets = setOf(sourceSet),
                documentation = docSymbol.toDocumentationNode().toSourceSetDependent()
        )
    }

    private fun R_GlobalConstantDefinition.visit(): DProperty {
        return DProperty(
                dri = DRI.from(this),
                name = simpleName,
                receiver = null,
                setter = null,
                getter = null,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                generics = listOf(),
                isExpectActual = false,
                sourceSets = setOf(sourceSet),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                type = type().toBound(),
                expectPresentInSet = null,
                documentation = simpleDocumentationNode("This is constant $simpleName").toSourceSetDependent()
        )
    }


    private fun <T> T.toSourceSetDependent() = if (this != null) mapOf(sourceSet to this) else mapOf()
}



