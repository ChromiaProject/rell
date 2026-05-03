/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.doc.DocCode

class R_ObjectType(val rObject: R_ObjectDefinition): R_SimpleType(rObject.appLevelName, rObject.cDefName) {
    init {
        checkNull(rObject.type) // during initialization
    }

    override fun equals0(other: R_Type) = other is R_ObjectType && other.rObject == rObject
    override fun hashCode0() = rObject.hashCode()

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rObject.moduleLevelName),
        valueMembers = R_LibTypeMemberRegistry.getValueMembers(this),
    )
}
