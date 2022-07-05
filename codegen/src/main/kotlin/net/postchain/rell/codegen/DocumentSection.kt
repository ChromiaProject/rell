package net.postchain.rell.codegen

interface DocumentSection : StringSerializable {
    val imports: List<String>
}