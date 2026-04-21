/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.base.model.*

fun parameterTransformer(name: String, type: R_Type): String = when (type) {
    is R_SetType -> "Array.from($name)"
    is R_StructType -> "Object.values($name)"
    else -> name
}
