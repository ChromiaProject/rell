package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceBuildParseTreeTest {
    //TODO: Test only works in isolation (not running test suite)
    private fun getFileContent(suffix: String): String {
        return File(rellFilesError.find { it.toString().endsWith(suffix) }!!).readText()
    }

    @Test
    fun `ParseTree finds no errors in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(getFileContent("no_errors.rell"))
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds error in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(getFileContent("single_syntax_error.rell"))
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(1)
    }

    @Test
    fun `ParseTree finds multiple errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(getFileContent("multiple_syntax_error.rell"))
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(4)
    }

    @Test
    fun `ParseTree finds no error in semantic error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(getFileContent("semantic_error.rell"))
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)

    }

    @Test
    fun `ParseTree finds no error in import error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(getFileContent("import.rell"))
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    companion object {
        var rellFilesError: MutableList<URI> = mutableListOf()
        var rellFilesCorrect: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors").file).absoluteFile
        val workspaceCorrect = File(RellResourceBuildModuleInfoTest.classLoader.getResource("rellDapp").file)
        val rellDesc = RellResourceFactory(workspaceError.toURI())

        @JvmStatic
        @BeforeAll
        fun setup() {
            findRellFilesInWorkspace(
                workspaceError,
                rellFilesError
            )
            findRellFilesInWorkspace(
                workspaceCorrect,
                rellFilesCorrect
            )
        }
    }
}