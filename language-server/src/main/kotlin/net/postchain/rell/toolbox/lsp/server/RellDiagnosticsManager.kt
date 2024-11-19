package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import java.net.URI

class RellDiagnosticsManager {
    private lateinit var diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit

    fun setDiagnosticsPublisher(publisher: (uri: URI, List<RellIssue>) -> Unit) {
        diagnosticsPublisher = publisher
    }

    fun reportAllDiagnostics(indexers: Collection<WorkspaceIndexer>) {
        indexers.forEach { indexer ->
            reportDiagnostics(indexer)
        }
    }

    fun reportDiagnostics(indexer: WorkspaceIndexer, fileUris: List<URI> = listOf()) {
        if (!::diagnosticsPublisher.isInitialized) {
            logger.error { "Diagnostics publisher not initialized" }
            return
        }

        var issues = indexer.getAllIssues()
        if (fileUris.isNotEmpty()) {
            issues = issues.filter { (uri, _) -> fileUris.contains(uri) }
        }

        publishDiagnostics(issues)
    }

    private fun publishDiagnostics(issues: Map<URI, List<RellIssue>>) {
        issues.forEach { (uri, issueList) ->
            diagnosticsPublisher(uri, issueList)
        }
    }

    fun clearDiagnostics(fileUri: URI) {
        diagnosticsPublisher(fileUri, listOf())
    }

    fun clearDiagnostics(fileUris: List<URI>) {
        fileUris.forEach { clearDiagnostics(it) }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
