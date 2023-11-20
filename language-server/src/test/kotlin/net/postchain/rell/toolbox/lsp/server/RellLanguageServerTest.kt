package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RellLanguageServerTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private val serverModule = TestServerModule()
    private val classLoader = javaClass.getClassLoader()
    private val testWorkspaceFolder = File(classLoader.getResource("rellDappWithErrors")!!.file)

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()
        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        clientServerLauncher.initializeServer(testWorkspaceFolder.toURI())

        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()

        assertThat(testClient.diagnostics).isEmpty()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `didOpen sends empty diagnostic for open file with no error`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen send diagnostic for open file with single syntax error`() {
        val file = testWorkspaceFolder.resolve("src/single_syntax_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen send diagnostic for open file with multiple semantic errors`() {
        val file = testWorkspaceFolder.resolve("src/semantic_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(3)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen event for file outside initialized workspace creates new indexer`(@TempDir tempDir: Path) {
        val srcPath = Paths.get("$tempDir/src")
        Files.createDirectory(srcPath)
        val file = File(tempDir.toString(), "/src/new_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
        assertThat(workspaceManager.indexers.keys).containsOnly(
            srcPath.toUri(),
            testWorkspaceFolder.resolve("src").toURI()
        )
    }

    @Test
    fun `didClose sends empty diagnostic for file with no errors`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val didCloseParam = DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString()))

        server.didClose(didCloseParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(workspaceManager.openDocuments).isEmpty()
    }

    @Test
    fun `didClose sends diagnostic for file with single syntax errors`() {
        val file = testWorkspaceFolder.resolve("src/single_syntax_error.rell")
        val didCloseParam = DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString()))

        server.didClose(didCloseParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(workspaceManager.openDocuments).isEmpty()
    }

    @Test
    fun `didClose send diagnostic for open file with multiple semantic errors`() {
        val file = testWorkspaceFolder.resolve("src/semantic_error.rell")
        val didCloseParam = DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString()))

        server.didClose(didCloseParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(3)
        assertThat(workspaceManager.openDocuments).isEmpty()
    }

    @Test
    fun `didChange sends diagnostic on open file after a change causes a error`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.didOpen(didOpenParam)
        await().until { workspaceManager.openDocuments.containsKey(file.toURI()) }
        val diagnosticBeforeChange = testClient.diagnostics.toMap()

        val versionedTextDocument = VersionedTextDocumentIdentifier(file.toURI().toString(), 2)
        val range = Range(Position(3, 17), Position(3, 18))
        val contentChanges = TextDocumentContentChangeEvent(range, "")
        val didChangeParam = DidChangeTextDocumentParams(versionedTextDocument, listOf(contentChanges))

        server.didChange(didChangeParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(diagnosticBeforeChange.keys).containsOnly(file.toURI().toString())
        assertThat(diagnosticBeforeChange[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
    }

    @Test
    fun `didChange sends empty diagnostic on open file after applied fix of error`() {
        val file = testWorkspaceFolder.resolve("src/single_syntax_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.didOpen(didOpenParam)
        await().until { workspaceManager.openDocuments.containsKey(file.toURI()) }
        val diagnosticBeforeChange = testClient.diagnostics.toMap()

        val versionedTextDocument = VersionedTextDocumentIdentifier(file.toURI().toString(), 2)
        val range = Range(Position(4, 13), Position(4, 13))
        val contentChanges = TextDocumentContentChangeEvent(range, ";")
        val didChangeParam = DidChangeTextDocumentParams(versionedTextDocument, listOf(contentChanges))

        server.didChange(didChangeParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(diagnosticBeforeChange.keys).containsOnly(file.toURI().toString())
        assertThat(diagnosticBeforeChange[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(file.toURI())
    }


    @Test
    fun `didSave send diagnostic on itself and affected files`() {
        val savedFile = testWorkspaceFolder.resolve("src/semantic_error.rell")
        val affectedFile = testWorkspaceFolder.resolve("src/import.rell")

        val textDocumentItem = createTextDocumentItem(savedFile)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.didOpen(didOpenParam)
        await().until { workspaceManager.openDocuments.containsKey(savedFile.toURI()) }

        val didSaveParams = DidSaveTextDocumentParams(TextDocumentIdentifier(savedFile.toURI().toString()))
        server.didSave(didSaveParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(
            savedFile.toURI().toString(),
            affectedFile.toURI().toString()
        )
        assertThat(testClient.diagnostics[savedFile.toURI().toString()]!!.size).isEqualTo(3)
        assertThat(testClient.diagnostics[affectedFile.toURI().toString()]!!.size).isEqualTo(0)
    }

    private fun createTextDocumentItem(file: File, version: Int = 1): TextDocumentItem {
        return TextDocumentItem(
            file.toURI().toString(),
            "rell",
            version,
            file.readText()
        )
    }
}
