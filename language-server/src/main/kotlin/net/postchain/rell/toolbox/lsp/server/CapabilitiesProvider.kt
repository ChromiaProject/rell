package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.core.tokens.RellSemanticTokensManager
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
        val serverCapabilities = ServerCapabilities()
        //serverCapabilities.setHoverProvider(true)
        serverCapabilities.setDefinitionProvider(true)
        serverCapabilities.setReferencesProvider(true)
        serverCapabilities.setDocumentSymbolProvider(true)
        serverCapabilities.setWorkspaceSymbolProvider(true)
        //serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)

        val semanticTokensOptions = SemanticTokensWithRegistrationOptions()
        semanticTokensOptions.legend = SemanticTokensLegend(
            RellSemanticTokensManager.semanticTokens.tokenTypes,
            RellSemanticTokensManager.semanticTokens.tokenModifiers
        )
        semanticTokensOptions.setRange(false)
        semanticTokensOptions.setFull(true)
        serverCapabilities.semanticTokensProvider = semanticTokensOptions

//        val completionOptions = CompletionOptions()
//        completionOptions.resolveProvider = false
//        completionOptions.triggerCharacters = listOf(".")
//        serverCapabilities.completionProvider = completionOptions

        serverCapabilities.setDocumentFormattingProvider(true)
        serverCapabilities.setDocumentRangeFormattingProvider(true)
//        serverCapabilities.setDocumentHighlightProvider(true)

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