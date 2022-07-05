package net.postchain.rell.codegen

interface Document : StringSerializable {
    val intro: String
    val packageString: String

    fun addSection(section: DocumentSection)
}