package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.section.DocumentSection

interface Document : StringSerializable {
    val intro: String
    val packageString: String

    fun addSection(section: DocumentSection)
}