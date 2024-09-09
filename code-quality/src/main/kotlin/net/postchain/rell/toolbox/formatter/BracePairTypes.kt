package net.postchain.rell.toolbox.formatter

enum class BracePairTypes(var opening: String, var closing: String) {
    CURLY("{", "}"),
    BRACKETS("[", "]"),
    PARENTHESES("(", ")"),
    ANGLE("<", ">")
}
