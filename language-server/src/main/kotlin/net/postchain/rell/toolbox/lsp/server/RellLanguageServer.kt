package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.RellAbout
import net.postchain.rell.toolbox.core.RellVersionInfo
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.core.tokens.RellSemanticTokensManager
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsConverter
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.util.concurrent.CompletableFuture


class RellLanguageServer(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val languageServerTerminator: RellLanguageServerTerminator,
    private val capabilitiesProvider: CapabilitiesProvider,
    private val semanticTokensManager: RellSemanticTokensManager,
    private val formattingManager: RellFormattingManager,
    private val indexCachingService: RellIndexCachingService
) : LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService {

    private val logger = KotlinLogging.logger {}

    private lateinit var languageClient: LanguageClient
    private lateinit var initializeParams: InitializeParams
    val initialized = CompletableFuture<InitializedParams>()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        if (this::initializeParams.isInitialized) {
            throw IllegalStateException("Rell language server has already been initialized.")
        }
        initializeParams = params
        val workspaceFolders = params.workspaceFolders ?: listOf()
        val result = InitializeResult()

        result.capabilities = capabilitiesProvider.createServerCapabilities(params)

        processInitializationOptions(params.initializationOptions)

        return requestManager.runWrite {
            workspaceManager.initialize(workspaceFolders, ::publishDiagnostics)
            result
        }
    }

    private fun processInitializationOptions(initializationOptions: Any?) {
        if (initializationOptions != null && initializationOptions is JsonObject) {
            workspaceManager.indexCachingEnabled = initializationOptions.get("indexCaching")?.asBoolean ?: false
        }
    }

    override fun initialized(params: InitializedParams) {
        initialized.complete(params)
    }

    private fun publishDiagnostics(uri: URI, issues: List<RellIssue>) {
        initialized.thenAccept { _ ->
            val publishDiagnosticsParams = PublishDiagnosticsParams()
            publishDiagnosticsParams.uri = uri.toString()
            publishDiagnosticsParams.diagnostics = DiagnosticsConverter.toDiagnostics(issues)

            languageClient.publishDiagnostics(publishDiagnosticsParams)
        }
    }

    @JsonRequest(useSegment = false, value = "rell/about")
    fun about(): CompletableFuture<RellAbout> {
        return CompletableFuture.completedFuture(RellVersionInfo.getAbout())
    }

    @JsonRequest(useSegment = false, value = "rell/invalidateCaches")
    fun invalidateCaches(): CompletableFuture<Boolean> {
        return requestManager.runWrite {
            indexCachingService.invalidateCaches()
        }
    }

    @JsonRequest(useSegment = false, value = "rell/cacheFolder")
    fun cacheFolder(): CompletableFuture<String> {
        return requestManager.runRead {
            indexCachingService.getCacheFolder().toString()
        }
    }

    override fun shutdown(): CompletableFuture<Any> {
        languageServerTerminator.shutdown()
        return CompletableFuture.completedFuture(Any())
    }

    override fun exit() {
        languageServerTerminator.exit()
    }

    override fun getTextDocumentService(): TextDocumentService {
        return this
    }

    override fun getWorkspaceService(): WorkspaceService {
        return this
    }

    override fun connect(client: LanguageClient) {
        languageClient = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val textDocument = params.textDocument
        val uri = URI(textDocument.uri)
        workspaceManager.didOpen(uri, textDocument.version, textDocument.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val textDocument: VersionedTextDocumentIdentifier = params.textDocument
        val uri = URI(textDocument.uri)
        workspaceManager.didChangeTextDocumentContent(
            uri, textDocument.version, params.contentChanges
        )
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = URI(params.textDocument.uri)
        requestManager.runWrite {
            workspaceManager.didClose(uri)
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        val uri = URI(params.textDocument.uri)
        requestManager.runWrite {
            workspaceManager.didSave(uri)
        }
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        requestManager.runWrite {
            workspaceManager.runIndexers()
        }
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        val dirtyFiles = mutableListOf<URI>()
        val deletedFiles = mutableListOf<URI>()
        params.changes.forEach { change ->
            val uri = URI(change.uri)
            if (change.type == FileChangeType.Deleted) {
                deletedFiles.add(uri)
            } else {
                dirtyFiles.add(uri)
            }
        }

        requestManager.runWrite {
            workspaceManager.didChangeFiles(dirtyFiles, deletedFiles, updateAffectedFiles = true)
        }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = URI(params.textDocument.uri)

        return requestManager.runRead {
            val resource = workspaceManager.getResource(uri)
            if (resource != null) {
                SemanticTokens(semanticTokensManager.getRelativeSemanticTokens(resource))
            } else {
                SemanticTokens()
            }
        }
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val fileUri = URI(params.textDocument.uri)

        return requestManager.runRead {
            workspaceManager.getDefinitionLocations(fileUri, params.position)
        }
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val fileUri = URI(params.textDocument.uri)
        return requestManager.runRead {
            workspaceManager.getDocumentSymbols(fileUri)
        }
    }


    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        val fileUri = URI(params.textDocument.uri)
        return requestManager.runRead {
            formattingManager.format(fileUri, params.options)
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        val fileUri = URI(params.textDocument.uri)
        return requestManager.runRead {
            formattingManager.rangeFormat(fileUri, params.range, params.options)
        }
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        val fileUri = URI(params.textDocument.uri)
        return requestManager.runRead {
            workspaceManager.getReferenceLocations(fileUri, params.position)
        }
    }

    override fun setTrace(params: SetTraceParams?) {
        logger.info { "Trace level set to ${params?.value}" }
    }
}
