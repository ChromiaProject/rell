/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ImmList

@JvmRecord
data class RR_EnumDefinition(
    val base: RR_DefinitionBase,
    val attrs: ImmList<RR_EnumAttr>,
) {
    fun attr(name: String): RR_EnumAttr? = attrs.find { it.nameStr == name }
    fun attr(value: Int): RR_EnumAttr? = if (value < 0 || value >= attrs.size) null else attrs[value]
}

@JvmRecord
data class RR_EnumAttr(val name: Name, val value: Int) {
    @get:JvmName("nameStr")
    val nameStr: String
        get() = name.str
}
