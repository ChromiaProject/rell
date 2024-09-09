package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either

class CapabilitiesProvider {

    fun createServerCapabilities(params: InitializeParams): ServerCapabilities {
        val serverCapabilities = ServerCapabilities().apply {
            setHoverProvider(true)
            setDefinitionProvider(true)
            setReferencesProvider(true)
            setDocumentSymbolProvider(true)
            setWorkspaceSymbolProvider(true)
            setTextDocumentSync(TextDocumentSyncKind.Incremental)
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                legend = SemanticTokensLegend(
                    RellSemanticTokensManager.semanticTokens,
                    RellSemanticTokensManager.tokenModifiers
                )
                setRange(false)
                setFull(true)
            }

            setDocumentFormattingProvider(true)
            setDocumentRangeFormattingProvider(true)
            // setCodeActionProvider(true)
            setCodeActionProvider(
                CodeActionOptions(
                    listOf(
                        CodeActionKind.QuickFix,
                        CodeActionKind.Refactor,
                        CodeActionKind.Source,
                        CodeActionKind.SourceOrganizeImports,
                        CodeActionKind.SourceFixAll,
                        CodeActionKind.RefactorExtract,
                        CodeActionKind.RefactorInline,
                        CodeActionKind.RefactorRewrite,
                    )
                ).apply { this.resolveProvider = true }
            )
        }

        val clientCapabilities = params.capabilities

        // TODO: folding options
        // TODO: code lens options
        // TODO: code action options
        // TODO: execute command options

        clientCapabilities?.textDocument?.rename?.let {
            val renameOptions = RenameOptions(true)
            serverCapabilities.renameProvider = Either.forRight(renameOptions)
        }

        val workspace = clientCapabilities?.workspace
        workspace?.let {
            if (workspace.workspaceFolders) {
                val workspaceFoldersOptions = WorkspaceFoldersOptions()
                workspaceFoldersOptions.supported = true
                workspaceFoldersOptions.setChangeNotifications(true)
                serverCapabilities.workspace = WorkspaceServerCapabilities(workspaceFoldersOptions)
            }
        }

        return serverCapabilities
    }
}
