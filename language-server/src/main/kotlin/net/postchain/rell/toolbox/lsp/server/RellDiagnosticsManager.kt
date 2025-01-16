package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import java.net.URI

class RellDiagnosticsManager {
    private lateinit var diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit
    private lateinit var notificationPublisher: (type: NotificationType, message: String) -> Unit

    fun setDiagnosticsPublisher(publisher: (uri: URI, List<RellIssue>) -> Unit) {
        diagnosticsPublisher = publisher
    }

    fun setNotificationPublisher(publisher: (type: NotificationType, message: String) -> Unit) {
        notificationPublisher = publisher
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

    fun sendNotification(type: NotificationType, messsage: String) {
        if (!::notificationPublisher.isInitialized) {
            logger.error { "Notification publisher not initialized" }
            return
        }
        notificationPublisher(type, messsage)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}


enum class NotificationType {
    ERROR,
    WARNING,
    INFO,
    LOG,
}
