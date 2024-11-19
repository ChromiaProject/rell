package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.common.RellAbout
import net.postchain.rell.toolbox.common.RellVersionInfo
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsConverter
import net.postchain.rell.toolbox.lsp.template.CreateNewProjectParams
import net.postchain.rell.toolbox.lsp.template.NewProjectTemplate
import net.postchain.rell.toolbox.lsp.template.NewProjectTemplateService
import net.postchain.rell.toolbox.lsp.testrunner.RellTestCase
import net.postchain.rell.toolbox.lsp.testrunner.RellTestFile
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.util.getCurrentLogFileName
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class RellLanguageServer(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val languageServerTerminator: RellLanguageServerTerminator,
    private val capabilitiesProvider: CapabilitiesProvider,
    private val indexCachingService: RellIndexCachingService,
    private val testRunner: RellTestRunner,
    private val newProjectTemplateService: NewProjectTemplateService,
    private val textDocumentService: RellTextDocumentService,
    private val workspaceService: RellWorkspaceService,
    private val indexingManager: RellIndexingManager,
) : LanguageServer, LanguageClientAware {

    private val logger = KotlinLogging.logger {}

    private lateinit var languageClient: LanguageClient
    private lateinit var initializeParams: InitializeParams
    val initialized = CompletableFuture<InitializedParams>()

    override fun getTextDocumentService() = textDocumentService
    override fun getWorkspaceService() = workspaceService

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        check(!this::initializeParams.isInitialized) { "Rell language server has already been initialized." }

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
            indexingManager.indexCachingEnabled = initializationOptions.get("indexCaching")?.asBoolean == true
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

    @JsonRequest(useSegment = false, value = "rell/newProjectTemplates")
    fun listNewProjectTemplates(): CompletableFuture<List<NewProjectTemplate>> {
        return CompletableFuture.completedFuture(newProjectTemplateService.getAvailableTemplates())
    }

    @JsonRequest(useSegment = false, value = "rell/createNewProject")
    fun createNewProject(params: CreateNewProjectParams): CompletableFuture<String> {
        val targetDir = File(parseFileUri(params.targetDirUri) ?: return CompletableFuture.completedFuture(null))
        val projectDir =
            newProjectTemplateService.createNewProjectTemplate(params.template, params.projectName, targetDir)
        return CompletableFuture.completedFuture(projectDir.absolutePath)
    }

    override fun shutdown(): CompletableFuture<Any> {
        languageServerTerminator.shutdown()
        return CompletableFuture.completedFuture(Any())
    }

    override fun exit() {
        languageServerTerminator.exit()
    }

    override fun connect(client: LanguageClient) {
        languageClient = client
        workspaceService.connect(client)
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
}
