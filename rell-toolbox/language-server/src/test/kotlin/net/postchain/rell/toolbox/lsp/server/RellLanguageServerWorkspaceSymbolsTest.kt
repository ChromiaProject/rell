/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellLanguageServerWorkspaceSymbolsTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var documentManager: RellDocumentManager
    private lateinit var testClient: TestClient

    private val serverModule = TestServerModule()
    private val rellFilePath = "first.rell"

    @TempDir
    lateinit var tempDir: File

    private lateinit var testDataBuilder: TestDataBuilder

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()
        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
        documentManager = koinApp.koin.get<RellDocumentManager>()

        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                query my_query() = 1;
                function my_function(paramA: integer, paramB: integer): integer {
                    return 42;
                }
                """.trimIndent()
            )
            addFile(
                rellFilePath,
                """
                module;
                val some_value = 123;
                operation some_operation() {}
                """.trimIndent()
            )
        }
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `Workspace symbols returned`() {
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)

        val params = WorkspaceSymbolParams("")
        val symbols = server.workspaceService.symbol(params).join().right

        assertThat(symbols).containsExactlyInAnyOrder(
            WorkspaceSymbol(
                "my_query",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(1, 6), Position(1, 14))
                    )
                )
            ),
            WorkspaceSymbol(
                "my_function",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(2, 9), Position(2, 20))
                    )
                )
            ),
            WorkspaceSymbol(
                "some_value",
                SymbolKind.Constant,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(rellFilePath).toURI().toString(),
                        Range(Position(1, 4), Position(1, 14))
                    )
                )
            ),
            WorkspaceSymbol(
                "some_operation",
                SymbolKind.Method,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(rellFilePath).toURI().toString(),
                        Range(Position(2, 10), Position(2, 24))
                    )
                )
            ),
        )
    }

    @Test
    fun `Workspace symbols are filtered`() {
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)

        val params = WorkspaceSymbolParams("my")
        val symbols = server.workspaceService.symbol(params).join().right

        assertThat(symbols).containsExactlyInAnyOrder(
            WorkspaceSymbol(
                "my_query",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(1, 6), Position(1, 14))
                    )
                )
            ),
            WorkspaceSymbol(
                "my_function",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(2, 9), Position(2, 20))
                    )
                )
            ),
        )
    }
}
