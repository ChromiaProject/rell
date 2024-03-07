package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule
import java.io.File
import kotlin.io.path.createDirectory

class RellLanguageServerTestRunner {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private lateinit var srcDir: File
    private lateinit var testFile: File
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        testFile = File(srcDir, "test_file.rell").apply {
            writeText(
                """
                @test module; 
                function test_1() { return 1; }
                function not_test() { return 1; }
                function test_2() { return 1; }
            """.trimIndent()
            )
        }
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
    fun `listTestFiles return all test modules`() {
        clientServerLauncher.initializeServer(srcDir.toURI())
        val testFiles = server.getTestFiles(srcDir.toURI().toString()).join()
        assertThat(testFiles).extracting { it.uri }.containsOnly(testFile.toURI())
    }

    @Test
    fun `listTestCases return all test functions in file`() {
        clientServerLauncher.initializeServer(srcDir.toURI())
        val testCases = server.getTestCases(testFile.toURI().toString()).join()
        assertThat(testCases).extracting { it.name }.containsOnly("test_1", "test_2")
    }
}
