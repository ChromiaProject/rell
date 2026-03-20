package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.section.Builtin

interface BuiltinType {
    fun createBuiltin(): Builtin
}
