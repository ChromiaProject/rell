/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.completion

import com.google.gson.JsonObject
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.indexer.RellCompilerUtils
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellCompletionSymbolService
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.CompletionItem
import java.net.URI
import kotlin.collections.component1
import kotlin.collections.component2

class RellCompletionService(
    private val rellSymbolService: RellCompletionSymbolService,
    private val completionItemFactory: CompletionItemFactory
) {
    private val rellCompilerUtils = RellCompilerUtils()

    fun getCompletions(
        fileUri: URI,
        offset: Int,
        indexer: WorkspaceIndexer,
        document: Document
    ): List<CompletionItem> {
        val activeImportSymbol = rellSymbolService.getActiveImportSymbol(fileUri, offset, document, indexer)
        val completions = if (activeImportSymbol != null) {
            val moduleSymbols = rellSymbolService.getSymbolInfoForImportedModule(activeImportSymbol, document, indexer)
            completionItemFactory.createCompletionForModuleSymbols(moduleSymbols)
        } else {
            getRootCompletions(fileUri, offset, document, indexer)
        }
        return addExtraProperties(completions, fileUri, offset)
    }

    private fun addExtraProperties(completions: List<CompletionItem>, fileUri: URI, offset: Int): List<CompletionItem> {
        val extraProperties = JsonObject().apply {
            addProperty("offset", offset)
            addProperty("fileUri", fileUri.toString())
        }
        return completions.map { completion ->
            completion.data = extraProperties
            completion
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

        return completionItemFactory.createCompletionItems(completions, trimPrefixDot) +
            completionItemFactory.createKeywordsCompletionItems() +
            getAvailableModulesCompletion(fileUri, indexer, getAvailableModules) +
            completionItemFactory.createSnippetCompletions()
    }

    private fun shouldGetAvailableModules(fileUri: URI, document: Document, resource: Resource, offset: Int): Boolean {
        val validOffset = if (offset != 0 && document.content.length == offset) offset - 1 else offset
        return rellSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, validOffset) != null
    }

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
                completionItemFactory.createAvailableModuleCompletion(moduleName)
            }
        }
        return availableModuleImports.distinctBy { it.label }
    }

    private fun isModuleValidCandidate(
        resource: Resource,
        fileResource: Resource,
        moduleName: R_ModuleName?,
        activeModule: R_ModuleName
    ) = (resource.isTest() && !fileResource.isTest()) || (moduleName == null || moduleName == activeModule)

    fun getReplacementText(document: Document, offset: Int, completion: String): String {
        if (completion.isEmpty()) return ""
        val safeOffset = offset.coerceIn(0, document.content.length)

        val lookBackStart = (safeOffset - 50).coerceAtLeast(0)
        val textBefore = document.getTextIn(Interval.of(lookBackStart, safeOffset - 1))

        var maxMatchLength = 0

        for (i in textBefore.indices) {
            val candidate = textBefore.substring(i)
            val matchLength = findCommonPrefix(candidate, completion)
            if (matchLength > 0 && i + matchLength == textBefore.length) {
                maxMatchLength = maxOf(maxMatchLength, matchLength)
            }
        }

        return completion.substring(maxMatchLength)
    }

    private fun findCommonPrefix(str1: String, str2: String): Int {
        val minLength = minOf(str1.length, str2.length)
        var commonLength = 0

        for (i in 0 until minLength) {
            if (str1[i] == str2[i]) {
                commonLength++
            } else {
                break
            }
        }

        return commonLength
    }
}
