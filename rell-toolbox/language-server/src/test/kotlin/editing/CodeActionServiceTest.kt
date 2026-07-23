/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
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
    fun `should offer no quick fix for a diagnostic-only issue`() {
        // The issue is reported on this line, but it has no fix, so only the disable action remains.
        val titles = codeActionsAt(diagnosticOnlyLine).map { it.title }
        assertThat(titles).isEqualTo(listOf(CodeActionTitles.DISABLE_LINTER.title))
    }

    @Test
    fun `fix-all for the file should include only the auto-fixable edit`() {
        val action = CodeActionService.getCodeActionForFile(mainFileUri, indexer)

        assertThat(action.title).isEqualTo(CodeActionTitles.AUTO_FIXABLE.title)
        assertThat(editsOf(action)).isEqualTo(listOf(expectedEdit))
    }

    private fun codeActionsAt(line: Int): List<CodeAction> {
        val range = Range(Position(line, 0), Position(line, 0))
        return CodeActionService.getCodeActions(mainFileUri, range, indexer).map { it.right }
    }

    private fun editsOf(action: CodeAction): List<TestTextEdit> =
        action.edit.changes[mainFileUri.toString()].orEmpty().map { TestTextEdit(it) }

    companion object {
        private const val PREFER_EMPTY_TITLE = "Use '.empty()' instead of a '.size()' comparison"
    }
}
