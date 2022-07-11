package net.postchain.rell.codegen.deps

interface ClassName {
    val rellName: String
    val name: String
    val module: String

    fun toPackageName() : String {
        if (module == "") return name
        return "$module.$name"
    }
}