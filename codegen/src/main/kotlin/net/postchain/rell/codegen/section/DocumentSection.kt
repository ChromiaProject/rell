package net.postchain.rell.codegen.section

import net.postchain.rell.codegen.StringSerializable

interface DocumentSection : StringSerializable {
    val externalName: String
    val moduleName: String
    val imports: List<String>
}