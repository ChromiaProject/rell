package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import net.postchain.rell.toolbox.lsp.editing.CodeActionTitles
import net.postchain.rell.toolbox.lsp.includeDefinition.LspIncludeDefinitionProvider
import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class RellTextDocumentService(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val semanticTokensManager: RellSemanticTokensManager,
    private val formattingManager: RellFormattingManager,
    private val indexingManager: RellIndexingManager,
    private val lspIncludeDefinitionProvider: LspIncludeDefinitionProvider
) : TextDocumentService {
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val textDocument = params.textDocument
        val uri = parseFileUri(textDocument.uri) ?: return
        if (uri.isRellFile()) {
            workspaceManager.didOpen(uri, textDocument.version, textDocument.text)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val textDocument: VersionedTextDocumentIdentifier = params.textDocument
        val uri = parseFileUri(textDocument.uri) ?: return
        if (uri.isRellFile()) {
            workspaceManager.didChangeTextDocumentContent(
                uri,
                params.contentChanges
            )
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = parseFileUri(params.textDocument.uri) ?: return
        if (uri.isRellFile()) {
            requestManager.runWrite {
                workspaceManager.didClose(uri)
            }
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        val uri = parseFileUri(params.textDocument.uri) ?: return
        if (uri.isRellFile()) {
            requestManager.runWrite {
                workspaceManager.didSave(uri)
            }
        }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(SemanticTokens())

        return requestManager.runRead {
            val resource = indexingManager.getResource(uri)
            if (uri.isRellFile() && resource != null) {
                SemanticTokens(semanticTokensManager.getRelativeSemanticTokens(resource))
            } else {
                SemanticTokens()
            }
        }
    }

    override fun definition(params: DefinitionParams):
        CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>?> {
        val fileUri = parseFileUri(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))

        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                workspaceManager.getDefinitionLocations(fileUri, params.position)
            } else {
                null
            }
        }
    }

    override fun documentSymbol(params: DocumentSymbolParams):
        CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>?> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                workspaceManager.getDocumentSymbols(fileUri)
            } else {
                null
            }
        }
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        return requestManager.runRead {
            Hover(workspaceManager.getHoverDocumentation(params))
        }
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>?> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                formattingManager.format(fileUri, params.options)
            } else {
                null
            }
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>?> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                formattingManager.rangeFormat(fileUri, params.range, params.options)
            } else {
                null
            }
        }
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>?> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                workspaceManager.getReferenceLocations(
                    fileUri,
                    params.position,
                    lspIncludeDefinitionProvider.getIncludeDefinition()
                )
            } else {
                null
            }
        }
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        val newName = params.newName
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(WorkspaceEdit())
        val position = params.position

        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                workspaceManager.rename(fileUri, position, newName)
            } else {
                WorkspaceEdit()
            }
        }
    }

    override fun prepareRename(params: PrepareRenameParams):
        CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
        val fileUri = parseFileUri(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either3.forThird(PrepareRenameDefaultBehavior(true)))
        val position = params.position

        return requestManager.runRead {
            if (fileUri.isRellFile()) {
                workspaceManager.prepareRename(fileUri, position)
            } else {
                Either3.forThird(PrepareRenameDefaultBehavior(true))
            }
        }
    }

    override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
        val data = unresolved.data as? JsonObject ?: return CompletableFuture.completedFuture(CodeAction())
        val fileUri = parseFileUri(data.get("fileUri").asString)
            ?: return CompletableFuture.completedFuture(CodeAction())

        return if (unresolved.title == CodeActionTitles.AUTO_FIXABLE.title) {
            return CompletableFuture.completedFuture(workspaceManager.getCodeActionForFile(fileUri))
        } else {
            CompletableFuture.completedFuture(CodeAction())
        }
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return CompletableFuture.completedFuture(workspaceManager.getCodeActions(fileUri, params.range))
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val fileUri = parseFileUri(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either.forLeft(listOf()))
        return CompletableFuture.completedFuture(
            Either.forLeft(workspaceManager.getCompletions(fileUri, params.position))
        )
    }
}
