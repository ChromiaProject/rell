package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.io.File
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule
import kotlin.io.path.createDirectory

class RellLanguageServerSemanticTokens {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private lateinit var srcDir: File
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val koinApp = serverModule.startKoin()

        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `Semantic tokens call with git URI not crashing`() {
        clientServerLauncher.initializeServer(srcDir.toURI())

        val gitUri = "git:/path/to/ft3-lib/rell/src/lib/ft4/accounts/strategies/transfer/operations.rell?{\"path\":\"/path/to/lib/ft3-lib/rell/src/lib/ft4/accounts/strategies/transfer/operations.rell\",\"ref\":\"~\"}"
        val params = SemanticTokensParams(TextDocumentIdentifier(gitUri))
        val tokens = server.semanticTokensFull(params).join()

        assertThat(tokens).isNotNull()
        assertThat(tokens.data).isNull()
        assertThat(tokens.resultId).isNull()
    }
}
