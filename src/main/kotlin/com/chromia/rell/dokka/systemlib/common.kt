package com.chromia.rell.dokka.systemlib

import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator.NULL_DESCRIPTOR
import net.postchain.rell.base.lmodel.L_Constant
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DProperty

fun L_Constant.toDProperty(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, docSymbol: DocSymbol) =
        makeDProperty(sourceSet, parent, docSymbol, simpleName.str, type)

fun makeDProperty(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, docSymbol: DocSymbol, name: String, type: M_Type) =
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
        )