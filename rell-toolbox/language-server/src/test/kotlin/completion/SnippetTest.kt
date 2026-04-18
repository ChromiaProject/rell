/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.completion

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.junit.jupiter.api.Test

class SnippetTest {

    @Test
    fun `test snippet creation`() {
        val snippet = Snippet(
            prefix = "entity",
            body = """
                entity ${'$'}{1:name} {
                    ${'$'}{2:fields}
                }
            """.trimIndent(),
            description = "Entity declaration",
            labelDetail = "entity + name + fields"
        )

        val completionItem = snippet.toCompletionItem()

        with(completionItem) {
            assertThat(label).isEqualTo("entity")
            assertThat(detail).isEqualTo("Entity declaration")
            assertThat(kind).isEqualTo(CompletionItemKind.Snippet)
            assertThat(insertTextFormat).isEqualTo(InsertTextFormat.Snippet)
            assertThat(insertText).isEqualTo(snippet.body)
            assertThat(labelDetails.description).isEqualTo("entity + name + fields")
        }
    }
}
