package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.core.TextEdit

data class FormatterIssue(
    val message: String,
    val type: DeltaType,
    val line: Int,
    val column: Int,
    val textEdit: TextEdit
)

enum class DeltaType {
    INSERT,
    CHANGE,
    DELETE
}
