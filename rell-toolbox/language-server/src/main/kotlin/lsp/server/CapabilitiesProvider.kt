/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

class CapabilitiesProvider(private val lspSystemPropertiesProvider: LspSystemPropertiesProvider) {

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
            setInlayHintProvider(true)
        }

        val clientCapabilities = params.capabilities

        serverCapabilities.completionProvider = CompletionOptions().apply {
            resolveProvider = lspSystemPropertiesProvider.getResolveCompletion()
            // TODO: comment out when dot completion is implemented
            // triggerCharacters = listOf(".")
        }

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
