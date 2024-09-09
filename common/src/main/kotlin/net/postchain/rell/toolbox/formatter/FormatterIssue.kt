package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.common.TextEdit

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
