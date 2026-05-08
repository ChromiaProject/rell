/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.DefinitionName
import net.postchain.rell.base.utils.ImmList

/**
 * Common base for all Rell definitions (entities, structs, functions, operations, queries, etc.).
 * Carries the definition identity ([defId], [defName]) and the initializer frame layout.
 */
@JvmRecord data class RR_DefinitionBase(
    val defId: DefinitionId,
    val defName: DefinitionName,
    val initFrame: RR_FrameDescriptor,
) {
    val simpleName: String
        get() = defName.simpleName

    val appLevelName: String
        get() = defName.appLevelName
}

@JvmRecord data class RR_FunctionHeader(
    val type: RR_Type,
    val params: ImmList<RR_FunctionParam>,
)
