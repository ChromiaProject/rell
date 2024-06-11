package util

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit

data class TestTextEdit(val range: TestRange, val newText: String) {
    constructor(textEdit: TextEdit) : this(TestRange(textEdit.range), textEdit.newText)
}

data class TestRange(val start: TestPosition, val end: TestPosition) {
    constructor(range: Range) : this(TestPosition(range.start), TestPosition(range.end))

    override fun toString() = if (start.line == end.line)
        "[${start.line}:${start.character}-${end.character}]"
    else
        "[${start.line}:${start.character}]-[${end.line}:${end.character}]"
}

data class TestPosition(val line: Int, val character: Int) {
    constructor(position: Position) : this(position.line, position.character)
}
