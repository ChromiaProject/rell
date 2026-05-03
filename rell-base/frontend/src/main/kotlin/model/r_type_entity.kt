/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.doc.DocCode

class R_EntityType(val rEntity: R_EntityDefinition): R_SimpleType(rEntity.appLevelName, rEntity.cDefName) {
    init {
        checkNull(rEntity.type) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_EntityType && other.rEntity == rEntity
    override fun hashCode0(): Int = rEntity.hashCode()

    override fun isComparable() = true
    override fun isDirectPure() = false

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rEntity.moduleLevelName),
        valueMembers = R_LibTypeMemberRegistry.getValueMembers(this),
    )
}

