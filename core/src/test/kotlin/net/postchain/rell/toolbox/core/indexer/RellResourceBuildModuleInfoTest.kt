package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceBuildModuleInfoTest {

    private fun filterMessages(messageList: List<C_Message>, fileUri: URI): List<C_Message> {
        return messageList.filter {
            fileUri.path == workspaceError.toURI().path + it.pos.path().str()
        }
    }

    //TODO make it so a compiler can take in one file without ws defined
    @Test
    fun `compiler finds errors in from imported file`() {
        val fileUri = rellFilesErrors.find { it.toString().endsWith("import.rell") }!!
        val fileContent = File(fileUri).readText()
        val rellCompilerUtils = RellCompilerUtils()
        val compilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceError.toURI())

        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser())
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(fileContent).first
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePath, parseTree).first

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile,
        )

        val errorMessages = filterMessages(rellCompileResult!!.messages, fileUri)
        assertThat(errorMessages).isEmpty()
    }

    //TODO make it so a compiler can take in one file without ws defined
    @Test
    fun `compiler finds single errors in single rell file`() {
        val fileUri = rellFilesErrors.find { it.toString().endsWith("semantic_error.rell") }!!
        val fileContent = File(fileUri).readText()
        val rellCompilerUtils = RellCompilerUtils()
        val compilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceError.toURI())

        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser())
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(fileContent).first
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePath, parseTree).first

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile
        )

        val errorMessages = filterMessages(rellCompileResult!!.messages, fileUri)

        assertThat(errorMessages.size).isEqualTo(3)
        assertThat(errorMessages).extracting(C_Message::code).containsAll(
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(11:10)",
            "unknown_name:b",
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(7:10)"
        )
    }

    //TODO: Redo tests to be specific to the method of RellResourceFactory class

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