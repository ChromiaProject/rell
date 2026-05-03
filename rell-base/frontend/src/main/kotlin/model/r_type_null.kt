/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.doc.DocType

object R_NullType: R_UniqueType("null", C_LibUtils.defName("null")) {
    override fun isComparable() = true
    override fun calcCommonType(other: R_Type): R_Type = R_NullableType(other)
    override fun getLibType0() = C_LibType.make(M_Types.NULL)
    override fun docType() = DocType.NULL
}
