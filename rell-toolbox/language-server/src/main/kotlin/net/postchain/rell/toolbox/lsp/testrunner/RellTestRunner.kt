/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.testrunner

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.server.RellIndexingManager
import net.postchain.rell.toolbox.lsp.symbols.NodeInfo
import net.postchain.rell.toolbox.lsp.symbols.OutlineNode
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import java.net.URI

class RellTestRunner(val indexingManager: RellIndexingManager, private val symbolService: RellSymbolService) {

    fun getTestFiles(workspaceUri: URI): List<RellTestFile> {
        return indexingManager.getIndexerForFolderOrNull(workspaceUri)?.let { indexer ->
            indexer.fileUriResourceMap
                .filter { it.value.isTest() }
                .map { (uri, resource) ->
                    RellTestFile(
                        uri,
                        resource.moduleInfo?.name?.str(),
                        true,
                        getTestCases(resource)
                    )
                }
        } ?: emptyList()
    }

    fun getTestFile(fileUri: URI): RellTestFile? {
        val resource = indexingManager.getResource(fileUri) ?: return null
        if (!resource.isTest()) return null
        return RellTestFile(
            fileUri,
            resource.moduleInfo?.name?.str(),
            true,
            getTestCases(resource)
        )
    }

    fun getTestCases(fileUri: URI): List<RellTestCase> {
        val resource = indexingManager.getResource(fileUri) ?: return listOf()
        return getTestCases(resource)
    }


    private fun getTestCases(resource: Resource): List<RellTestCase> {
        if (!resource.isTest()) {
            return listOf()
        }
        return populateTestCases(resource.fileUri, resource)
    }

    private fun populateTestCases(fileUri: URI, resource: Resource): List<RellTestCase> {
        val moduleName = resource.moduleInfo?.name?.str()
        val range = Range(Position(0, 0), Position(0, 0))
        val rootNode = NodeInfo(moduleName ?: "Unknown", range, range, SymbolKind.File)
        val outlineTreeRoot = symbolService.getOutlineTreeRoot(rootNode, resource)

        val testCases = mutableListOf<RellTestCase>()
        visitTreeNodes(outlineTreeRoot, fileUri, testCases)
        return testCases
    }

    private fun visitTreeNodes(outlineNode: OutlineNode, fileUri: URI, testCases: MutableList<RellTestCase>) {
        val testTreeNode = outlineNode.toDocumentSymbol()
        if (isTestCase(outlineNode)) {
            val range = testTreeNode.range
            val name = testTreeNode.name
            val uri = fileUri.toString()
            val testCase = RellTestCase(name, range, uri)
            testCases.add(testCase)
        }

        for (child in outlineNode.getChildren()) {
            visitTreeNodes(child, fileUri, testCases)
        }
    }

    private fun isTestCase(outlineNode: OutlineNode): Boolean {
        val info = outlineNode.getInfo()
        return info.symbolKind == SymbolKind.Function &&
            (followsTestNamingConvention(info.text) || info.hasAnnotation("test")) &&
            !info.hasAnnotation("disabled")
    }

    private fun followsTestNamingConvention(name: String): Boolean {
        return name == "test" || name.startsWith("test_")
    }
}

data class RellTestCase(val name: String, val range: Range, val uri: String)

data class RellTestFile(
    val uri: URI,
    val moduleName: String? = null,
    val canResolveChildren: Boolean = true,
    val testCases: List<RellTestCase> = listOf()
)
