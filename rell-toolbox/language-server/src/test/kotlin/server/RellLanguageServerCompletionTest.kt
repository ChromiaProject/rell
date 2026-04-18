/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.lsp.createTextDocumentItem
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.io.File

class RellLanguageServerCompletionTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
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
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `completions correctly returned`() {
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                query q() = 123;
                query p() { return 222; }
                enum e { a, b, c }
                entity t {
                    first: text;
                    last: text;
                }
                """.trimIndent()
            )
        }
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)

        val textDocumentItem = createTextDocumentItem(testDataBuilder.mainFile)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val position = org.eclipse.lsp4j.Position(0, 0)
        val documentId = TextDocumentIdentifier(testDataBuilder.mainFileUri.toString())

        val params = CompletionParams(documentId, position)
        val completions = server.textDocumentService.completion(params).join()

        val expectedCompletions = arrayOf(
            "p",
            "q",
            "e",
            "t",
            "abs",
            "min",
            "max",
            "print",
            "log",
            "require",
            "require_not_empty",
            "verify_signature",
            "sha256",
            "try_call",
            "keccak256"
        )

        assertThat(completions.left).extracting { it.label }.containsAtLeast(*expectedCompletions)
    }

    @Test
    fun `completions resolved correctly`() {
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                query p() { kecc return 222; }
                """.trimIndent()
            )
        }
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)

        val textDocumentItem = createTextDocumentItem(testDataBuilder.mainFile)
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.textDocumentService.didOpen(didOpenParam)
        await().until { testClient.diagnostics.isNotEmpty() }

        val position = org.eclipse.lsp4j.Position(1, 16)
        val documentId = TextDocumentIdentifier(testDataBuilder.mainFileUri.toString())

        val params = CompletionParams(documentId, position)
        val completions = server.textDocumentService.completion(params).join()
        val completion = "keccak256"
        val completionItem = completions.left.first { it.label == completion }

        val resolved = server.textDocumentService.resolveCompletionItem(completionItem).join()
        val expected = "ak256(\${1:input})"
        assertThat(resolved?.textEdit?.left?.newText).isEqualTo(expected)
    }
}
