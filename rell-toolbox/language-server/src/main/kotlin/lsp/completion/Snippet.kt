/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.completion

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.InsertTextFormat

data class Snippet(
    val prefix: String,
    val body: String,
    val description: String,
    val labelDetail: String
) {
    fun toCompletionItem(): CompletionItem = CompletionItem().apply {
        label = prefix
        detail = description
        labelDetails = CompletionItemLabelDetails().apply {
            description = labelDetail
        }
        kind = CompletionItemKind.Snippet
        insertText = body
        insertTextFormat = InsertTextFormat.Snippet
    }
}
