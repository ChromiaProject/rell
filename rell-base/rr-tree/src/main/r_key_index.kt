/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

// Lives in `rr-tree` rather than `utils` because these are data shapes
// referenced from RR_EntityDefinition; `utils` should not own RR-shaped types.
// The `R_` prefix is preserved here for backwards compatibility — see CLAUDE.md
// for the reasoning.

sealed class R_KeyIndex {
    abstract val attribs: ImmList<Name>

    val strAttribs by lazy { attribs.mapToImmList { it.str } }
}

data class R_Key(override val attribs: ImmList<Name>): R_KeyIndex()
data class R_Index(override val attribs: ImmList<Name>): R_KeyIndex()
