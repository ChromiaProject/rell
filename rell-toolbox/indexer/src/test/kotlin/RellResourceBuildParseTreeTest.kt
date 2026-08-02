/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

@Suppress("JAVA_CLASS_ON_COMPANION")
class RellResourceBuildParseTreeTest {
    private fun getFileContent(suffix: String): String {
        return File(rellFilesError.find { it.toString().endsWith(suffix) }!!).readText()
    }

    @Test
    fun `ParseTree finds no errors in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTree(getFileContent("no_errors.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds error in single rell file`() {
        val parseTreeWithErrors =
            rellDesc.buildParseTree(getFileContent("single_syntax_error.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors).extracting { it.message }.containsExactly("';' expected")
    }

    @Test
    fun `ParseTree finds multiple errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTree(getFileContent("multiple_syntax_error.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors).extracting { it.message }.containsAtLeast(
            "';' expected",
            // The expected-token sets here are too wide to enumerate, so the message degrades to
            // a plain "unexpected token" without alternatives.
            "Unexpected token 'va'",
            "Unexpected token ';'"
        )
    }

    @Test
    fun `ParseTree finds no error in semantic error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTree(getFileContent("/semantic_error.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors.size).isEqualTo(0)
    }

    @Test
    fun `ParseTree finds no error in import error file`() {
        val parseTreeWithErrors = rellDesc.buildParseTree(getFileContent("/import.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors.size).isEqualTo(0)
    }

    companion object {
        var rellFilesError: MutableList<URI> = mutableListOf()
        private var rellFilesCorrect: MutableList<URI> = mutableListOf()
        private val classLoader = javaClass.getClassLoader()
        private val workspaceError = File(classLoader.getResource("rellDappWithErrors")!!.file).absoluteFile
        private val workspaceCorrect = File(RellResourceBuildModuleInfoTest.classLoader.getResource("rellDapp")!!.file)
        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser(), ChromiaModelProvider(null))

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
