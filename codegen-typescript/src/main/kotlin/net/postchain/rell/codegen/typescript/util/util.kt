package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.model.*

fun parameterTransformer(name: String, type: R_Type): String = when (type) {
    is R_SetType -> "Array.from($name)"
    else -> name
}
