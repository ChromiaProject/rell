package net.postchain.rell.codegen.section

import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.deps.ClassName

interface DocumentSection : StringSerializable {
    val moduleName: String
    val imports: List<String>
    val deps: Set<ClassName>
        get() = setOf()
}