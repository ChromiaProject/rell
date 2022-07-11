package net.postchain.rell.codegen.deps

interface ClassName {
    val rellName: String
    val name: String
    val module: String

    fun toPackageName(basePackage: String) : String {
        if (module == "") return "$basePackage.$name"
        return "$basePackage.$module.$name"
    }
}