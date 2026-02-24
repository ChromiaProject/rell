package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.common.RellAbout
import net.postchain.rell.toolbox.common.RellVersionInfo
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsPublisher
import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.template.AddToProjectParams
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsConfig
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsManager
import net.postchain.rell.toolbox.lsp.template.CreateNewProjectParams
import net.postchain.rell.toolbox.lsp.template.NewProjectTemplate
import net.postchain.rell.toolbox.lsp.template.ProjectTemplateService
import net.postchain.rell.toolbox.lsp.testrunner.RellTestCase
import net.postchain.rell.toolbox.lsp.testrunner.RellTestFile
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.util.getCurrentLogFileName
import org.eclipse.lsp4j.DidChangeConfigurationCapabilities
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RelativePattern
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.WatchKind
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

class RellLanguageServer(
    private val workspaceManager: RellWorkspaceManager,
    private val requestManager: RellRequestManager,
    private val languageServerTerminator: RellLanguageServerTerminator,
    private val capabilitiesProvider: CapabilitiesProvider,
    private val indexCachingService: RellIndexCachingService,
    private val testRunner: RellTestRunner,
    private val projectTemplateService: ProjectTemplateService,
    private val textDocumentService: RellTextDocumentService,
    private val workspaceService: RellWorkspaceService,
    private val indexingManager: RellIndexingManager,
    private val lspSystemPropertiesProvider: LspSystemPropertiesProvider,
    private val inlayHintManager: RellInlayHintsManager,
) : LanguageServer, LanguageClientAware {

    private val logger = KotlinLogging.logger {}

    private lateinit var languageClient: LanguageClient
    private lateinit var initializeParams: InitializeParams
    val initialized = CompletableFuture<InitializedParams>()
    private lateinit var diagnosticsPublisher: DiagnosticsPublisher

    override fun getTextDocumentService() = textDocumentService
    override fun getWorkspaceService() = workspaceService

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        check(!this::initializeParams.isInitialized) { "Rell language server has already been initialized." }

        initializeParams = params
        val workspaceFolders = params.workspaceFolders ?: listOf()
        val result = InitializeResult()

        result.capabilities = capabilitiesProvider.createServerCapabilities(params)

        processInitializationOptions(params.initializationOptions)

        registerFileWatchers(params.workspaceFolders)

        val currentLogFileName = File(getCurrentLogFileName())
        val message =
            MessageParams(
                MessageType.Info,
                "Rell Language Server logs will be written in: ${currentLogFileName.parent}"
            )
        languageClient.logMessage(message)

        return requestManager.runWrite {
            workspaceManager.initialize(workspaceFolders, diagnosticsPublisher, ::sendNotification)
            result
        }
    }

    private fun registerFileWatchers(workspaceFolders: List<WorkspaceFolder>?) {
        workspaceFolders?.forEach { folder ->
            val allFilesWatcher = FileSystemWatcher()
            allFilesWatcher.globPattern = Either.forRight(RelativePattern(Either.forLeft(folder), "**/*"))
            allFilesWatcher.kind = WatchKind.Create or WatchKind.Change or WatchKind.Delete

            val registrationOptions = DidChangeWatchedFilesRegistrationOptions(listOf(allFilesWatcher))
            val registration = Registration(
                UUID.randomUUID().toString(),
                "workspace/didChangeWatchedFiles",
                registrationOptions
            )
            languageClient.registerCapability(RegistrationParams(listOf(registration)))
        }
    }

    private fun processInitializationOptions(initializationOptions: Any?) {
        if (initializationOptions != null && initializationOptions is JsonObject) {
            indexingManager.indexCachingEnabled = initializationOptions.get("indexCaching")?.asBoolean == true
            val inlayHintsJson = initializationOptions.get("inlayHints")?.asJsonObject
            val inlayHintsConfig = RellInlayHintsConfig(
                isVariableTypesEnabled = inlayHintsJson?.get("variableTypeHints")?.asBoolean ?: false,
                isReturnTypesEnabled = inlayHintsJson?.get("returnTypeHints")?.asBoolean ?: false,
                isParameterNamesEnabled = inlayHintsJson?.get("parameterHints")?.asBoolean ?: false
            )
            inlayHintManager.updateConfig(inlayHintsConfig)
        }
    }

    override fun initialized(params: InitializedParams) {
        initialized.complete(params)
    }

    private fun sendNotification(type: NotificationType, message: String) {
        initialized.thenAccept { _ ->
            val messageType = when (type) {
                NotificationType.INFO -> MessageType.Info
                NotificationType.WARNING -> MessageType.Warning
                NotificationType.ERROR -> MessageType.Error
                NotificationType.LOG -> MessageType.Log
            }

            val messageParams = MessageParams(messageType, message)
            languageClient.showMessage(messageParams)
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
        return CompletableFuture.completedFuture(projectTemplateService.getAvailableTemplates())
    }

    @JsonRequest(useSegment = false, value = "rell/createNewProject")
    fun createNewProject(params: CreateNewProjectParams): CompletableFuture<String> {
        val targetDir = File(parseFileUri(params.targetDirUri) ?: return CompletableFuture.completedFuture(null))
        val projectDir =
            projectTemplateService.createNewProjectTemplate(
                params.template,
                params.projectName,
                targetDir,
                params.options
            )
        return CompletableFuture.completedFuture(projectDir.absolutePath)
    }

    @JsonRequest(useSegment = false, value = "rell/addToProject")
    fun addToProject(params: AddToProjectParams): CompletableFuture<Void> {
        val targetDir = File(parseFileUri(params.targetDirUri) ?: return CompletableFuture.completedFuture(null))
        projectTemplateService.addToProject(targetDir, params.options)
        return CompletableFuture.completedFuture(null)
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
        diagnosticsPublisher = DiagnosticsPublisher(client, initialized, lspSystemPropertiesProvider.getIssueCaching())
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

    @JsonRequest(useSegment = false, value = "rell/getTestFile")
    fun getTestFile(fileUri: String): CompletableFuture<RellTestFile?> {
        val parsedUri = parseFileUri(fileUri) ?: return CompletableFuture.completedFuture(null)
        return requestManager.runRead {
            testRunner.getTestFile(parsedUri)
        }
    }
}
