package net.postchain.rell.toolbox.lsp.server

import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandCapabilities
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities


class CapabilitiesProvider {

    fun createServerCapabilities(params: InitializeParams): ServerCapabilities {
        val serverCapabilities = ServerCapabilities()
        //serverCapabilities.setHoverProvider(true)
        serverCapabilities.setDefinitionProvider(true)
        serverCapabilities.setReferencesProvider(true)
        //serverCapabilities.setDocumentSymbolProvider(true)
        serverCapabilities.setWorkspaceSymbolProvider(true)
        serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)

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
        // TODO: rename options
        // TODO: execute command options

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