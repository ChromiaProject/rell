/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.gson.JsonObject
import net.postchain.rell.toolbox.common.RellVersionInfo
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.lsp.createTextDocumentItem
import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RellLanguageServerTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var indexingManager: RellIndexingManager
    private lateinit var documentManager: RellDocumentManager
    private lateinit var testClient: TestClient
    private lateinit var lspIncludeDefinitionProvider: LspSystemPropertiesProvider
    private lateinit var testWorkspaceFolder: File
    private val serverModule = TestServerModule()
    private val testFilePath = "new_file.rell"

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        testWorkspaceFolder = setupRellTestProject(tempDir).workspaceFolder

        val koinApp = serverModule.startKoin()
        lspIncludeDefinitionProvider = koinApp.koin.get()
        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        clientServerLauncher.initializeServer(testWorkspaceFolder.toURI())

        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
        documentManager = koinApp.koin.get<RellDocumentManager>()
        indexingManager = koinApp.koin.get<RellIndexingManager>()
        assertThat(testClient.diagnostics).isEmpty()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `about endpoint is resolves with correct info`() {
        val response = server.about().get()
        assertThat(response).isEqualTo(RellVersionInfo.getAbout())
    }

    @Test
    fun `files watcher is registered from server`() {
        assertThat(testClient.registeredCapabilities).hasSize(1)

        val registeredCapability = testClient.registeredCapabilities.first()
        assertThat(registeredCapability.method).isEqualTo("workspace/didChangeWatchedFiles")

        val registerOptions = registeredCapability.registerOptions as? JsonObject
        assertThat(registerOptions).isNotNull()

        assertThat(registerOptions!!.get("watchers")).isNotNull()
        assertThat(registerOptions.getAsJsonArray("watchers").size()).isEqualTo(1)
        val watcher = registerOptions.getAsJsonArray("watchers").first().asJsonObject

        assertThat(watcher.get("globPattern")).isNotNull()
        assertThat(watcher.get("globPattern").asJsonObject.get("pattern").asString).isEqualTo("**/*")

        assertThat(watcher.get("kind")).isNotNull()
        assertThat(watcher.get("kind").asInt).isEqualTo(7)
    }

    @Test
    fun `invalidateCaches endpoint is resolves with correct info`() {
        val response = server.invalidateCaches().get()
        assertThat(response).isTrue()
    }

    @Test
    fun `cacheFolder endpoint is resolves with correct info`() {
        val response = server.cacheFolder().get().replace("\\", "/")
        assertThat(response).endsWith(".cache/chromia/rell-language-server")
    }

    @Test
    fun `semanticTokensFull returns the tokens within the file`(@TempDir tempDir: Path) {
        val file = createSimpleRellFileInDirectory(tempDir)
        val semanticTokenParams = createSemanticTokensParams(file)
        val response = server.textDocumentService.semanticTokensFull(semanticTokenParams)
        await().until { testClient.diagnostics.isNotEmpty() }
        assertThat(response.get().data).isNotEmpty()
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
    }

    @Test
    fun `semanticTokensFull returns empty for non rell files`(@TempDir tempDir: Path) {
        val testFilePath = "new_file.txt"
        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                module;
                function foo() {}
                """.trimIndent()
            )
        }

        val file = testDataBuilder.sourceFile(testFilePath)
        val semanticTokenParams = createSemanticTokensParams(file)
        val response = server.textDocumentService.semanticTokensFull(semanticTokenParams)
        assertThat(response.get().data).isEmpty()
    }

    @Test
    fun `definition do not return the tokens within the file if not opened`(@TempDir tempDir: Path) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                module;
                function foo() {
                    val x = bar.name;
                }

                entity bar {
                    name;
                }
                """.trimIndent()
            )
        }

        val file = testDataBuilder.sourceFile(testFilePath)
        val pos = Position(2, 13)
        val definitionParams = createDefinitionParams(file, pos)
        val response = server.textDocumentService.definition(definitionParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(response.get()!!.left).isEmpty()
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
    }

    @Test
    fun `definition returns the tokens within the file if opened`(@TempDir tempDir: Path) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                module;
                function foo() {
                    val x = bar.name;
                }

                entity bar {
                    name;
                }
                """.trimIndent()
            )
        }

        val file = testDataBuilder.sourceFile(testFilePath)
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val pos = Position(2, 13)
        val definitionParams = createDefinitionParams(file, pos)
        val response = server.textDocumentService.definition(definitionParams)

        assertThat(response.get()!!.left).isNotEmpty()
    }

    @Test
    fun `references return symbol without definition when LspIncludeDefinition is false`(
        @TempDir tempDir: Path
    ) {
        serverModule.stopKoinGlobalContext()
        val koinApp = serverModule.startKoin(false)
        val lspSystemPropertiesProvider = koinApp.koin.get<LspSystemPropertiesProvider>()
        val clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        clientServerLauncher.initializeServer(testWorkspaceFolder.toURI())

        val testClient = clientServerLauncher.testClient
        val server = koinApp.koin.get<RellLanguageServer>()
        assertThat(testClient.diagnostics).isEmpty()

        val srcPath = Paths.get("$tempDir/src")
        Files.createDirectory(srcPath)
        val file = File(tempDir.toString(), "/src/new_file.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    val x = bar.name;
                }

                entity bar {
                    name;
                }
                """.trimIndent()
            )
        }
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val pos = Position(2, 13)
        val referenceParams = createReferenceParams(file, pos)
        val response = server.textDocumentService.references(referenceParams)

        assertThat(lspSystemPropertiesProvider.getIncludeDefinition()).isFalse()
        assertThat(response.get()!!).containsExactlyInAnyOrder(
            Location(
                file.toURI().toString(),
                Range(Position(2, 12), Position(2, 15))
            )
        )
    }

    @Test
    fun `references return symbols including definition when LspIncludeDefinition is true`(@TempDir tempDir: Path) {
        val srcPath = Paths.get("$tempDir/src")
        Files.createDirectory(srcPath)
        val file = File(tempDir.toString(), "/src/new_file.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    val x = bar.name;
                }

                entity bar {
                    name;
                }
                """.trimIndent()
            )
        }

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val pos = Position(2, 13)
        val referenceParams = createReferenceParams(file, pos)
        val response = server.textDocumentService.references(referenceParams)

        assertThat(lspIncludeDefinitionProvider.getIncludeDefinition()).isTrue()
        assertThat(response.get()!!).containsExactlyInAnyOrder(
            Location(
                file.toURI().toString(),
                Range(Position(2, 12), Position(2, 15))
            ),
            Location(
                file.toURI().toString(),
                Range(Position(5, 7), Position(5, 10))
            )
        )
    }

    @Test
    fun `reference returns the locations to symbols`(@TempDir tempDir: Path) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                module;
                function foo() {
                    val x = bar.name;
                }

                entity bar {
                    name;
                }
                """.trimIndent()
            )
        }

        val file = testDataBuilder.sourceFile(testFilePath)
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val pos = Position(2, 13)
        val referenceParams = createReferenceParams(file, pos)
        val response = server.textDocumentService.references(referenceParams)

        assertThat(response.get()).isNotNull().isNotEmpty()
    }

    @Test
    fun `documentSymbol returns if the file is open`(@TempDir tempDir: Path) {
        val file = createSimpleRellFileInDirectory(tempDir)

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val documentSymbolParams = createDocumentSymbolParams(file)
        val response = server.textDocumentService.documentSymbol(documentSymbolParams)

        assertThat(response.get()).isNotNull().isNotEmpty()
    }

    @Test
    fun `can handle file paths with spaces`(@TempDir tempDir: Path) {
        val dir = tempDir.resolve("my dir")
        Files.createDirectory(dir)
        val file = createSimpleRellFileInDirectory(dir)

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }
    }

    @Test
    fun `formatting returns changes needed`(@TempDir tempDir: Path) {
        val file = createSimpleRellFileInDirectory(tempDir)
        val formattingParams = createDocumentFormattingParams(file)
        val response = server.textDocumentService.formatting(formattingParams)

        assertThat(response.get()).isNotNull().isNotEmpty()
    }

    @Test
    fun `rangeFormatting returns changes needed`(@TempDir tempDir: Path) {
        val file = createSimpleRellFileInDirectory(tempDir)

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val range = Range(Position(0, 1), Position(0, 7))
        val formattingParams = createDocumentRangeFormattingParams(file, range)
        val response = server.textDocumentService.rangeFormatting(formattingParams)

        assertThat(response.get()).isNotNull().isNotEmpty()
    }

    @Test
    fun `didOpen sends empty diagnostic for open file with no error`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen send diagnostic for open file with single syntax error`() {
        val file = testWorkspaceFolder.resolve("src/single_syntax_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen send diagnostic for open file with multiple semantic errors`() {
        val file = testWorkspaceFolder.resolve("src/semantic_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(3)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
    }

    @Test
    fun `didOpen event for file outside initialized workspace creates new indexer`(@TempDir tempDir: Path) {
        val testDataBuilder = testData(tempDir) {
            emptyRellModule(testFilePath)
        }

        val file = testDataBuilder.sourceFile(testFilePath)

        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
        assertThat(indexingManager.indexers.keys).containsOnly(
            testDataBuilder.sourceFolderUri,
            testWorkspaceFolder.resolve("src").toURI()
        )
    }

    @Test
    fun `didClose removes file from openDocuments`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { documentManager.getOpenDocuments().isNotEmpty() }

        val didCloseParam = DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString()))
        server.textDocumentService.didClose(didCloseParam)

        await().until { documentManager.getOpenDocuments().isEmpty() }
        assertThat(documentManager.getOpenDocuments()).isEmpty()
    }

    @Test
    fun `didChange sends diagnostic on open file after a change causes a error`() {
        val file = testWorkspaceFolder.resolve("src/no_errors.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { documentManager.getOpenDocuments().containsKey(file.toURI()) }
        val diagnosticBeforeChange = testClient.diagnostics.toMap()

        val versionedTextDocument = VersionedTextDocumentIdentifier(file.toURI().toString(), 2)
        val range = Range(Position(3, 17), Position(3, 18))
        val contentChanges = TextDocumentContentChangeEvent(range, "")
        val didChangeParam = DidChangeTextDocumentParams(versionedTextDocument, listOf(contentChanges))

        server.textDocumentService.didChange(didChangeParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(diagnosticBeforeChange.keys).containsOnly(file.toURI().toString())
        assertThat(diagnosticBeforeChange[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
    }

    @Test
    fun `didChange sends empty diagnostic on open file after applied fix of error`() {
        val file = testWorkspaceFolder.resolve("src/single_syntax_error.rell")
        val textDocumentItem = createTextDocumentItem(file)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)

        server.textDocumentService.didOpen(didOpenParam)
        await().until { documentManager.getOpenDocuments().containsKey(file.toURI()) }
        val diagnosticBeforeChange = testClient.diagnostics.toMap()

        val versionedTextDocument = VersionedTextDocumentIdentifier(file.toURI().toString(), 2)
        val range = Range(Position(4, 13), Position(4, 13))
        val contentChanges = TextDocumentContentChangeEvent(range, ";")
        val didChangeParam = DidChangeTextDocumentParams(versionedTextDocument, listOf(contentChanges))

        server.textDocumentService.didChange(didChangeParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(diagnosticBeforeChange.keys).containsOnly(file.toURI().toString())
        assertThat(diagnosticBeforeChange[file.toURI().toString()]!!.size).isEqualTo(1)
        assertThat(testClient.diagnostics.keys).containsOnly(file.toURI().toString())
        assertThat(testClient.diagnostics[file.toURI().toString()]!!.size).isEqualTo(0)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file.toURI())
    }

    @Test
    fun `didSave send diagnostic on itself and affected files`() {
        val savedFile = testWorkspaceFolder.resolve("src/semantic_error.rell")
        val affectedFile = testWorkspaceFolder.resolve("src/import.rell")

        val textDocumentItem = createTextDocumentItem(savedFile)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { documentManager.getOpenDocuments().containsKey(savedFile.toURI()) }

        val didSaveParams = DidSaveTextDocumentParams(TextDocumentIdentifier(savedFile.toURI().toString()))
        server.textDocumentService.didSave(didSaveParams)
        await().until { testClient.diagnostics.size == 2 }

        assertThat(testClient.diagnostics.keys).containsOnly(
            savedFile.toURI().toString(),
            affectedFile.toURI().toString()
        )
        assertThat(testClient.diagnostics[savedFile.toURI().toString()]!!.size).isEqualTo(3)
        assertThat(testClient.diagnostics[affectedFile.toURI().toString()]!!.size).isEqualTo(0)
    }

    private fun createSimpleRellFileInDirectory(directory: Path): File {
        val testDataBuilder = testData(directory) {
            addFile(
                testFilePath,
                """
                module;
                function foo {}
                function bar() {}
                """.trimIndent()
            )
        }

        return testDataBuilder.sourceFile(testFilePath)
    }

    private fun createReferenceParams(file: File, pos: Position): ReferenceParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        return ReferenceParams(id, pos, ReferenceContext())
    }

    private fun createDocumentRangeFormattingParams(file: File, range: Range): DocumentRangeFormattingParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        val options = FormattingOptions()
        return DocumentRangeFormattingParams(id, options, range)
    }

    private fun createDocumentFormattingParams(file: File): DocumentFormattingParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        val options = FormattingOptions()
        return DocumentFormattingParams(id, options)
    }

    private fun createDocumentSymbolParams(file: File): DocumentSymbolParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        return DocumentSymbolParams(id)
    }

    private fun createSemanticTokensParams(file: File): SemanticTokensParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        return SemanticTokensParams(id)
    }

    private fun createDefinitionParams(file: File, position: Position): DefinitionParams {
        val id = TextDocumentIdentifier(file.toURI().toString())
        return DefinitionParams(id, position)
    }

    private fun setupRellTestProject(dir: File): TestDataBuilder {
        return testData(dir) {
            addFile(
                "import.rell",
                """
                    module;

                    import ^.semantic_error.*;

                    operation create_c() {
                        create c(name = "");
                    }
                """.trimIndent()
            )
            addFile(
                "multiple_syntax_error.rell",
                """
                    module

                    function a() {
                        val a = "a"
                    }

                    va = 2;

                    function () = ;

                    create anEntity(name);
                """.trimIndent()
            )
            addFile(
                "no_errors.rell",
                """
                    module;

                    function main() {
                        return "main";
                    }
                """.trimIndent()
            )
            addFile(
                "semantic_error.rell",
                """
                    module;

                    entity c {
                        name;
                    }

                    function a() {
                        create b(name = "");
                    }

                    function a() {
                        val a = 2;
                    }

                    function foo() {
                        val a = 2;
                    }
                """.trimIndent()
            )
            addFile(
                "single_syntax_error.rell",
                """
                    module;


                    function a() {
                        val a = 2
                    }
                """.trimIndent()
            )
            addFile(
                "syntax_error.rell",
                """
                    module;


                    function a() {
                        val a = 2
                    }


                    namespace ns {
                        function ns_b() {
                            val a = 2
                        }

                        operation ns_op() {
                            errVal = "error
                        }
                    }


                    function c()
                """.trimIndent()
            )
        }
    }
}
