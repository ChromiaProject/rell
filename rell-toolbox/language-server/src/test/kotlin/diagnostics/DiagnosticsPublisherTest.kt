/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.diagnostics

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.RellIssueSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CompletableFuture

class DiagnosticsPublisherTest {

    private lateinit var mockClient: LanguageClient
    private lateinit var publisherWithInitialized: DiagnosticsPublisher
    private lateinit var initialized: CompletableFuture<Any>

    private val testUri = URI("file:///test/file.rell")

    @BeforeEach
    fun setup() {
        mockClient = mockk<LanguageClient>(relaxed = true)
        initialized = CompletableFuture.completedFuture(Any())
        publisherWithInitialized = DiagnosticsPublisher(mockClient, initialized)
    }

    @Test
    fun `publishDiagnostics should send diagnostics to client when issues change`() {
        val issues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))
        val paramsSlot = slot<PublishDiagnosticsParams>()

        publisherWithInitialized.publishDiagnostics(testUri, issues)

        verify { mockClient.publishDiagnostics(capture(paramsSlot)) }

        val sentParams = paramsSlot.captured
        assertThat(sentParams.uri).isEqualTo(testUri.toString())
        assertThat(sentParams.diagnostics.size).isEqualTo(2)
        assertThat(sentParams.diagnostics[0].message).isEqualTo("Error 1")
        assertThat(sentParams.diagnostics[1].message).isEqualTo("Error 2")
    }

    @Test
    fun `publishDiagnostics should not send diagnostics when issues haven't changed`() {
        val issues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))

        publisherWithInitialized.publishDiagnostics(testUri, issues)
        clearMocks(mockClient)

        publisherWithInitialized.publishDiagnostics(testUri, issues)
        verify(exactly = 0) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `when caching disabled publishDiagnostics should send diagnostics when issues haven't changed`() {
        val publisherWithoutCaching = DiagnosticsPublisher(mockClient, initialized, checkCacheBeforeSend = false)
        val issues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))

        publisherWithoutCaching.publishDiagnostics(testUri, issues)
        clearMocks(mockClient)

        publisherWithoutCaching.publishDiagnostics(testUri, issues)
        verify(exactly = 1) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `publishDiagnostics should send diagnostics when issues change`() {
        val initialIssues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))
        val paramsSlot = slot<PublishDiagnosticsParams>()

        publisherWithInitialized.publishDiagnostics(testUri, initialIssues)

        clearMocks(mockClient)

        val newIssues = listOf(createTestIssue("Error 1"), createTestIssue("Error 3"))
        publisherWithInitialized.publishDiagnostics(testUri, newIssues)
        verify { mockClient.publishDiagnostics(capture(paramsSlot)) }

        val sentParams = paramsSlot.captured
        assertThat(sentParams.diagnostics.size).isEqualTo(2)
        assertThat(sentParams.diagnostics[0].message).isEqualTo("Error 1")
        assertThat(sentParams.diagnostics[1].message).isEqualTo("Error 3")
    }

    @Test
    fun `clearDiagnostics should send empty diagnostics list for specified URI`() {
        val issues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))
        val paramsSlot = slot<PublishDiagnosticsParams>()

        publisherWithInitialized.publishDiagnostics(testUri, issues)
        clearMocks(mockClient)

        publisherWithInitialized.clearDiagnostics(testUri)

        verify { mockClient.publishDiagnostics(capture(paramsSlot)) }
        val sentParams = paramsSlot.captured
        assertThat(sentParams.uri).isEqualTo(testUri.toString())
        assertThat(sentParams.diagnostics.isEmpty()).isTrue()
    }

    @Test
    fun `clearDiagnostics should send empty diagnostics for multiple URIs`() {
        val uri1 = URI("file:///test/file1.rell")
        val uri2 = URI("file:///test/file2.rell")

        publisherWithInitialized.publishDiagnostics(uri1, listOf(createTestIssue("Error 1")))
        publisherWithInitialized.publishDiagnostics(uri2, listOf(createTestIssue("Error 2")))
        clearMocks(mockClient)

        publisherWithInitialized.clearDiagnostics(listOf(uri1, uri2))

        verify(exactly = 2) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `publishDiagnostics should not publish when initialized is null`() {
        val publisherWithoutInitialized = DiagnosticsPublisher(mockClient, null)
        val issues = listOf(createTestIssue("Error 1"))

        publisherWithoutInitialized.publishDiagnostics(testUri, issues)

        verify(exactly = 0) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `publishDiagnostics should not publish when client is null`() {
        val publisherWithoutClient = DiagnosticsPublisher(null, initialized)
        val issues = listOf(createTestIssue("Error 1"))

        publisherWithoutClient.publishDiagnostics(testUri, issues)

        verify(exactly = 0) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `clearDiagnostics for collection should handle empty list`() {
        publisherWithInitialized.clearDiagnostics(emptyList())

        verify(exactly = 0) { mockClient.publishDiagnostics(any()) }
    }

    @Test
    fun `publishDiagnostics should not publish when only order of issues changes`() {
        val initialIssues = listOf(createTestIssue("Error 1"), createTestIssue("Error 2"))

        publisherWithInitialized.publishDiagnostics(testUri, initialIssues)
        clearMocks(mockClient)

        val reorderedIssues = listOf(createTestIssue("Error 2"), createTestIssue("Error 1"))
        publisherWithInitialized.publishDiagnostics(testUri, reorderedIssues)

        verify(exactly = 0) { mockClient.publishDiagnostics(any()) }
    }

    private fun createTestIssue(message: String): RellIssue {
        return RellIssue(
            message = message,
            code = "test-code",
            severity = RellIssueSeverity.ERROR,
            line = 1,
            column = 1
        )
    }
}
