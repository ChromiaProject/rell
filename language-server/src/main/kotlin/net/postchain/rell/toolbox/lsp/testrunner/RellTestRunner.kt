package net.postchain.rell.toolbox.lsp.testrunner

import java.net.URI
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import net.postchain.rell.toolbox.lsp.symbols.NodeInfo
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind


class RellTestRunner(val workspaceManager: RellWorkspaceManager, val symbolService: RellSymbolService) {

    fun getTestFiles(workspaceUri: URI): List<RellTestFile> {
        val srcDir = workspaceManager.findSourceDirURI(workspaceUri)
        val indexer = workspaceManager.getIndexerFor(srcDir)
        return indexer.fileUriResourceMap
            .filter { it.value.isTest() }
            .map { (uri, resource) -> RellTestFile(uri, resource.moduleInfo?.name?.str(), true, getTestCases(uri)) }
    }

    fun getTestCases(fileUri: URI): List<RellTestCase> {
        val resource = workspaceManager.getResource(fileUri) ?: return listOf()
        if (!resource.isTest()) {
            return listOf()
        }
        return populateTestCases(fileUri, resource)
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