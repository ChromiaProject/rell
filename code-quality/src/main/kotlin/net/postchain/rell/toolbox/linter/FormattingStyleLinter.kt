package net.postchain.rell.toolbox.linter

import com.github.difflib.patch.Chunk
import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.Range
import net.postchain.rell.toolbox.core.TextEdit
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.core.offsetToPosition
import net.postchain.rell.toolbox.formatter.DeltaType
import net.postchain.rell.toolbox.formatter.Diff
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.formatter.RellFormatter

class FormattingStyleLinter : AbstractFormattingStyleLinter() {

    override fun enhanceWithFormatterIssues(
        linterOptions: LinterOptions,
        formatterOptions: FormatterOptions?,
        resource: Resource,
        fileContent: String
    ) {
        resource.formatterIssues = lint(linterOptions, formatterOptions, fileContent)
    }

    override fun lint(
        linterOptions: LinterOptions,
        formatterOptions: FormatterOptions?,
        fileContent: String
    ): List<FormatterIssue> {
        if (!(linterOptions.enabled && linterOptions.ruleFormatter == true)) {
            return listOf()
        }
        val options = formatterOptions ?: FormatterOptions()
        val formattedFileContent = RellFormatter.formatString(fileContent, options)
        val deltas = Diff.diffInline(fileContent, formattedFileContent)
        val formatterIssues = mutableListOf<FormatterIssue>()
        for (delta in deltas) {
            when (delta.type) {
                com.github.difflib.patch.DeltaType.INSERT -> {
                    val position = offsetToPosition(fileContent, delta.source.position)
                    formatterIssues.add(
                        FormatterIssue(
                            "Insert: `${getFormatterMessage(delta.target)}` at line ${position.line + 1}, column ${position.character + 1}",
                            DeltaType.INSERT,
                            position.line + 1,
                            position.character,
                            getTextEditForInsert(delta.target, position.line, position.character)
                        )
                    )
                }

                com.github.difflib.patch.DeltaType.DELETE -> {
                    val position = offsetToPosition(fileContent, delta.source.position)
                    formatterIssues.add(
                        FormatterIssue(
                            "Delete: `${getFormatterMessage(delta.source)}` from line ${position.line + 1}, column ${position.character + 1}",
                            DeltaType.DELETE,
                            position.line + 1,
                            position.character,
                            getTextEditForDelete(delta.source, position.line, position.character)
                        )
                    )
                }

                com.github.difflib.patch.DeltaType.CHANGE -> {
                    val position = offsetToPosition(fileContent, delta.source.position)
                    formatterIssues.add(
                        FormatterIssue(
                            "Change: `${getFormatterMessage(delta.source)}` to '${getFormatterMessage(delta.target)}' at line ${position.line + 1}, column ${position.character + 1}",
                            DeltaType.CHANGE,
                            position.line + 1,
                            position.character,
                            getTextEditForChange(delta.source, delta.target, position.line, position.character)
                        )
                    )
                }

                com.github.difflib.patch.DeltaType.EQUAL -> {
                    // Ignore
                }

                else -> throw IllegalStateException("Unsupported delta type: ${delta.type}")
            }
        }

        return formatterIssues
    }

    private fun getFormatterMessage(chunk: Chunk<String>): String {
        return chunk.lines.joinToString(" ") {
            it.replace("\n", "⏎")
                .replace(' ', '·')
                .replace("\t", "↹")
        }
    }

    private fun getTextEditForInsert(chunk: Chunk<String>, line: Int, character: Int): TextEdit {
        val newText = chunk.lines.joinToString(" ")
        return TextEdit(Range(Position(line, character), Position(line, character)), newText)
    }

    private fun getTextEditForDelete(chunk: Chunk<String>, line: Int, character: Int): TextEdit {
        val newText = chunk.lines.joinToString(" ")
        val newLinesCount = newText.count { it == '\n' }
        val stopColumn = if (newLinesCount > 0) {
            newText.substringAfterLast('\n', "").length
        } else {
            character + newText.length
        }
        return TextEdit(Range(Position(line, character), Position(line + newLinesCount, stopColumn)), "")
    }

    private fun getTextEditForChange(
        source: Chunk<String>,
        target: Chunk<String>,
        line: Int,
        character: Int
    ): TextEdit {
        val newText = target.lines.joinToString(" ")
        val oldText = source.lines.joinToString(" ")
        val stopLine = line + oldText.count { it == '\n' }
        val stopColumn = if (line != stopLine) {
            character + oldText.substringAfterLast('\n', "").length
        } else {
            character + oldText.length
        }
        return TextEdit(Range(Position(line, character), Position(stopLine, stopColumn)), newText)
    }
}
