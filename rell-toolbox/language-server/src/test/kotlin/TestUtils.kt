/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp

import org.eclipse.lsp4j.*
import java.io.File

data class TestTextEdit(val range: TestRange, val newText: String) {
    constructor(textEdit: TextEdit) : this(TestRange(textEdit.range), textEdit.newText)
}

data class TestRange(val start: TestPosition, val end: TestPosition) {
    constructor(range: Range) : this(TestPosition(range.start), TestPosition(range.end))

    override fun toString() = if (start.line == end.line) {
        "[${start.line}:${start.character}-${end.character}]"
    } else {
        "[${start.line}:${start.character}]-[${end.line}:${end.character}]"
    }
}

data class TestPosition(val line: Int, val character: Int) {
    constructor(position: Position) : this(position.line, position.character)
}

data class TestPrepareRenameResult(val range: TestRange, val placeholder: String) {
    constructor(prepareRenameResult: PrepareRenameResult) : this(
        TestRange(prepareRenameResult.range),
        prepareRenameResult.placeholder
    )
}

fun createTextDocumentItem(file: File, version: Int = 1): TextDocumentItem {
    return TextDocumentItem(
        file.toURI().toString(),
        "rell",
        version,
        file.readText()
    )
}
