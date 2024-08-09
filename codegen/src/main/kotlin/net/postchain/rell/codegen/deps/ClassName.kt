package net.postchain.rell.codegen.deps

interface ClassName {
    val rellName: String
    val className: String
    val constantName: String
    val module: String

    fun toPackageName(basePackage: String): String {
        if (module == "") return "$basePackage.$className"
        return "$basePackage.$module.$className"
    }
}
