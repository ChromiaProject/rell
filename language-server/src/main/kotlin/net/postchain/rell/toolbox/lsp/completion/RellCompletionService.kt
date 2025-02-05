package net.postchain.rell.toolbox.lsp.completion

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeCompletionParam
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.common.RellKeywords
import net.postchain.rell.toolbox.indexer.RellCompilerUtils
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.symbols.RellRelevantImportSymbol
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
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

class RellCompletionService(private val rellSymbolService: RellSymbolService) {
    private val rellCompilerUtils = RellCompilerUtils()

    fun getCompletions(
        fileUri: URI,
        offset: Int,
        indexer: WorkspaceIndexer,
        document: Document
    ): List<CompletionItem> {
        val activeImportSymbol = rellSymbolService.getActiveImportSymbol(fileUri, offset, document, indexer)
        return if (activeImportSymbol != null) {
            val moduleSymbols = rellSymbolService.getSymbolInfoForImportedModule(activeImportSymbol, document, indexer)
            getCompletionForModuleSymbols(moduleSymbols)
        } else {
            getRootCompletions(fileUri, offset, document, indexer)
        }
    }

    private fun shouldTrimPrefixDot(doc: Document, offset: Int): Boolean {
        return doc.previousNonLetterChar(offset) == '.'
    }

    private fun getRootCompletions(
        fileUri: URI,
        offset: Int,
        document: Document,
        indexer: WorkspaceIndexer
    ): List<CompletionItem> {
        val filePath = rellCompilerUtils.createCompilerSourcePath(fileUri, indexer.workspaceUri)
        val resource = indexer.getResource(fileUri) ?: return emptyList()

        val options = C_CompilerOptions.builder()
            .defaultLib(true)
            .hiddenLib(false)
            .ideDocSymbolsEnabled(true)
            .ide(true)
            .build()

        val sourceDir = IdeDirApi.mapDir(indexer.fileMap)
        val completions = IdeApi.getCompletions(sourceDir, filePath, offset, options)
        val trimPrefixDot = shouldTrimPrefixDot(document, offset)
        val getAvailableModules = shouldGetAvailableModules(fileUri, document, resource, offset)

        return createCompletionItems(
            completions,
            trimPrefixDot
        ) + createKeywordsCompletionItems() + getAvailableModulesCompletion(fileUri, indexer, getAvailableModules)
    }

    private fun shouldGetAvailableModules(fileUri: URI, document: Document, resource: Resource, offset: Int): Boolean =
        rellSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, offset) != null

    private fun getAvailableModulesCompletion(
        fileUri: URI,
        indexer: WorkspaceIndexer,
        getAvailableModules: Boolean
    ): List<CompletionItem> {
        if (!getAvailableModules) return listOf()

        val fileResource = indexer.getResource(fileUri)
        val activeModule = fileResource?.moduleInfo?.name ?: return emptyList()
        val availableModuleImports = indexer.resources.flatMap { resource ->
            val moduleName = resource.moduleInfo?.name
            if (isModuleValidCandidate(resource, fileResource, moduleName, activeModule)) {
                emptyList()
            } else {
                createAvailableModuleCompletion(moduleName)
            }
        }
        return availableModuleImports.distinctBy { it.label }
    }

    private fun createAvailableModuleCompletion(moduleName: R_ModuleName?) = listOf(
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
            insertText = "import $moduleName.{$0};"
            insertTextFormat = InsertTextFormat.Snippet
        }
    )

    private fun isModuleValidCandidate(
        resource: Resource,
        fileResource: Resource,
        moduleName: R_ModuleName?,
        activeModule: R_ModuleName
    ) = (resource.isTest() && !fileResource.isTest()) || (moduleName == null || moduleName == activeModule)

    private fun getCompletionForModuleSymbols(modulesSymbols: List<IdeSymbolInfo>): List<CompletionItem> {
        return modulesSymbols.map { ideSymbol ->
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
