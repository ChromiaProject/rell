/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated

object L_InternalUtils {
    fun deprecatedStrCodeOrNull(deprecated: C_Deprecated?): String? {
        return if (deprecated == null) null else deprecatedStrCode(deprecated)
    }

    fun deprecatedStrCode(deprecated: C_Deprecated): String {
        val level = if (deprecated.error) "ERROR" else "WARNING"
        return "@deprecated($level)"
    }
}
