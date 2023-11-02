package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildParseTreeTest {

    //SyntaxErrors only
    @Test
    fun `ParseTree finds no errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFiles["no_errors.rell"]!!)
        assertThat(parseTreeWithErrors.second.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds error in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFiles["syntax_error.rell"]!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(1)
    }

    @Test
    fun `ParseTree finds multiple errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTreeWithSyntaxErrors(rellFiles["multiple_syntax_error.rell"]!!)
        //TODO proper assertion on error
        assertThat(parseTreeWithErrors.second.size).isEqualTo(4)
    }

    companion object {
        var rellFiles: MutableMap<String, URI> = mutableMapOf()
        val classLoader = javaClass.getClassLoader()
        val workspace = File(classLoader.getResource("rellDappWithErrors").file).absoluteFile
        val rellDesc = RellResourceDescription()
        @JvmStatic
        @BeforeAll
        fun setup() {
            findRellFilesInWorkspace(
                workspace,
                rellFiles
            )
        }
    }
}