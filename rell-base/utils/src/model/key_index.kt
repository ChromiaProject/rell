/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

sealed class KeyIndex {
    abstract val attribs: ImmList<Name>

    val strAttribs by lazy { attribs.mapToImmList { it.str } }
}

data class Key(override val attribs: ImmList<Name>): KeyIndex()
data class Index(override val attribs: ImmList<Name>): KeyIndex()
