package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import net.postchain.rell.toolbox.indexer.IndexingState
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsConfig
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsManager
import net.postchain.rell.toolbox.lsp.server.events.FileEventsBatcher
import net.postchain.rell.toolbox.lsp.server.events.FileEventsProcessor
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class RellWorkspaceService(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val indexingManager: RellIndexingManager,
    private val diagnosticsManager: RellDiagnosticsManager,
    private val symbolService: RellSymbolService,
    private val inlayHintsManager: RellInlayHintsManager
) : WorkspaceService, LanguageClientAware {
    private lateinit var languageClient: LanguageClient
    private lateinit var fileEventsBatcher: FileEventsBatcher
    private lateinit var fileEventsProcessor: FileEventsProcessor

    override fun connect(client: LanguageClient) {
        this.languageClient = client

        fileEventsBatcher = FileEventsBatcher()
        fileEventsProcessor = FileEventsProcessor(fileEventsBatcher, this::processFileChangeEvents)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        requestManager.runWrite {
            processConfigurationChanges(params)
        }
    }

    private fun processConfigurationChanges(params: DidChangeConfigurationParams) {
        val rellSettings = (params.settings as? JsonObject) ?: return

        rellSettings["indexCaching"]?.asBoolean?.let { enabled ->
            indexingManager.indexCachingEnabled = enabled
            indexingManager.runIndexers()
        }

        rellSettings.get("inlayHints")?.asJsonObject?.let { hints ->
            updateInlayHintsConfig(hints)
        }
    }

    private fun updateInlayHintsConfig(changes: JsonObject) {
        val current = inlayHintsManager.inlayHintsConfig

        val newConfig = RellInlayHintsConfig(
            isParameterNamesEnabled = changes["parameterHints"]?.asBoolean ?: current.isParameterNamesEnabled,
            isReturnTypesEnabled = changes["returnTypeHints"]?.asBoolean ?: current.isReturnTypesEnabled,
            isVariableTypesEnabled = changes["variableTypeHints"]?.asBoolean ?: current.isVariableTypesEnabled
        )

        inlayHintsManager.updateConfig(newConfig)
        languageClient.refreshInlayHints()
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        fileEventsBatcher.addChanges(params.changes)
    }

    private fun processFileChangeEvents(changes: List<FileEvent>) {
        val dirtyFiles = mutableListOf<URI>()
        val deletedFiles = mutableListOf<URI>()
        val dirtyFolders = mutableListOf<URI>()
        val deletedFolders = mutableListOf<URI>()
        val createdChromiaConfig = mutableListOf<URI>()
        val configFilesToProcess = mutableListOf<Pair<URI, WorkspaceIndexer>>()

        for (change in changes) {
            val uri = parseFileUri(change.uri) ?: continue

            when {
                uri.isRellFile() -> {
                    when (change.type) {
                        FileChangeType.Created -> {
                            dirtyFiles.add(uri)
                        }

                        FileChangeType.Changed -> {
                            if (indexingManager.resourceHasChanged(uri)) {
                                dirtyFiles.add(uri)
                            }
                        }

                        FileChangeType.Deleted -> {
                            deletedFiles.add(uri)
                        }
                    }
                }

                uri.isChromiaConfig() && change.type == FileChangeType.Created -> {
                    createdChromiaConfig.add(uri)
                }

                uri.isDependencyMarkerFile() &&
                    change.type in setOf(FileChangeType.Changed, FileChangeType.Created) -> {
                    indexingManager.runIndexers(::handleIndexingState, skipCache = true)
                }

                else -> {
                    val indexer = indexingManager.getIndexerForConfigFile(uri)
                    if (indexer != null && indexer.isConfigFile(uri)) {
                        configFilesToProcess.add(uri to indexer)
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
        }

        for ((uri, indexer) in configFilesToProcess) {
            indexer.updateConfig(uri, ::handleIndexingState)
            diagnosticsManager.reportDiagnostics(indexer)
        }

        if (!shouldProcessChanges(dirtyFiles, deletedFiles, dirtyFolders, createdChromiaConfig)) {
            return
        }

        workspaceManager.didCreateChromiaConfig(createdChromiaConfig)
        workspaceManager.didChangeFolders(dirtyFolders, deletedFolders)
        workspaceManager.didChangeFiles(dirtyFiles, deletedFiles, updateAffectedFiles = true)
        languageClient.refreshSemanticTokens()
    }

    private fun shouldProcessChanges(
        dirtyFiles: List<URI>,
        deletedFiles: List<URI>,
        dirtyFolders: List<URI>,
        createdChromiaConfig: List<URI>
    ): Boolean {
        return dirtyFiles.isNotEmpty() ||
            deletedFiles.isNotEmpty() ||
            dirtyFolders.isNotEmpty() ||
            createdChromiaConfig.isNotEmpty()
    }

    override fun symbol(params: WorkspaceSymbolParams):
        CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
        return CompletableFuture.completedFuture(
            Either.forRight(symbolService.getWorkspaceSymbols(params.query, indexingManager.getAllIndexers()))
        )
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

    fun shutdown() {
        if (this::fileEventsBatcher.isInitialized) {
            fileEventsBatcher.shutdown()
        }
        if (this::fileEventsProcessor.isInitialized) {
            fileEventsProcessor.shutdown()
        }
    }
}
