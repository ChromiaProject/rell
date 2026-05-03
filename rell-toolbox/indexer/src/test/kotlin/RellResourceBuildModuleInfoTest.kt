/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import kotlin.io.path.readText
import kotlin.io.path.toPath

@Suppress("JAVA_CLASS_ON_COMPANION")
class RellResourceBuildModuleInfoTest {

    private fun filterMessages(messageList: List<C_Message>, fileUri: URI): List<C_Message> {
        return messageList.filter {
            fileUri.path == workspaceError.toURI().path + it.pos.path().str()
        }
    }

    // TODO make it so a compiler can take in one file without ws defined
    @Test
    fun `compiler finds errors in from imported file`() {
        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellCompilerUtils = RellCompilerUtils()
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()

        val fileUriImport = rellFilesErrors.find { it.toString().endsWith("/import.rell") }!!
        val fileUriSemanticError = rellFilesErrors.find { it.toString().endsWith("/semantic_error.rell") }!!

        val fileContentImport = File(fileUriImport).readText()
        val fileContentSemanticError = File(fileUriSemanticError).readText()

        val importParseResult = rellDesc.buildParseTree(fileContentImport)
        val parseTreeImport = importParseResult.parseTree
        val semanticErrorParseResult = rellDesc.buildParseTree(fileContentSemanticError)
        val parseTreeSematicError = semanticErrorParseResult.parseTree

        val compilerSourcePathImport = rellCompilerUtils.createCompilerSourcePath(fileUriImport, workspaceError.toURI())
        val compilerSourcePathSemanticError =
            rellCompilerUtils.createCompilerSourcePath(fileUriSemanticError, workspaceError.toURI())

        val sRellFileImport = rellDesc.buildRellAstWithCompilerErrors(
            compilerSourcePathImport,
            parseTreeImport,
        ).first
        val sRellFileSemanticError = rellDesc.buildRellAstWithCompilerErrors(
            compilerSourcePathSemanticError,
            parseTreeSematicError,
        ).first

        val rellCompileResultSemanticError = rellDesc.compileResult(
            compilerSourcePathSemanticError,
            sRellFileSemanticError,
            fileMap,
            fileUriSemanticError.toPath().readText()
        )

        val errorMessagesSemanticError = filterMessages(rellCompileResultSemanticError!!.messages, fileUriSemanticError)
        assertThat(errorMessagesSemanticError.size).isEqualTo(3)

        val rellCompileResultImport = rellDesc.compileResult(
            compilerSourcePathImport,
            sRellFileImport,
            fileMap,
            fileUriImport.toPath().readText()
        )

        val errorMessages = filterMessages(rellCompileResultImport!!.messages, fileUriImport)
        assertThat(errorMessages).isEmpty()
    }

    // TODO make it so a compiler can take in one file without ws defined
    @Test
    fun `compiler finds single errors in single rell file`() {
        val fileUri = rellFilesErrors.find { it.toString().endsWith("/semantic_error.rell") }!!
        val fileContent = File(fileUri).readText()
        val rellCompilerUtils = RellCompilerUtils()
        val compilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceError.toURI())

        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val parsingResult = rellDesc.buildParseTree(fileContent)
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(
            compilerSourcePath,
            parsingResult.parseTree,
        ).first
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile,
            fileMap,
            fileUri.toPath().readText()
        )

        val errorMessages = filterMessages(rellCompileResult!!.messages, fileUri)

        assertThat(errorMessages.size).isEqualTo(3)
        assertThat(errorMessages).extracting(C_Message::code).containsAtLeast(
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(11:10)",
            "unknown_name:b",
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(7:10)"
        )
    }

    // TODO: Redo tests to be specific to the method of RellResourceFactory class

    companion object {
        var rellFilesErrors: MutableList<URI> = mutableListOf()
        private var rellFilesCorrect: MutableList<URI> = mutableListOf()
        val classLoader: ClassLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors")!!.file)
        private val workspaceCorrect = File(classLoader.getResource("rellDapp")!!.file)

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
