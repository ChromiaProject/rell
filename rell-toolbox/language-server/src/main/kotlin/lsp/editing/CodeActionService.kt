/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsConverter
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.io.File
import java.net.URI

enum class CodeActionTitles(val title: String) {
    AUTO_FIXABLE("Fix all auto-fixable issues"),
    DISABLE_LINTER("Disable linter for this line");

    companion object {
        fun disableRuleGlobally(ruleId: String): String = "Disable '$ruleId' in ${LinterOptions.CONFIG_FILE_NAME}"
    }
}

object CodeActionService {

    // The `.rell_lint` key gating the formatter diagnostics (see LinterOptions.updateOptionsFromFile).
    private const val FORMATTER_RULE_ID = "rule_formatter"

    private const val SECTION_HEADER = "[*.rell]"

    fun getCodeActions(
        fileUri: URI,
        range: Range,
        indexer: WorkspaceIndexer,
        only: List<String>? = null,
    ): List<Either<Command, CodeAction>> {
        val resource = indexer.getResource(fileUri) ?: return mutableListOf()
        val linterIssues = findLinterIssuesForRange(range, resource)
        val formatterIssues = findFormatterIssuesForRange(range, resource)
        return createCodeActions(fileUri, indexer.workspaceUri, linterIssues, formatterIssues, range)
            .filter { it.isLeft || matchesKindFilter(it.right.kind, only) }
    }

    fun getCodeActionForFile(fileUri: URI, indexer: WorkspaceIndexer): CodeAction {
        val resource = indexer.getResource(fileUri) ?: return CodeAction()
        val codeAction = CodeAction(CodeActionTitles.AUTO_FIXABLE.title)
        codeAction.kind = CodeActionKind.SourceFixAll

        val linterEdits = resource.linterIssues.mapNotNull { it.fix() }.map {
            getEditsForLinterFix(fileUri, it)
        }
        val formatterEdits = resource.formatterIssues.map {
            getEditsForFormatterIssue(fileUri, it)
        }
        codeAction.edit = WorkspaceEdit(mergeCodeActionEdits(linterEdits + formatterEdits))
        return codeAction
    }

    private fun findLinterIssuesForRange(range: Range, resource: Resource): List<LinterIssue> {
        return resource.linterIssues.filter {
            it.ctx.start.line == range.start.line + 1
        }
    }

    private fun findFormatterIssuesForRange(range: Range, resource: Resource): List<FormatterIssue> {
        return resource.formatterIssues.filter {
            it.line == range.start.line + 1
        }
    }

    private fun createCodeActions(
        fileUri: URI,
        workspaceUri: URI,
        linterIssues: List<LinterIssue>,
        formatterIssues: List<FormatterIssue>,
        range: Range
    ): List<Either<Command, CodeAction>> {
        val linterCodeActions = linterIssues.mapNotNull { issue ->
            val fix = issue.fix() ?: return@mapNotNull null
            val action = CodeAction(fix.title)
            action.kind = CodeActionKind.QuickFix
            action.edit = WorkspaceEdit(getEditsForLinterFix(fileUri, fix))
            action.diagnostics = DiagnosticsConverter.toDiagnostics(listOf(RellIssue.fromLinterIssue(issue)))
            action.isPreferred = true
            Either.forRight<Command, CodeAction>(action)
        }

        val formatterCodeActions = formatterIssues.map {
            val action = CodeAction(it.message)
            action.kind = CodeActionKind.QuickFix
            action.edit = WorkspaceEdit(getEditsForFormatterIssue(fileUri, it))
            action.diagnostics = DiagnosticsConverter.toDiagnostics(listOf(RellIssue.fromFormatterIssue(it)))
            action.isPreferred = true
            Either.forRight<Command, CodeAction>(action)
        }

        val codeActions = linterCodeActions + formatterCodeActions

        // The rule-level escape hatch: a quickfix on each diagnostic that switches the whole rule
        // off in `.rell_lint`, listed in the diagnostic's quick-fix group after the real fix.
        val disableRuleActions = buildList {
            for ((ruleId, issues) in linterIssues.groupBy { it.ruleId }) {
                add(disableRuleGloballyAction(workspaceUri, ruleId, issues.map(RellIssue::fromLinterIssue)))
            }
            if (formatterIssues.isNotEmpty()) {
                add(
                    disableRuleGloballyAction(
                        workspaceUri,
                        FORMATTER_RULE_ID,
                        formatterIssues.map(RellIssue::fromFormatterIssue),
                    )
                )
            }
        }.map { Either.forRight<Command, CodeAction>(it) }

        // Rewrites the whole file rather than the diagnostic under the cursor, so it is a source
        // action rather than a quick-fix.
        val autoFixAll = CodeAction(CodeActionTitles.AUTO_FIXABLE.title)
        autoFixAll.kind = CodeActionKind.SourceFixAll
        autoFixAll.data = mapOf("fileUri" to fileUri)
        autoFixAll.isPreferred = false
        val autoFixAllEither = Either.forRight<Command, CodeAction>(autoFixAll)

        // Suppressing the inspection is the escape hatch, never the recommended action. Emitting it
        // as a `source` action rather than a `quickfix` keeps it out of the diagnostic's quick-fix
        // group next to the real fix in clients that partition by kind.
        val disableNextLine = CodeAction(CodeActionTitles.DISABLE_LINTER.title)
        disableNextLine.kind = CodeActionKind.Source
        disableNextLine.edit = getEditsForDisableNextLine(fileUri, range)
        disableNextLine.isPreferred = false
        val disableNextLineEither = Either.forRight<Command, CodeAction>(disableNextLine)

        return if (codeActions.isNotEmpty()) {
            codeActions + disableRuleActions + disableNextLineEither + autoFixAllEither
        } else {
            if (linterIssues.isNotEmpty()) {
                disableRuleActions + disableNextLineEither
            } else {
                listOf()
            }
        }
    }

    private fun disableRuleGloballyAction(workspaceUri: URI, ruleId: String, issues: List<RellIssue>): CodeAction {
        val action = CodeAction(CodeActionTitles.disableRuleGlobally(ruleId))
        action.kind = CodeActionKind.QuickFix
        action.edit = disableRuleGloballyEdit(workspaceUri, ruleId)
        action.diagnostics = DiagnosticsConverter.toDiagnostics(issues)
        action.isPreferred = false
        return action
    }

    /**
     * Switches [ruleId] off in the `.rell_lint` the linter actually reads (workspace root or up to
     * two parent directories); when none exists, the edit creates one at the workspace root.
     * Appending the property at the end of the file wins over any earlier assignment of the same
     * key, and a section header is added to a section-less file because properties outside a
     * section are ignored by the parser.
     */
    private fun disableRuleGloballyEdit(workspaceUri: URI, ruleId: String): WorkspaceEdit {
        val configFile = RellLinterOptionsResolver.findLinterConfigFile(workspaceUri)
            ?: return createConfigWithDisabledRule(workspaceUri, ruleId)

        val content = configFile.readText()
        val hasSection = content.lineSequence().any { it.trim().startsWith("[") }
        val insertion = buildString {
            if (content.isNotEmpty() && !content.endsWith("\n")) append('\n')
            if (!hasSection) append("$SECTION_HEADER\n")
            append("$ruleId=false\n")
        }
        val lines = content.split("\n")
        val end = Position(lines.size - 1, lines.last().length)
        return WorkspaceEdit(mapOf(configFile.toURI().toString() to listOf(TextEdit(Range(end, end), insertion))))
    }

    private fun createConfigWithDisabledRule(workspaceUri: URI, ruleId: String): WorkspaceEdit {
        val configUri = File(workspaceUri).resolve(LinterOptions.CONFIG_FILE_NAME).toURI().toString()
        val insert = TextEdit(Range(Position(0, 0), Position(0, 0)), "$SECTION_HEADER\n$ruleId=false\n")
        return WorkspaceEdit(
            listOf(
                Either.forRight<TextDocumentEdit, ResourceOperation>(CreateFile(configUri)),
                Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(VersionedTextDocumentIdentifier(configUri, null), listOf(insert))
                ),
            )
        )
    }

    /** LSP `CodeActionContext.only`: a requested kind also matches its sub-kinds ("source" matches "source.fixAll"). */
    private fun matchesKindFilter(kind: String?, only: List<String>?): Boolean {
        if (only.isNullOrEmpty()) return true
        if (kind == null) return false
        return only.any { kind == it || kind.startsWith("$it.") }
    }

    private fun mergeCodeActionEdits(codeActionsEdits: List<Map<String, List<TextEdit>>>): Map<String, List<TextEdit>> {
        val result = mutableMapOf<String, MutableList<TextEdit>>()
        for (codeActionEdit in codeActionsEdits) {
            for ((key, value) in codeActionEdit) {
                if (result.containsKey(key)) {
                    result[key]!!.addAll(value)
                } else {
                    result[key] = value.toMutableList()
                }
            }
        }
        return result
    }

    private fun getEditsForLinterFix(fileUri: URI, fix: LinterFix): Map<String, List<TextEdit>> {
        val range = Range(
            Position(fix.line, fix.charPositionInLine),
            Position(fix.endLine, fix.endCharPositionInLine),
        )
        val edit = TextEdit(range, fix.newText)
        return mapOf(fileUri.toString() to listOf(edit))
    }

    private fun getEditsForFormatterIssue(
        fileUri: URI,
        formatterIssue: FormatterIssue
    ): Map<String, List<TextEdit>> {
        val edits = formatterIssue.textEdit.let {
            val range = Range(
                Position(it.range.start.line, it.range.start.character),
                Position(it.range.end.line, it.range.end.character),
            )
            listOf(TextEdit(range, it.newText))
        }
        return mapOf(fileUri.toString() to edits)
    }

    private fun getEditsForDisableNextLine(fileUri: URI, range: Range): WorkspaceEdit {
        val lspRange = Range(
            Position(range.start.line, 0),
            Position(range.start.line, 0),
        )
        val textEdits = listOf(TextEdit(lspRange, "// rell-lint-disable-next-line\n"))
        return WorkspaceEdit(mapOf(fileUri.toString() to textEdits))
    }
}
