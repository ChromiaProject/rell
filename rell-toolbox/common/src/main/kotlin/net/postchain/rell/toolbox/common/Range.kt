package net.postchain.rell.toolbox.common

data class Range(val start: Position, val end: Position) {
    override fun toString() = if (start.line == end.line) {
        "[${start.line}:${start.character}-${end.character}]"
    } else {
        "[${start.line}:${start.character}]-[${end.line}:${end.character}]"
    }
}
