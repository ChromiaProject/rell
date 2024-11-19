package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.indexer.IndexingState
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.File
import java.net.URI

class RellWorkspaceService(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val indexingManager: RellIndexingManager,
    private val diagnosticsManager: RellDiagnosticsManager,
) : WorkspaceService, LanguageClientAware {
    private lateinit var languageClient: LanguageClient

    override fun connect(client: LanguageClient) {
        this.languageClient = client
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        requestManager.runWrite {
            indexingManager.runIndexers()
        }
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        val dirtyFiles = mutableListOf<URI>()
        val deletedFiles = mutableListOf<URI>()
        val dirtyFolders = mutableListOf<URI>()
        val deletedFolders = mutableListOf<URI>()
        for (change in params.changes) {
            val uri = parseFileUri(change.uri) ?: continue
            if (uri.isRellFile()) {
                if (change.type == FileChangeType.Deleted) {
                    deletedFiles.add(uri)
                } else {
                    dirtyFiles.add(uri)
                }
            } else {
                val indexer = indexingManager.getIndexerForConfigFile(uri)
                if (indexer != null && indexer.isConfigFile(uri)) {
                    requestManager.runWrite {
                        indexer.updateConfig(uri, ::handleIndexingState)
                        diagnosticsManager.reportDiagnostics(indexer)
                    }
                } else {
                    if (change.type == FileChangeType.Deleted) {
                        deletedFolders.add(uri)
                    } else {
                        if (File(uri).isDirectory) {
                            dirtyFolders.add(uri)
                        }
                    }
                }
            }
        }

        requestManager.runWrite {
            workspaceManager.didChangeFiles(dirtyFiles, deletedFiles, updateAffectedFiles = true)
            workspaceManager.didChangeFolders(dirtyFolders, deletedFolders)
            languageClient.refreshSemanticTokens()
        }
    }

    private fun handleIndexingState(state: IndexingState) {
        val token = "rell-indexing"

        if (state == IndexingState.BEGIN) {
            val startIndexingProgress = ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressBegin().apply { title = token })
            )
            languageClient.notifyProgress(startIndexingProgress)
        }
        if (state == IndexingState.END) {
            val endIndexingProgress = ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressEnd()))
            languageClient.notifyProgress(endIndexingProgress)
        }
    }
}
