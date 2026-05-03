/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.model.R_CompositeType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_TypeMeta
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.immListOf

class R_LazyExpr(type: R_Type, val innerExpr: R_Expr): R_BaseExpr(type)

class R_LazyType(private val valueType: R_Type): R_CompositeType("lazy<${valueType.strCode()}>") {
    override fun equals0(other: R_Type) = other is R_LazyType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()

    override fun getTypeMeta0() = META
    override fun getTypeArgs() = immListOf(valueType)
    override fun strCode() = name

    override fun getLibType0(): C_LibType {
        val b = DocCode.builder()
        b.keyword("lazy")
        b.raw("<")
        valueType.docType().genCode(b)
        b.raw(">")
        val doc = b.build()
        return C_LibType.make(this, doc)
    }

    companion object {
        private val META = R_TypeMeta.make { t -> R_LazyType(t) }
    }
}
