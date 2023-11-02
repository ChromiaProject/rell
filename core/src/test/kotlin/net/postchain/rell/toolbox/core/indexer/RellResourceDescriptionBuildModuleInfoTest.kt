package net.postchain.rell.toolbox.core.indexer

import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildModuleInfoTest {

    @Test
    fun `just a runner`() {
        var rellFiles: MutableMap<String, URI> = mutableMapOf()
        val classLoader = javaClass.getClassLoader()
        val workspace = File(classLoader.getResource("rellDappWithErrors").file).absoluteFile
        findRellFilesInWorkspace(workspace, rellFiles)

        val rellDesc = RellResourceDescription()
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(rellFiles["syntax_error.rell"]!!)
        //val rellModuleInfo = rellDesc.buildModuleInfo(workspace.toURI(), rellFiles["syntax_error.rell"]!!, parseTree)
        //assertThat(parseTree.exception).isNotNull()
    }
}