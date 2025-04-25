package net.postchain.rell.toolbox.lsp.diagnostics

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.RellIssue
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


open class DiagnosticsPublisher(
    private val languageClient: LanguageClient?,
    private val initialized: CompletableFuture<*>?,
    private val checkCacheBeforeSend : Boolean = true
) {
    private val diagnosticsCache = ConcurrentHashMap<URI, Set<RellIssue>>()
    private val logger = KotlinLogging.logger {}

    open fun publishDiagnostics(uri: URI, issues: List<RellIssue>) {
        val newIssues = issues.toSet()

        if (shouldSend(uri, newIssues)) {
            diagnosticsCache[uri] = newIssues
            sendDiagnosticsToClient(uri, issues)
        }
    }

    private fun shouldSend(uri: URI, newIssues: Set<RellIssue>): Boolean {
        if (!checkCacheBeforeSend) return true
        val existingIssues = diagnosticsCache[uri]
        return existingIssues == null || existingIssues != newIssues
    }

    open fun clearDiagnostics(uri: URI) {
        publishDiagnostics(uri, emptyList())
    }

    open fun clearDiagnostics(uris: Collection<URI>) {
        uris.forEach { clearDiagnostics(it) }
    }

    private fun sendDiagnosticsToClient(uri: URI, issues: List<RellIssue>) {
        if (languageClient != null && initialized != null) {
            initialized.thenAccept { _ ->
                val publishDiagnosticsParams = PublishDiagnosticsParams()
                publishDiagnosticsParams.uri = uri.toString()
                publishDiagnosticsParams.diagnostics = DiagnosticsConverter.toDiagnostics(issues)

                logger.debug { "Publishing ${issues.size} diagnostics for $uri" }
                languageClient.publishDiagnostics(publishDiagnosticsParams)
            }
        } else {
            logger.error { "Cannot send diagnostics: client or initialized future is null" }
        }
    }
}
