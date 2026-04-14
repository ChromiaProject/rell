/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.R_Type

fun parameterTransformer(name: String, type: R_Type): String = when (type) {
    is R_SetType -> "Array.from($name)"
    is R_StructType -> "Object.values($name)"
    else -> name
}
