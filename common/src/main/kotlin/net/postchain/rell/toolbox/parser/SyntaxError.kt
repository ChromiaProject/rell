package net.postchain.rell.toolbox.parser

data class SyntaxError(
    val message: String?,
    val line: Int,
    val charPositionInLine: Int,
    val sourceName: String?
)
