package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildModuleInfoTest {

    @Test
    fun `sRellFile finds single errors in single rell file`() {

        val sourceDir : C_SourceDir = C_SourceDir.diskDir(workspaceCorrect)
        sourceDir.files(
            C_SourcePath.of(workspaceCorrect.list()!!.asList()))
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFilesCorrect.find { it.toString().endsWith("entities.rell") }!!)
        val rellModuleInfo = rellDesc.compileResult(workspaceCorrect.toURI(), rellFilesCorrect.find { it.toString().endsWith("entities.rell") }!!, parseTreeWithErrors.first, sourceDir)
        //assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
        //assertThat(parseTree.exception).isNotNull()
    }

    companion object {
        var rellFilesErrors: MutableList<URI> = mutableListOf()
        var rellFilesCorrect: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors").file)
        val workspaceCorrect = File(classLoader.getResource("rellDapp").file)
        val rellDesc = RellResourceDescription()
        @JvmStatic
        @BeforeAll
        fun setup() {
            findRellFilesInWorkspace(
                workspaceError,
                rellFilesErrors
            )
            findRellFilesInWorkspace(
                workspaceCorrect,
                rellFilesCorrect
            )
        }
    }
}