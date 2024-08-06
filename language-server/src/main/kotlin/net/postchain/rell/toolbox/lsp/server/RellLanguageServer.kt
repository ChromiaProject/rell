package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import net.postchain.rell.toolbox.core.RellAbout
import net.postchain.rell.toolbox.core.RellVersionInfo
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.core.tokens.RellSemanticTokensManager
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsConverter
import net.postchain.rell.toolbox.lsp.editing.CodeActionTitles
import net.postchain.rell.toolbox.lsp.testrunner.RellTestCase
import net.postchain.rell.toolbox.lsp.testrunner.RellTestFile
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.util.getCurrentLogFileName
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService


class RellLanguageServer(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val languageServerTerminator: RellLanguageServerTerminator,
    private val capabilitiesProvider: CapabilitiesProvider,
    private val semanticTokensManager: RellSemanticTokensManager,
    private val formattingManager: RellFormattingManager,
    private val indexCachingService: RellIndexCachingService,
    private val testRunner: RellTestRunner,
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

        val currentLogFileName = File(getCurrentLogFileName())
        val message =
            MessageParams(
                MessageType.Info,
                "Rell Language Server logs will be written in: ${currentLogFileName.parent}"
            )
        languageClient.logMessage(message)

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
                uri, textDocument.version, params.contentChanges
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

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        requestManager.runWrite {
            workspaceManager.runIndexers()
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
                val indexer = workspaceManager.getIndexerForConfigFile(uri)
                if (indexer != null && indexer.isConfigFile(uri)) {
                    requestManager.runWrite {
                        indexer.updateConfig(uri)
                        workspaceManager.runLinter()
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

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(SemanticTokens())

        return requestManager.runRead {
            val resource = workspaceManager.getResource(uri)
            if (uri.isRellFile() && resource != null) {
                SemanticTokens(semanticTokensManager.getRelativeSemanticTokens(resource))
            } else {
                SemanticTokens()
            }
        }
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>?> {
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

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>?> {
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
                workspaceManager.getReferenceLocations(fileUri, params.position)
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

    override fun prepareRename(params: PrepareRenameParams): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
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

    override fun setTrace(params: SetTraceParams?) {
        logger.info { "Trace level set to ${params?.value}" }
    }

    @JsonRequest(useSegment = false, value = "rell/listTestFiles")
    fun getTestFiles(workspaceUri: String): CompletableFuture<List<RellTestFile>> {
        val parsedUri = parseFileUri(workspaceUri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            testRunner.getTestFiles(parsedUri)
        }
    }

    @JsonRequest(useSegment = false, value = "rell/listTestCases")
    fun getTestCases(testFileUri: String): CompletableFuture<List<RellTestCase>> {
        val fileUri = parseFileUri(testFileUri) ?: return CompletableFuture.completedFuture(listOf())
        return requestManager.runRead {
            testRunner.getTestCases(fileUri)
        }
    }

    override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
        val codeActionTitle = unresolved.title
        val fileUri = parseFileUri((unresolved.data as JsonObject).get("fileUri").asString)
            ?: return CompletableFuture.completedFuture(CodeAction())
        return if (codeActionTitle == CodeActionTitles.AUTO_FIXABLE.title) {
            return CompletableFuture.completedFuture(workspaceManager.getCodeActionForFile(fileUri))
        } else {
            CompletableFuture.completedFuture(CodeAction())
        }
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return CompletableFuture.completedFuture(listOf())
        return CompletableFuture.completedFuture(workspaceManager.getCodeActions(fileUri, params.range))
    }
}
