package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildParseTreeTest {
    @Test
    fun `ParseTree finds no errors in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find { it.toString().endsWith("no_errors.rell") }!!)
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds error in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTreeWithSyntaxErrors(rellFilesError.find {
                it.toString().endsWith("syntax_error.rell")
            }!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(4)
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
    fun `Builds minimal parse tree when error occurred of building initial tree`() {
        val parseTreeWithError = rellDesc.buildParseTreeWithSyntaxErrors(URI("noFile.rell"))
        assertThat(parseTreeWithError.first.children.size).isEqualTo(1)
        assertThat(parseTreeWithError.first.children.first().toString()).isEqualTo("<EOF>")
    }

    companion object {
        var rellFilesError: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors").file).absoluteFile
        val rellDesc = RellResourceDescription(workspaceError.toURI())

        @JvmStatic
        @BeforeAll
        fun setup() {
            findRellFilesInWorkspace(
                workspaceError,
                rellFilesError
            )
        }
    }
}
