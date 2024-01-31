package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule
import java.io.File

class RellSymbolRenameTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private lateinit var testWorkspaceSrc: File
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()

        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()

        val rellRenameFolderDir = File(javaClass.getClassLoader().getResource("symbolRename")!!.file)
        val tempCopyDestination = tempDir.resolve("symbolRename")
        await().until { rellRenameFolderDir.copyRecursively(tempCopyDestination) }

        testWorkspaceSrc = tempCopyDestination.resolve("src")
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `Renaming isn't allowed for restricted symbols`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("restricted.rell")
        val fileUri = openFile(renamedSymbolFile)

        val restrictedSymbolPositions = listOf(
            Position(1, 10),
            Position(2, 10),
            Position(2, 24),
            Position(4, 6),
            Position(6, 35),
        )

        restrictedSymbolPositions.forEach { position ->
            val prepareRenameParams = PrepareRenameParams(TextDocumentIdentifier(fileUri), position)
            val prepareRenameResult = server.prepareRename(prepareRenameParams)
            val result = prepareRenameResult.join()

            assertThat(result.left).isNull()
            assertThat(result.right).isEqualTo(Either.forRight(PrepareRenameDefaultBehavior()))
        }
    }

    @Test
    fun `Symbol is renamed in whole workspace`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("main.rell")
        val fileUri = openFile(renamedSymbolFile)

        val newFunctionName = "newName"
        val renameParams = RenameParams(TextDocumentIdentifier(fileUri), Position(3, 15), "newName")
        val renameResult = server.rename(renameParams)
        val result = renameResult.join()

        val anotherImportingFileUri = testWorkspaceSrc.resolve("submodule/another_importing.rell").toURI().toString()
        val expectedChanges = listOf(
            fileUri to listOf(
                TextEdit(Range(Position(3, 9), Position(3, 24)), newFunctionName),
                TextEdit(Range(Position(13, 17), Position(13, 32)), newFunctionName)
            ),
            anotherImportingFileUri to listOf(
                TextEdit(Range(Position(2, 17), Position(2, 32)), newFunctionName)
            )
        )
        assertThat(result.changes).containsOnly(*expectedChanges.toTypedArray())
    }

    @Test
    fun `Renaming symbol in non rell file is ignored`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("main.js")
        val fileUri = openFile(renamedSymbolFile)

        val newFunctionName = "newName"
        val renameParams = RenameParams(TextDocumentIdentifier(fileUri), Position(0, 11), "newName")
        val renameResult = server.rename(renameParams)
        val result = renameResult.join()

        assertThat(result.changes).isEmpty()
    }

    private fun openFile(file: File): String {
        val fileUri = file.toURI().toString()
        val textDocumentItem = TextDocumentItem(fileUri, "rell", 1, file.readText())
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.didOpen(didOpenParam)
        return fileUri
    }
}
