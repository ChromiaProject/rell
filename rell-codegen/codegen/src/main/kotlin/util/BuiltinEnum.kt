/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.section.Builtin

interface BuiltinType {
    fun createBuiltin(): Builtin
}
