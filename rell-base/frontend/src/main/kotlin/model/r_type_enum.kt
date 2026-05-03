/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.doc.DocCode

class R_EnumType(val enum: R_EnumDefinition): R_SimpleType(enum.appLevelName, enum.cDefName) {
    init {
        checkNull(enum.type) // during initialization
    }

    override fun isComparable() = true

    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun isDirectPure() = true

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(enum.moduleLevelName),
        staticMembers = R_LibTypeMemberRegistry.getStaticMembers(this),
    )
}
