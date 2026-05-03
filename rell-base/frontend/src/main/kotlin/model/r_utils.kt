/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import java.util.*

class R_IdeName(val rName: Name, val ideInfo: C_IdeSymbolInfo) {
    val str = rName.str

    override fun equals(other: Any?) = other is R_IdeName && rName == other.rName
    override fun hashCode() = Objects.hash(javaClass, rName)
    override fun toString() = rName.toString()
}

object R_Utils {
    private val ERROR_APP_UID = AppUid(-1)
    private val ERROR_CONTAINER_UID = R_ContainerUid(-1, ERROR_APP_UID)
    private val ERROR_FN_UID = R_FnUid(-1, ERROR_CONTAINER_UID)
    val ERROR_BLOCK_UID = R_FrameBlockUid(-1, ERROR_FN_UID)
}
