package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Builtin

interface BuiltinType: ClassName {
    fun createBuiltin(): Builtin
}
