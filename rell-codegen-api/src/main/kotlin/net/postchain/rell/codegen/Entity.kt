package net.postchain.rell.codegen

interface Entity : Formattable {
    val imports: List<String>
}