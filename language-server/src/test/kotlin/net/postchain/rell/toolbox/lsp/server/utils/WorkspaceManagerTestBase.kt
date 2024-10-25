package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
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

    protected val rellLinter = RellLinter()
    protected val formattingStyleLinter = FormattingStyleLinter()

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
        workspaceManager =
            RellWorkspaceManager(
                symbolService, referenceService, indexCachingService, rellLinter,
                formattingStyleLinter,
                RellFormatterOptionsResolver(),
                RellLinterOptionsResolver(),
                RellCompletionService()
            )
    }

    @AfterEach
    fun breakdown() {
        diagnostics.clear()
    }

    protected fun populateDiagnostics(uri: URI, issues: List<RellIssue>) {
        diagnostics[uri] = issues
    }

    protected fun initializeWorkspace(workspace: File = this.workspace) {
        val workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
    }
}
