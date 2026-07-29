/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.TestPosition
import net.postchain.rell.toolbox.lsp.TestRange
import net.postchain.rell.toolbox.lsp.TestTextEdit
import net.postchain.rell.toolbox.testing.testData
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

/**
 * Covers the LSP quick-fix surface for the linter: a rule that carries an auto-fix must produce a
 * `quickfix` code action with the correct text edit, while a diagnostic-only rule must produce no
 * quick fix at all (only the "disable for this line" action).
 */
class CodeActionServiceTest {
    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val formatterOptions = FormatterOptions()

    // rule_prefer_empty carries an auto-fix; rule_redundant_boolean_comparison is diagnostic-only.
    private val linterOptions = testLinterOptions {
        rulePreferEmpty = true
        ruleRedundantBooleanComparison = true
    }

    @TempDir
    private lateinit var tempDir: File
    private lateinit var mainFileUri: URI
    private lateinit var indexer: WorkspaceIndexer

    // Line 2 (0-based) holds the auto-fixable issue, line 5 the diagnostic-only one.
    private val fixableLine = 2
    private val diagnosticOnlyLine = 5
    private val expectedEdit = TestTextEdit(
        TestRange(TestPosition(fixableLine, 11), TestPosition(fixableLine, 25)),
        "xs.empty()"
    )

    @BeforeEach
    fun setup() {
        val workspaceFolder = testData(tempDir) {
            addMainFile(
                """
                module;
                function fixable(xs: list<integer>): boolean {
                    return xs.size() == 0;
                }
                function diagnostic_only(flag: boolean): boolean {
                    return flag == false;
                }
                """.trimIndent()
            )
        }.workspaceFolder
        mainFileUri = workspaceFolder.resolve("src/main.rell").toURI()
        indexer = WorkspaceIndexer(
            workspaceFolder.toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        )
        indexer.initialFileIndexBuild()
    }

    @Test
    fun `should offer a quick fix with the correct edit for an auto-fixable issue`() {
        val actions = codeActionsAt(fixableLine)

        assertThat(actions.map { it.title }).contains(PREFER_EMPTY_TITLE)
        val quickFix = actions.first { it.title == PREFER_EMPTY_TITLE }
        assertThat(quickFix.kind).isEqualTo("quickfix")
        assertThat(editsOf(quickFix)).isEqualTo(listOf(expectedEdit))
    }

    @Test
    fun `should offer the disable and fix-all actions alongside a quick fix`() {
        val titles = codeActionsAt(fixableLine).map { it.title }
        assertThat(titles).contains(CodeActionTitles.DISABLE_LINTER.title)
        assertThat(titles).contains(CodeActionTitles.AUTO_FIXABLE.title)
    }

    @Test
    fun `should rank the fix above the suppression and the whole-file action`() {
        val actions = codeActionsAt(fixableLine)
        val quickFix = actions.first { it.title == PREFER_EMPTY_TITLE }
        val disable = actions.first { it.title == CodeActionTitles.DISABLE_LINTER.title }
        val fixAll = actions.first { it.title == CodeActionTitles.AUTO_FIXABLE.title }

        // Only the actual fix is preferred; suppressing or rewriting the file never is.
        assertThat(quickFix.isPreferred).isEqualTo(true)
        assertThat(disable.isPreferred).isEqualTo(false)
        assertThat(fixAll.isPreferred).isEqualTo(false)

        // Neither the whole-file rewrite nor the suppression is a quick-fix, so LSP4IJ keeps them out
        // of the diagnostic's quick-fix group and below the real fix in the popup.
        assertThat(fixAll.kind).isEqualTo(CodeActionKind.SourceFixAll)
        assertThat(disable.kind).isEqualTo(CodeActionKind.Source)

        // The fix declares the diagnostic it resolves, so a client can associate the two.
        assertThat(quickFix.diagnostics.map { it.code.left })
            .isEqualTo(listOf("linter_issue:rule_prefer_empty"))
    }

    @Test
    fun `should offer no quick fix for a diagnostic-only issue`() {
        // The issue is reported on this line, but it has no fix, so only the disable actions remain.
        val titles = codeActionsAt(diagnosticOnlyLine).map { it.title }
        assertThat(titles).isEqualTo(
            listOf(DISABLE_REDUNDANT_COMPARISON_TITLE, CodeActionTitles.DISABLE_LINTER.title)
        )
    }

    @Test
    fun `quickfix-only request should exclude the source actions`() {
        val titles = codeActionsAt(fixableLine, only = listOf("quickfix")).map { it.title }

        assertThat(titles).contains(PREFER_EMPTY_TITLE)
        assertThat(titles).contains(DISABLE_PREFER_EMPTY_TITLE)
        assertThat(titles).doesNotContain(CodeActionTitles.DISABLE_LINTER.title)
        assertThat(titles).doesNotContain(CodeActionTitles.AUTO_FIXABLE.title)
    }

    @Test
    fun `disable-globally quickfix should carry the server-side command`() {
        val actions = codeActionsAt(diagnosticOnlyLine)
        val disableRule = actions.first { it.title == DISABLE_REDUNDANT_COMPARISON_TITLE }

        assertThat(disableRule.kind).isEqualTo("quickfix")
        assertThat(disableRule.isPreferred).isEqualTo(false)
        assertThat(disableRule.diagnostics.map { it.code.left })
            .isEqualTo(listOf("linter_issue:rule_redundant_boolean_comparison"))

        // The config edit happens server-side (executeCommand) so diagnostics refresh immediately;
        // the action carries no client-applied edit.
        assertThat(disableRule.edit).isNull()
        assertThat(disableRule.command.command).isEqualTo(CodeActionService.DISABLE_RULE_COMMAND)
        assertThat(disableRule.command.arguments)
            .isEqualTo(listOf<Any>("rule_redundant_boolean_comparison", mainFileUri.toString()))
    }

    @Test
    fun `fix-all for the file should include only the auto-fixable edit`() {
        val action = CodeActionService.getCodeActionForFile(mainFileUri, indexer)

        assertThat(action.title).isEqualTo(CodeActionTitles.AUTO_FIXABLE.title)
        assertThat(editsOf(action)).isEqualTo(listOf(expectedEdit))
    }

    private fun codeActionsAt(line: Int, only: List<String>? = null): List<CodeAction> {
        val range = Range(Position(line, 0), Position(line, 0))
        return CodeActionService.getCodeActions(mainFileUri, range, indexer, only).map { it.right }
    }

    private fun editsOf(action: CodeAction): List<TestTextEdit> =
        action.edit.changes[mainFileUri.toString()].orEmpty().map { TestTextEdit(it) }

    companion object {
        // The action is titled after what the fix does, not after the diagnostic message.
        private const val PREFER_EMPTY_TITLE = "Replace with 'xs.empty()'"
        private const val DISABLE_PREFER_EMPTY_TITLE = "Disable 'rule_prefer_empty' in .rell_lint"
        private const val DISABLE_REDUNDANT_COMPARISON_TITLE =
            "Disable 'rule_redundant_boolean_comparison' in .rell_lint"
    }
}
