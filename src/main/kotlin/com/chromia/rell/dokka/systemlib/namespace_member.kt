@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.translator.RellDeclarationDescriptor
import net.postchain.rell.base.lmodel.L_NamespaceMember
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import org.jetbrains.dokka.base.renderers.html.SearchbarDataInstaller
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.Visibility
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text

fun L_NamespaceMember.toDClass(sourceSet: DokkaConfiguration.DokkaSourceSet) = when (this) {
    is L_NamespaceMember_Type -> typeToDClass(this, sourceSet)
    else -> null
}

fun typeToDClass(type: L_NamespaceMember_Type, sourceSet: DokkaConfiguration.DokkaSourceSet): DClass {

    with(type) {

        val dri = DRI(fullName.moduleName.str(), simpleName.str)
        val constructors = typeDef.members.all.filterIsInstance<L_TypeDefMember_Constructor>().map { it.toDFunction(sourceSet, dri) }
        val functions = typeDef.members.all.filterIsInstance<L_TypeDefMember_Function>().map { it.toDFunction(sourceSet, dri) }
        val properties = typeDef.members.all.filterIsInstance<L_TypeDefMember_Property>()
        val constants = typeDef.members.all.filterIsInstance<L_TypeDefMember_Constant>()

        return DClass(
                dri = dri,
                name = simpleName.str,
                documentation = mapOf(sourceSet to docSymbol.toDocumentationNode()),

                constructors = constructors,
                properties = listOf(),
                functions = functions,
                generics = listOf(),
                classlikes = listOf(),
                isExpectActual = false,
                companion = null,
                expectPresentInSet = null,
                visibility = mapOf(sourceSet to KotlinVisibility.Public),
                supertypes = mapOf(sourceSet to listOf()), // TODO: add super types
                sourceSets = setOf(sourceSet),
                sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
                modifier = mapOf()
        )
    }

    fun a(parent: SearchbarDataInstaller.DRIWithSourceSets) {

        DObject(
                dri = parent.dri.withClass("static"),
                )
    }
}