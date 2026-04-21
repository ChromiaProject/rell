/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ImmList

data class RR_EnumDefinition(
    val base: RR_DefinitionBase,
    val attrs: ImmList<RR_EnumAttr>,
) {
    private val attrMap by lazy { attrs.associateBy { it.name } }

    fun attr(name: String): RR_EnumAttr? = attrMap[name]
    fun attr(value: Long): RR_EnumAttr? = if (value < 0 || value >= attrs.size) null else attrs[value.toInt()]
}

data class RR_EnumAttr(
    val rName: Name,
    val value: Int,
) {
    val name: String get() = rName.str
}
