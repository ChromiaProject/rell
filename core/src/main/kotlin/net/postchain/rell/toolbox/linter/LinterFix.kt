package net.postchain.rell.toolbox.linter

data class LinterFix(val line: Int, val charPositionInLine: Int, val length: Int, val newText: String)