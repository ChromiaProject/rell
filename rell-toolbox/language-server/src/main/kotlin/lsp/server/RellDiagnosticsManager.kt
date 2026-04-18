/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsPublisher
import java.net.URI

class RellDiagnosticsManager {
    private lateinit var diagnosticsPublisher: DiagnosticsPublisher
    private lateinit var notificationPublisher: (type: NotificationType, message: String) -> Unit

    fun setDiagnosticsPublisher(publisher: DiagnosticsPublisher) {
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

    fun reportDiagnostics(indexer: WorkspaceIndexer, fileUris: List<URI> = listOf(), skipCache: Boolean = false) {
        if (!::diagnosticsPublisher.isInitialized) {
            logger.error { "Diagnostics publisher not initialized" }
            return
        }

        var issues = indexer.getAllIssues()
        if (fileUris.isNotEmpty()) {
            issues = issues.filter { (uri, _) -> fileUris.contains(uri) }
        }

        publishDiagnostics(issues, skipCache)
    }

    private fun publishDiagnostics(issues: Map<URI, List<RellIssue>>, skipCache: Boolean = false) {
        issues.forEach { (uri, issueList) ->
            diagnosticsPublisher.publishDiagnostics(uri, issueList, skipCache)
        }
    }

    fun clearDiagnostics(fileUri: URI) {
        if (::diagnosticsPublisher.isInitialized) {
            diagnosticsPublisher.publishDiagnostics(fileUri, listOf())
        }
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
