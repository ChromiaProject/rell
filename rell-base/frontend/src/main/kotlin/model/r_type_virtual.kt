/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.lmodel.L_TypeDefDocCodeStrategy
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.doc.DocTypeSet
import net.postchain.rell.base.utils.immListOf

sealed class R_VirtualType(val baseInnerType: R_Type): R_CompositeType("virtual<${baseInnerType.name}>") {
    private val isError = baseInnerType.isError()

    final override fun isReference() = true
    final override fun isError() = isError
    final override fun strCode() = name
    final override fun isDirectPure() = false    // Maybe it's actually pure.
    override fun explicitComponentTypes() = immListOf<R_Type>()

    override fun docType(): DocType {
        val docArgs = immListOf(DocTypeSet.one(baseInnerType.docType()))
        val strategy = L_TypeDefDocCodeStrategy { argDocs ->
            val b = DocCode.builder()
            b.keyword("virtual").raw("<")
            b.append(argDocs[0]).raw(">")
            b.build()
        }
        return DocType.generic(strategy, docArgs)
    }

    companion object {
        val META = R_TypeMeta.make { t ->
            S_VirtualType.virtualType(t)
        }
    }
}
