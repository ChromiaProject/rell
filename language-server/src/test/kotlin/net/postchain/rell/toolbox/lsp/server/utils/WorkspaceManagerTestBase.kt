package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsPublisher
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsManager
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.server.NotificationType
import net.postchain.rell.toolbox.lsp.server.RellDiagnosticsManager
import net.postchain.rell.toolbox.lsp.server.RellDocumentManager
import net.postchain.rell.toolbox.lsp.server.RellIndexingManager
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import net.postchain.rell.toolbox.lsp.symbols.RellCompletionSymbolService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

open class WorkspaceManagerTestBase {
    protected lateinit var workspaceManager: RellWorkspaceManager
    protected lateinit var indexingManager: RellIndexingManager
    protected var diagnostics = mutableMapOf<URI, List<RellIssue>>()
    protected var notifications = mutableListOf<Pair<NotificationType, String>>()
    protected lateinit var workspace: File
    protected lateinit var sourceDir: File
    protected val symbolService = RellSymbolService()
    protected val completionSymbolService = RellCompletionSymbolService(RellSymbolService())
    protected val documentManager = RellDocumentManager()
    protected val diagnosticsManager = RellDiagnosticsManager()
    protected val diagnosticsPublisher = object : DiagnosticsPublisher(null, CompletableFuture.completedFuture(null)) {
        override fun publishDiagnostics(uri: URI, issues: List<RellIssue>) {
            diagnostics[uri] = issues
        }
    }
    protected lateinit var inlayHintManager: RellInlayHintsManager

    protected val rellLinter = RellLinter()
    protected val formattingStyleLinter = FormattingStyleLinter()
    val TEST_WORKSPACE_NAME = "testWorkspace"

    @BeforeEach
    fun setup(@TempDir tempWorkspace: File) {
        workspace = tempWorkspace
        sourceDir = File(workspace, "src").apply { mkdir() }
        val referenceService = RellReferenceService(symbolService)
        val indexCachingService = RellIndexCachingService(
            RellIndexSerializer(
                rellLinter,
                formattingStyleLinter,
                RellFormatterOptionsResolver(),
                RellLinterOptionsResolver()
            )
        )

        indexingManager = RellIndexingManager(
            indexCachingService,
            diagnosticsManager,
            rellLinter,
            formattingStyleLinter,
            RellFormatterOptionsResolver(),
            RellLinterOptionsResolver(),
        )

        workspaceManager =
            RellWorkspaceManager(
                symbolService,
                referenceService,
                RellCompletionService(completionSymbolService),
                documentManager,
                indexingManager,
                diagnosticsManager,
            )
        inlayHintManager = RellInlayHintsManager(
            indexingManager,
            RellInlayHintsProvider()
        )
    }

    @AfterEach
    fun breakdown() {
        diagnostics.clear()
        notifications.clear()
    }

    protected fun populateNotifications(type: NotificationType, message: String) {
        notifications.add(type to message)
    }

    protected fun initializeWorkspace(workspace: File = this.workspace) {
        val workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), TEST_WORKSPACE_NAME))
        workspaceManager.initialize(workspaceFolders, diagnosticsPublisher, ::populateNotifications)
    }

    protected fun initializeWorkspaces(workspaces: List<File>) {
        val workspaceFolders = workspaces.map { WorkspaceFolder(it.toURI().toString(), TEST_WORKSPACE_NAME) }
        workspaceManager.initialize(workspaceFolders, diagnosticsPublisher, ::populateNotifications)
    }
}
