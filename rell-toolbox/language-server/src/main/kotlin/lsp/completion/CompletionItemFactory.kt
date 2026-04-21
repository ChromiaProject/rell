/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.completion

import com.google.common.collect.Multimap
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeCompletionParam
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.common.RellKeywords
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.symbols.RellRelevantImportSymbol
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

class CompletionItemFactory {

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

    fun createCompletionItems(
        completions: Multimap<String, IdeCompletion>,
        trimPrefixDot: Boolean
    ): List<CompletionItem> {
        return completions.asMap().flatMap { (key, ideCompletions) ->
            toSemanticCompletions(key, ideCompletions, trimPrefixDot)
        }
    }

    fun createKeywordsCompletionItems(): List<CompletionItem> {
        return RellKeywords.asList().map {
            CompletionItem().apply {
                label = it
                labelDetails = CompletionItemLabelDetails().apply {
                    description = "keyword"
                }
                kind = CompletionItemKind.Keyword
            }
        }
    }

    fun createAvailableModuleCompletion(moduleName: ModuleName?): List<CompletionItem> = listOf(
        CompletionItem().apply {
            label = "import $moduleName.*;"
            labelDetails = CompletionItemLabelDetails().apply {
                description = "module"
            }
            kind = CompletionItemKind.Module
            insertText = "import $moduleName.*;"
        },
        CompletionItem().apply {
            label = "import $moduleName.{T};"
            labelDetails = CompletionItemLabelDetails().apply {
                description = "module"
            }
            kind = CompletionItemKind.Module
            insertText = $$"import $$moduleName.{$0};"
            insertTextFormat = InsertTextFormat.Snippet
        }
    )

    fun createCompletionForModuleSymbols(moduleSymbols: List<IdeSymbolInfo>): List<CompletionItem> {
        return moduleSymbols.map { ideSymbol ->
            val symbolName = ideSymbol.defId?.encode()?.substringAfterLast("[")?.substringBeforeLast("]")
            CompletionItem().apply {
                label = symbolName ?: ""
                labelDetails = CompletionItemLabelDetails().apply {
                    description = RellRelevantImportSymbol.fromIdeSymbolKind(ideSymbol.kind)?.displayName ?: ""
                }
                kind = ideSymbolKindToCompletionKind[ideSymbol.kind]
                insertText = symbolName ?: ""
                documentation = Either.forRight(MarkupContent("markdown", formatDocSymbol(ideSymbol.doc)))
            }
        }
    }

    private fun toSemanticCompletions(key: String, ideCompletions: Collection<IdeCompletion>, trimPrefixDot: Boolean):
        List<CompletionItem> {
        return ideCompletions.map { completion ->
            CompletionItem().apply {
                label = key
                labelDetails = CompletionItemLabelDetails().apply {
                    detail = getLabelDetail(completion)
                    description = getLabelDetailDescription(completion)
                }
                kind = getKind(completion)
                documentation = Either.forRight(getDocumentation(completion))
                insertText = getInsertText(key, completion, trimPrefixDot)
                insertTextFormat = InsertTextFormat.Snippet
                preselect = false
                if (completion.deprecated) {
                    tags = listOf(CompletionItemTag.Deprecated)
                }
            }
        }
    }

    private fun getLabelDetailDescription(completion: IdeCompletion): String {
        return completion.kind.msg
    }

    private fun getKind(completion: IdeCompletion): CompletionItemKind {
        return docSymbolKindToCompletionKind[completion.kind] ?: CompletionItemKind.Text
    }

    private fun getDocumentation(completion: IdeCompletion): MarkupContent {
        return MarkupContent("markdown", formatDocSymbol(completion.docSymbol))
    }

    private fun getInsertText(key: String, completion: IdeCompletion, trimPrefixDot: Boolean): String {
        return when (completion.kind) {
            DocSymbolKind.FUNCTION,
            DocSymbolKind.OPERATION,
            DocSymbolKind.QUERY -> key + formatInsertTextParams(completion.params)

            else -> {
                if (trimPrefixDot) {
                    key.trimStart('.')
                } else {
                    key
                }
            }
        }
    }

    private fun formatInsertTextParams(params: List<IdeCompletionParam>?): String {
        return buildString {
            append("(")
            params?.forEachIndexed { index, param ->
                append("\${${index + 1}:${param.name}}")
                if (index < params.size - 1) {
                    append(", ")
                }
            }
            append(")")
        }
    }

    private fun getLabelDetail(completion: IdeCompletion): String {
        return when (completion.kind) {
            DocSymbolKind.FUNCTION,
            DocSymbolKind.OPERATION,
            DocSymbolKind.QUERY -> formatLabelDetails(completion)

            else -> ""
        }
    }

    private fun formatLabelDetails(completion: IdeCompletion): String {
        val params = completion.params?.joinToString(", ", "(", ")", -1, "") { it.code }
        val result = completion.result ?: ""
        return "$params: $result"
    }

    companion object {
        private val docSymbolKindToCompletionKind = mapOf(
            DocSymbolKind.NONE to CompletionItemKind.Text,
            DocSymbolKind.MODULE to CompletionItemKind.Module,
            DocSymbolKind.NAMESPACE to CompletionItemKind.Module,
            DocSymbolKind.CONSTANT to CompletionItemKind.Constant,
            DocSymbolKind.PROPERTY to CompletionItemKind.Property,
            DocSymbolKind.TYPE to CompletionItemKind.TypeParameter,
            DocSymbolKind.TYPE_EXTENSION to CompletionItemKind.TypeParameter,
            DocSymbolKind.ENUM to CompletionItemKind.Enum,
            DocSymbolKind.ENUM_VALUE to CompletionItemKind.EnumMember,
            DocSymbolKind.ENTITY to CompletionItemKind.Class,
            DocSymbolKind.ENTITY_ATTR to CompletionItemKind.Field,
            DocSymbolKind.OBJECT to CompletionItemKind.Class,
            DocSymbolKind.OBJECT_ATTR to CompletionItemKind.Field,
            DocSymbolKind.TUPLE_ATTR to CompletionItemKind.Field,
            DocSymbolKind.AT_VAR_COL to CompletionItemKind.Property,
            DocSymbolKind.STRUCT to CompletionItemKind.Struct,
            DocSymbolKind.STRUCT_ATTR to CompletionItemKind.Field,
            DocSymbolKind.CONSTRUCTOR to CompletionItemKind.Method,
            DocSymbolKind.FUNCTION to CompletionItemKind.Function,
            DocSymbolKind.OPERATION to CompletionItemKind.Function,
            DocSymbolKind.QUERY to CompletionItemKind.Function,
            DocSymbolKind.ALIAS to CompletionItemKind.Reference,
            DocSymbolKind.PARAMETER to CompletionItemKind.TypeParameter,
            DocSymbolKind.IMPORT to CompletionItemKind.Reference,
            DocSymbolKind.AT_VAR_DB to CompletionItemKind.Field,
            DocSymbolKind.VAR to CompletionItemKind.Variable,
        )

        private val ideSymbolKindToCompletionKind = mapOf(
            IdeSymbolKind.DEF_CONSTANT to CompletionItemKind.Constant,
            IdeSymbolKind.DEF_ENTITY to CompletionItemKind.Class,
            IdeSymbolKind.DEF_ENUM to CompletionItemKind.Enum,
            IdeSymbolKind.DEF_FUNCTION to CompletionItemKind.Function,
            IdeSymbolKind.DEF_FUNCTION_ABSTRACT to CompletionItemKind.Function,
            IdeSymbolKind.DEF_FUNCTION_EXTEND to CompletionItemKind.Function,
            IdeSymbolKind.DEF_FUNCTION_EXTENDABLE to CompletionItemKind.Function,
            IdeSymbolKind.DEF_NAMESPACE to CompletionItemKind.Module,
            IdeSymbolKind.DEF_OBJECT to CompletionItemKind.Class,
            IdeSymbolKind.DEF_OPERATION to CompletionItemKind.Function,
            IdeSymbolKind.DEF_QUERY to CompletionItemKind.Function,
            IdeSymbolKind.DEF_STRUCT to CompletionItemKind.Struct,
        )
    }
}
