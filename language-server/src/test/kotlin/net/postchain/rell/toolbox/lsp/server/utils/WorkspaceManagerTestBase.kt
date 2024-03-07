package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

open class WorkspaceManagerTestBase {
    protected lateinit var workspaceManager: RellWorkspaceManager
    protected var diagnostics = mutableMapOf<URI, List<RellIssue>>()
    protected lateinit var workspace: File
    protected lateinit var sourceDir: File
    protected val symbolService = RellSymbolService()

    @BeforeEach
    fun setup(@TempDir tempWorkspace: File) {
        workspace = tempWorkspace
        sourceDir = File(workspace, "src").apply { mkdir() }
        val referenceService = RellReferenceService(symbolService)
        val indexCachingService = RellIndexCachingService(RellIndexSerializer())
        workspaceManager = RellWorkspaceManager(symbolService, referenceService, indexCachingService)
    }

    @AfterEach
    fun breakdown() {
        diagnostics.clear()
    }

    protected fun populateDiagnostics(uri: URI, issues: List<RellIssue>) {
        diagnostics[uri] = issues
    }

    protected fun createFile(parent: File, name: String, content: String): File {
        return File(parent, name).apply {
            writeText(content)
        }
    }

    protected fun initializeWorkspace(workspace: File = this.workspace) {
        val workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
    }
}
