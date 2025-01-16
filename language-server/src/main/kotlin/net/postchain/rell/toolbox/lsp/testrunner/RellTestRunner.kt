package net.postchain.rell.toolbox.lsp.testrunner

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.server.RellIndexingManager
import net.postchain.rell.toolbox.lsp.symbols.NodeInfo
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import java.net.URI

class RellTestRunner(val indexingManager: RellIndexingManager, private val symbolService: RellSymbolService) {

    fun getTestFiles(workspaceUri: URI): List<RellTestFile> {
        return indexingManager.getAllIndexers().flatMap { indexer ->
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
        }
    }

    fun getTestCases(fileUri: URI): List<RellTestCase> {
        val resource = indexingManager.getResource(fileUri) ?: return listOf()
        return getTestCases(resource)
    }

    fun getTestCases(resource: Resource): List<RellTestCase> {
        if (!resource.isTest()) {
            return listOf()
        }
        return populateTestCases(resource.fileUri, resource)
    }

    private fun populateTestCases(fileUri: URI, resource: Resource): List<RellTestCase> {
        val moduleName = resource.moduleInfo?.name?.str()
        val range = Range(Position(0, 0), Position(0, 0))
        val rootNode = NodeInfo(moduleName ?: "Unknown", range, range, SymbolKind.File)
        val testTree = symbolService.getDocumentSymbolsWithRoot(rootNode, resource)

        val testCases = mutableListOf<RellTestCase>()
        visitTreeNodes(testTree, fileUri, testCases)
        return testCases
    }

    private fun visitTreeNodes(testTreeNode: DocumentSymbol, fileUri: URI, testCases: MutableList<RellTestCase>) {
        if (isTestCase(testTreeNode)) {
            val range = testTreeNode.range
            val name = testTreeNode.name
            val uri = fileUri.toString()
            val testCase = RellTestCase(name, range, uri)
            testCases.add(testCase)
        }

        if (testTreeNode.children == null) {
            return
        }
        for (child in testTreeNode.children) {
            visitTreeNodes(child, fileUri, testCases)
        }
    }

    private fun isTestCase(testTreeNode: DocumentSymbol): Boolean {
        return testTreeNode.kind == SymbolKind.Function &&
            (testTreeNode.name == "test" || testTreeNode.name.startsWith("test_"))
    }
}

data class RellTestCase(val name: String, val range: Range, val uri: String)

data class RellTestFile(
    val uri: URI,
    val moduleName: String? = null,
    val canResolveChildren: Boolean = true,
    val testCases: List<RellTestCase> = listOf()
)
