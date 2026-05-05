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
        assertThat(parseTreeWithErrors.syntaxErrors).extracting { it.message }.containsExactly("missing ';' at '}'")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `ParseTree finds multiple errors in single rell file`() {
        val parseTreeWithErrors = rellDesc.buildParseTree(getFileContent("multiple_syntax_error.rell"))
        assertThat(parseTreeWithErrors.syntaxErrors).extracting { it.message }.containsAtLeast(
            "missing ';' at 'function'",
            "missing ';' at '}'",
            // ANTLR's expected-token list ordering is determined by token IDs in the .g4 grammar,
            // not by the rule's literal source order. Updated to match Rell.g4's emitted order
            // (the previous strings were captured from the legacy better-parse output).
            "extraneous input 'va' expecting {<EOF>, 'abstract', 'mutable', 'override', '@', 'entity', 'class', 'object', 'struct', 'record', 'enum', 'function', 'namespace', 'import', 'operation', 'query', 'include', 'val'}",
            "extraneous input ';' expecting {'.', '(', 'false', 'true', 'null', 'struct', 'virtual', 'if', 'when', '++', '--', '+', '-', 'not', 'create', '\$', '[', RULE_ID, RULE_DECIMAL, RULE_BIG_INTEGER, RULE_NUMBER, RULE_BYTES, RULE_STRING}"
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
