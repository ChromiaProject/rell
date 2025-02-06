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

fun createSnippetCompletions() = defaultSnippets.map(Snippet::toCompletionItem)

private val defaultSnippets = listOf(
    Snippet(
        prefix = "entity",
        body = """
            entity ${'$'}{1:name} {
                ${'$'}{2:fields}
            }
        """.trimIndent(),
        description = "Entity declaration",
        labelDetail = "entity + name + fields"
    ),
    Snippet(
        prefix = "enum",
        body = """
            enum ${'$'}{1:name} {
                ${'$'}{2:field},
            }
        """.trimIndent(),
        description = "Enum declaration",
        labelDetail = "enum + name + field"
    ),
    Snippet(
        prefix = "function",
        body = """
            function ${'$'}{1:name} () {
                ${'$'}{2:body}
            }
        """.trimIndent(),
        description = "Function declaration",
        labelDetail = "function + name + body"
    ),
    Snippet(
        prefix = "if",
        body = """
            if (${'$'}{1:condition}) {
                ${'$'}{2:body}
            }
        """.trimIndent(),
        description = "If statement with bracketed body",
        labelDetail = "if_body + condition + body"
    ),
    Snippet(
        prefix = "if_else",
        body = """
            if (${'$'}{1:condition}) {
                ${'$'}{2:body_if}
            } else {
                ${'$'}{3:body_else}
            }
        """.trimIndent(),
        description = "If/else statement",
        labelDetail = "if_else + condition + body_if + body_else"
    ),
    Snippet(
        prefix = "if_else_if",
        body = """
            if (${'$'}{1:condition}) {
                ${'$'}{2:body_if}
            } else if (${'$'}{3:condition_else_if}) {
                ${'$'}{4:body_else_if}
            } else {
                ${'$'}{5:body_else}
            }
        """.trimIndent(),
        description = "If else/if else statement",
        labelDetail = "if_else_if + condition + body_if + condition_else_if + body_else_if + body_else"
    ),
    Snippet(
        prefix = "if_oneline",
        body = "if (${'$'}{1:condition}) ${'$'}{2:action};",
        description = "If statement one-liner",
        labelDetail = "if_oneline + condition + action"
    ),
    Snippet(
        prefix = "object",
        body = """
            object ${'$'}{1:name} {
                ${'$'}{2:field};
            }
        """.trimIndent(),
        description = "Object",
        labelDetail = "object + name + field"
    ),
    Snippet(
        prefix = "operation",
        body = """
            operation ${'$'}{1:name} (${'$'}{2:args}) {
                ${'$'}{3:body}
            }
        """.trimIndent(),
        description = "Operation statement",
        labelDetail = "operation + name + args + body"
    ),
    Snippet(
        prefix = "print",
        body = "print(${'$'}{1:value});",
        description = "Print statement",
        labelDetail = "print + value"
    ),
    Snippet(
        prefix = "query",
        body = "query ${'$'}{1:name} () = ${'$'}{2:from} @ { ${'$'}{3:where} } ( ${'$'}{4:select} );",
        description = "Query declaration",
        labelDetail = "query + name + from + where + select"
    ),
    Snippet(
        prefix = "struct",
        body = """
            struct ${'$'}{1:name} {
                ${'$'}{2:field};
            }
        """.trimIndent(),
        description = "Struct declaration",
        labelDetail = "struct + name + field"
    )
)
