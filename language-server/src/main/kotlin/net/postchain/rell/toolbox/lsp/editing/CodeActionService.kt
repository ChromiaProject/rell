package net.postchain.rell.toolbox.lsp.editing

import java.net.URI
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormatterIssue
import net.postchain.rell.toolbox.linter.issues.LinterIssue
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

enum class CodeActionTitles(val title: String) {
    AUTO_FIXABLE("Fix all auto-fixable issues"),
    DISABLE_LINTER("Disable linter for this line");
}


object CodeActionService {

    fun getCodeActions(fileUri: URI, range: Range, indexer: WorkspaceIndexer): List<Either<Command, CodeAction>> {
        val resource = indexer.getResource(fileUri) ?: return mutableListOf()
        val linterIssues = findLinterIssuesForRange(range, resource)
        val formatterIssues = findFormatterIssuesForRange(range, resource)
        return createCodeActions(fileUri, linterIssues, formatterIssues, range)
    }

    fun getCodeActionForFile(fileUri: URI, indexer: WorkspaceIndexer): CodeAction {
        val resource = indexer.getResource(fileUri) ?: return CodeAction()
        val codeAction = CodeAction(CodeActionTitles.AUTO_FIXABLE.title)
        codeAction.kind = "quickfix"

        val linterEdits = resource.linterIssues.map {
            getEditsForLinterIssue(fileUri, it)
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
        linterIssues: List<LinterIssue>,
        formatterIssues: List<FormatterIssue>,
        range: Range
    ): List<Either<Command, CodeAction>> {
        val linterCodeActions = linterIssues.filter {
            it.fix() != null
        }.map {
            val action = CodeAction(it.message)
            action.kind = "quickfix"
            action.edit = WorkspaceEdit(getEditsForLinterIssue(fileUri, it))
            Either.forRight<Command, CodeAction>(action)
        }
        val formatterCodeActions = formatterIssues.map {
            val action = CodeAction(it.message)
            action.kind = "quickfix"
            action.edit = WorkspaceEdit(getEditsForFormatterIssue(fileUri, it))
            Either.forRight<Command, CodeAction>(action)
        }
        val codeActions = linterCodeActions + formatterCodeActions

        val autoFixAll = CodeAction(CodeActionTitles.AUTO_FIXABLE.title)
        autoFixAll.kind = "quickfix"
        autoFixAll.data = mapOf("fileUri" to fileUri)
        val autoFixAllEither = Either.forRight<Command, CodeAction>(autoFixAll)

        val disableNextLine = CodeAction(CodeActionTitles.DISABLE_LINTER.title)
        disableNextLine.kind = "quickfix"
        disableNextLine.edit = getEditsForDisableNextLine(fileUri, range)
        val disableNextLineEither = Either.forRight<Command, CodeAction>(disableNextLine)

        return if (codeActions.isNotEmpty()) {
            codeActions + disableNextLineEither + autoFixAllEither
        } else {
            if (linterIssues.isNotEmpty()) {
                listOf(disableNextLineEither)
            } else {
                listOf()
            }
        }
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

    private fun getEditsForLinterIssue(fileUri: URI, linterIssue: LinterIssue): Map<String, List<TextEdit>> {
        val fix = linterIssue.fix() ?: return mapOf(fileUri.toString() to listOf())
        val range = Range(
            Position(fix.line, fix.charPositionInLine),
            Position(fix.line, fix.charPositionInLine + fix.length)
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
