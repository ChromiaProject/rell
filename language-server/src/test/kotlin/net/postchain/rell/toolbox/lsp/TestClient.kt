package net.postchain.rell.toolbox.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

open class TestClient : LanguageClient {

    val diagnostics = mutableMapOf<String, List<Diagnostic>>()
    val progressNotifications = mutableListOf<ProgressParams>()

    fun clearDiagnostics() {
        diagnostics.clear()
    }

    override fun telemetryEvent(`object`: Any?) {
        TODO("Not yet implemented")
    }

    override fun publishDiagnostics(diagnosticsParams: PublishDiagnosticsParams) {
        diagnostics[diagnosticsParams.uri] = diagnosticsParams.diagnostics
    }

    override fun showMessage(messageParams: MessageParams?) {
        TODO("Not yet implemented")
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        TODO("Not yet implemented")
    }

    override fun logMessage(message: MessageParams?) {
        return
    }

    override fun refreshSemanticTokens(): CompletableFuture<Void>? {
        return CompletableFuture<Void>()
    }

    override fun notifyProgress(params: ProgressParams) {
        progressNotifications.add(params)
    }
}
