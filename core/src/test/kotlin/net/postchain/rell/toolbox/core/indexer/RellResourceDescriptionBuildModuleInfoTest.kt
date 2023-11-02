package net.postchain.rell.toolbox.core.indexer

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildModuleInfoTest {

    @Test
    fun `sRellFile finds single errors in single rell file`() {
        val fileUri = rellFilesCorrect.find { it.toString().endsWith("entities.rell") }!!

        val rellCompilerPaths = RellCompilerPaths(workspaceCorrect.toURI())
        val compilerSourcePath = rellCompilerPaths.createCompilerSourcePath(fileUri)
        val rellCompilerFilePath = rellCompilerPaths.createRellCompilerFilePath(compilerSourcePath)

        val rellDesc = RellResourceDescription(workspaceCorrect.toURI())
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(fileUri)
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(rellCompilerFilePath, parseTree.first)

        val rellModuleInfo = rellDesc.compileResult(
            fileUri,
            sRellFile.first
        )
        //assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
        //assertThat(parseTree.exception).isNotNull()
    }

    companion object {
        var rellFilesErrors: MutableList<URI> = mutableListOf()
        var rellFilesCorrect: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors").file)
        val workspaceCorrect = File(classLoader.getResource("rellDapp").file)


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