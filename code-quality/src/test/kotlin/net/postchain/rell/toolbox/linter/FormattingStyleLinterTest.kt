package net.postchain.rell.toolbox.linter

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.Range
import net.postchain.rell.toolbox.core.TextEdit
import net.postchain.rell.toolbox.indexer.RellResourceFactory
import net.postchain.rell.toolbox.parser.AntlrRellParser
import net.postchain.rell.toolbox.formatter.FormatterOptions
import org.junit.jupiter.api.Test
import java.io.File
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.formatter.DeltaType
import net.postchain.rell.toolbox.formatter.FormatterIssue


class FormattingStyleLinterTest {

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(
            lint(
                "formatter.rell",
                LinterOptions(enabled = false, ruleFormatter = true),
                FormatterOptions()
            )
        ).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(
            lint(
                "formatter.rell",
                LinterOptions(enabled = true, ruleFormatter = false),
                FormatterOptions()
            )
        ).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(
            lint(
                "formatter.rell",
                LinterOptions(enabled = true, ruleFormatter = null),
                FormatterOptions()
            )
        ).isEmpty()
    }

    @Test
    fun `should find formatting violations`() {
        val result = lint("formatter.rell", LinterOptions(enabled = true, ruleFormatter = true), FormatterOptions())
        val expectedIssues = listOf(
            FormatterIssue(
                message = "Insert: `⏎` at line 2, column 1",
                type = DeltaType.INSERT,
                line = 2,
                column = 0,
                textEdit = TextEdit(range = Range(Position(1, 0), Position(1, 0)), newText = "\n")
            ),
            FormatterIssue(
                message = "Delete: `··` from line 2, column 10",
                type = DeltaType.DELETE,
                line = 2,
                column = 9,
                textEdit = TextEdit(range = Range(Position(1, 9), Position(1, 11)), newText = "")
            ),
            FormatterIssue(
                message = "Delete: `····` from line 2, column 15",
                type = DeltaType.DELETE,
                line = 2,
                column = 14,
                textEdit = TextEdit(range = Range(Position(1, 14), Position(1, 18)), newText = "")
            ),
            FormatterIssue(
                message = "Delete: `·` from line 2, column 32",
                type = DeltaType.DELETE,
                line = 2,
                column = 31,
                textEdit = TextEdit(range = Range(Position(1, 31), Position(1, 32)), newText = "")
            ),
            FormatterIssue(
                message = "Insert: `····` at line 4, column 1",
                type = DeltaType.INSERT,
                line = 4,
                column = 0,
                textEdit = TextEdit(range = Range(Position(3, 0), Position(3, 0)), newText = "    ")
            ),
            FormatterIssue(
                message = "Delete: `⏎` from line 5, column 1",
                type = DeltaType.DELETE,
                line = 5,
                column = 0,
                textEdit = TextEdit(range = Range(Position(4, 0), Position(5, 0)), newText = "")
            ),
            FormatterIssue(
                message = "Change: `·` to '⏎' at line 6, column 2",
                type = DeltaType.CHANGE,
                line = 6,
                column = 1,
                textEdit = TextEdit(range = Range(Position(5, 1), Position(5, 2)), newText = "\n")
            )
        )
        assertThat(result).containsExactly(*expectedIssues.toTypedArray())
    }

    private fun lint(
        fileName: String,
        linterOptions: LinterOptions,
        formatterOptions: FormatterOptions
    ): List<FormatterIssue> {
        val formattingStyleLinter = FormattingStyleLinter()
        val fileContent = javaClass.getResource("/linter/$fileName")!!.readText()
        return formattingStyleLinter.lint(linterOptions, formatterOptions, fileContent)
    }

    @Test
    fun `should enhance resource with formatter issues`() {
        val formattingStyleLinter = FormattingStyleLinter()
        val fileName = "formatter.rell"
        val fileUri = javaClass.getResource("/linter/$fileName")!!.toURI()
        val resourceFactory = RellResourceFactory(fileUri, AntlrRellParser(), ChromiaModelProvider(null))
        val resource = resourceFactory.buildRellResource(fileUri, mutableMapOf())
        val fileContent = File(fileUri).readText()

        assertThat(resource.formatterIssues).isEmpty()
        formattingStyleLinter.enhanceWithFormatterIssues(
            LinterOptions(enabled = true, ruleFormatter = true),
            FormatterOptions(),
            resource,
            fileContent
        )
        assertThat(resource.formatterIssues).hasSize(7)
    }
}
