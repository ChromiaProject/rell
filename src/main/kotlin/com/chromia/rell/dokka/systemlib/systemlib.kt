@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.model.toDClasslike
import com.chromia.rell.dokka.model.toDFunction
import com.chromia.rell.dokka.model.toDProperty
import com.chromia.rell.dokka.model.toDRI
import com.chromia.rell.dokka.translator.RellDeclarationDescriptor
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.lmodel.L_NamespaceMember_Namespace
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import net.postchain.rell.base.lmodel.L_NamespaceMember_TypeExtension
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import org.checkerframework.checker.units.qual.t
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text

fun L_Module.toDPackage(sourceSet: DokkaConfiguration.DokkaSourceSet) = DPackage(
        dri = DRI(moduleName.str()),
        documentation = mapOf(
                sourceSet to DocumentationNode(listOf(Description(Text(docSymbol.comment?.description ?: ""))))
        ),
        sourceSets = setOf(sourceSet),
        // Global constants
        properties = listOf(),
        // Entities/Structs/Objects
        classlikes = this.namespace.getAllDefs().filterIsInstance<L_NamespaceMember_Type>().mapNotNull { it.toDClass(sourceSet) },
        typealiases = listOf(),
        // Functions, queries, operations
        functions = listOf()
)

val NULL_DESCRIPTOR: DocumentableSource = DescriptorDocumentableSource(RellDeclarationDescriptor())

/*
fun C_LibModule.funs()  = lModule.namespace.getAllDefs()
.flatMap { namespaceMember ->
    when (namespaceMember) {
        is L_NamespaceMember_Type -> {
            val typeSection = null//createNamespaceTypeSection(namespaceMember)
            val typeMembers = namespaceMember.typeDef.members.all.map { createTypeDefMemberSection(namespaceMember.qualifiedName, it) }
            if (typeSection.id.sectionName == "index") println("Type: ${typeSection.id} - ${typeSection.format()}")
            listOf(typeSection) + typeMembers
        }
        is L_NamespaceMember_Namespace -> {
            val nsSection = null//createNamespaceSection(namespaceMember)
            //println("NS: ${nsSection.id} - ${nsSection.format()}")
            if (nsSection.id.sectionName == "index") println("Ns: ${nsSection.id} - ${nsSection.format()}")
            listOf(nsSection)
        }
        is L_NamespaceMember_TypeExtension -> {
            val extSection = null//createNamespaceMemberSection(namespaceMember)
            val extMembers = namespaceMember.typeExt.members.all.map { createTypeDefMemberSection(namespaceMember.qualifiedName, it) }
            if (extSection.id.sectionName == "index") println("Ext: ${extSection.id} - ${extSection.format()}")
            listOf(extSection) + extMembers
        }
        else -> null//listOf(createNamespaceMemberSection(namespaceMember))
    }
}
*/
