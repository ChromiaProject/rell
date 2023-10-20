package net.postchain.rell.toolbox.lsp.server

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess


interface RellLanguageServer : LanguageServer, LanguageClientAware
class LanguageServerImpl : RellLanguageServer {

    private val documentService: DocumentServiceImpl = DocumentServiceImpl()
    private val workspaceService: WorkspaceServiceImpl = WorkspaceServiceImpl()
    private lateinit var languageClient: LanguageClient

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val result = InitializeResult()
        return CompletableFuture.completedFuture(result)
    }

    override fun shutdown(): CompletableFuture<Any> {
        TODO("Not yet implemented")
    }

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService {
        return documentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun connect(client: LanguageClient) {
        languageClient = client
    }
}
