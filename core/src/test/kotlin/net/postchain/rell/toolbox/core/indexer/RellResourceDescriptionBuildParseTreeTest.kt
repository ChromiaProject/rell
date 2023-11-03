package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildParseTreeTest {
    //TODO: Test only works in isolation (not running test suite)
    @Test
    fun `ParseTree finds no errors in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find { it.toString().endsWith("no_errors.rell") }!!)
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds error in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find { it.toString().endsWith("syntax_error.rell") }!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(1)
    }

    @Test
    fun `ParseTree finds multiple errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find {
            it.toString().endsWith("multiple_syntax_error.rell")
        }!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(4)
    }

    @Test
    fun `ParseTree finds no error in semantic error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find {
            it.toString().endsWith("semantic_error.rell")
        }!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)

    }

    @Test
    fun `ParseTree finds no error in import error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find {
            it.toString().endsWith("import.rell")
        }!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    @Test
    fun `Error is thrown when file is not found`() {
        //TODO make it catch a proper error
        assertThrows<IllegalArgumentException> {
            rellDesc.buildParseTreeWithSyntaxErrors(URI("noFile.rell"))
        }

    }
        companion object {
            var rellFilesError: MutableList<URI> = mutableListOf()
            var rellFilesCorrect: MutableList<URI> = mutableListOf()
            val classLoader = javaClass.getClassLoader()
            val workspaceError = File(classLoader.getResource("rellDappWithErrors").file).absoluteFile
            val workspaceCorrect = File(RellResourceDescriptionBuildModuleInfoTest.classLoader.getResource("rellDapp").file)
            val rellDesc = RellResourceDescription(workspaceError.toURI())

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