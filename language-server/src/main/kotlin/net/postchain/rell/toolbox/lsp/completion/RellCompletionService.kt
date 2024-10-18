package net.postchain.rell.toolbox.lsp.completion

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeCompletionParam
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.common.RellKeywords
import net.postchain.rell.toolbox.indexer.RellCompilerUtils
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import kotlin.collections.component1
import kotlin.collections.component2

class RellCompletionService {
    private val rellCompilerUtils = RellCompilerUtils()

    private val kindMapping = mapOf(
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

    fun getCompletions(
        fileUri: URI,
        offset: Int,
        indexer: WorkspaceIndexer,
        trimPrefixDot: Boolean = false
    ): List<CompletionItem> {
        val filePath = rellCompilerUtils.createCompilerSourcePath(fileUri, indexer.workspaceUri)
        indexer.getResource(fileUri) ?: return emptyList()

        val options = C_CompilerOptions.builder()
            .defaultLib(true)
            .hiddenLib(false)
            .ideDocSymbolsEnabled(true)
            .ide(true)
            .build()

        val sourceDir = IdeDirApi.mapDir(indexer.fileMap)
        val completions = IdeApi.getCompletions(sourceDir, filePath, offset, options)
        return createCompletionItems(completions, trimPrefixDot) + createKeywordsCompletionItems()
    }

    private fun createKeywordsCompletionItems(): List<CompletionItem> {
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

    private fun createCompletionItems(
        completions: Multimap<String, IdeCompletion>,
        trimPrefixDot: Boolean
    ): List<CompletionItem> {
        return completions.asMap().flatMap { (key, ideCompletions) ->
            toSemanticCompletions(key, ideCompletions, trimPrefixDot)
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
        return kindMapping[completion.kind] ?: CompletionItemKind.Text
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
}
