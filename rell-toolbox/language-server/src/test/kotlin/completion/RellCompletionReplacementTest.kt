/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.completion

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellCompletionSymbolService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.junit.jupiter.api.Test
import java.net.URI

class RellCompletionReplacementTest {
    private val symbolService = RellSymbolService()
    private val completionSymbolService = RellCompletionSymbolService(symbolService)
    private val completionItemFactory = CompletionItemFactory()
    private val completionService = RellCompletionService(completionSymbolService, completionItemFactory)
    private val dummyURI = URI.create("file:/test")

    @Test
    fun `should return remaining text when partial match exists`() {
        val document = Document(dummyURI, 0, "This is fun")
        val result = completionService.getReplacementText(document, document.content.length, "function")

        assertThat(result).isEqualTo("ction")
    }

    @Test
    fun `should return full completion when no match exists`() {
        val document = Document(dummyURI, 0, "hello world")
        val result = completionService.getReplacementText(document, document.content.length, "testing")

        assertThat(result).isEqualTo("testing")
    }

    @Test
    fun `should handle multi-word completions`() {
        val document = Document(dummyURI, 0, "public cla")
        val result = completionService.getReplacementText(document, document.content.length, "class MyClass")

        assertThat(result).isEqualTo("ss MyClass")
    }

    @Test
    fun `Should handle empty completion`() {
        val document = Document(dummyURI, 0, "some text")
        val result = completionService.getReplacementText(document, 5, "")

        assertThat(result).isEmpty()
    }

    @Test
    fun `should handle empty document`() {
        val document = Document(dummyURI, 0, "")
        val result = completionService.getReplacementText(document, 0, "completion")

        assertThat(result).isEqualTo("completion")
    }

    @Test
    fun `should handle offset at beginning of document`() {
        val document = Document(dummyURI, 0, "some text here")
        val result = completionService.getReplacementText(document, 0, "prefix")

        assertThat(result).isEqualTo("prefix")
    }

    @Test
    fun `should handle offset beyond document length`() {
        val document = Document(dummyURI, 0, "short")
        val result = completionService.getReplacementText(document, 100, "completion")

        assertThat(result).isEqualTo("completion")
    }

    @Test
    fun `should handle negative offset`() {
        val document = Document(dummyURI, 0, "some text")
        val result = completionService.getReplacementText(document, -5, "prefix")

        assertThat(result).isEqualTo("prefix")
    }

    @Test
    fun `should handle cursor in middle of document`() {
        val document = Document(dummyURI, 0, "hello world")
        val result = completionService.getReplacementText(document, 3, "help")

        assertThat(result).isEqualTo("p")
    }

    @Test
    fun `should only match text before cursor position`() {
        val document = Document(dummyURI, 0, "function test func")
        val result = completionService.getReplacementText(document, 13, "function")

        assertThat(result).isEqualTo("function")
    }

    @Test
    fun `should handle text longer than look-back limit`() {
        val longText = "a".repeat(100) + "function"
        val document = Document(dummyURI, 0, longText)
        val result = completionService.getReplacementText(document, document.content.length, "functional")

        assertThat(result).isEqualTo("al")
    }

    @Test
    fun `should not match beyond look-back limit`() {
        val longPrefix = "function" + "x".repeat(50)
        val document = Document(dummyURI, 0, longPrefix)
        val result = completionService.getReplacementText(document, document.content.length, "function")

        assertThat(result).isEqualTo("function")
    }
}
