package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


@Suppress("JAVA_CLASS_ON_COMPANION")
class RellResourceBuildModuleInfoTest {

    private fun filterMessages(messageList: List<C_Message>, fileUri: URI): List<C_Message> {
        return messageList.filter {
            fileUri.path == workspaceError.toURI().path + it.pos.path().str()
        }
    }

    //TODO make it so a compiler can take in one file without ws defined
    @Test
    fun `compiler finds errors in from imported file`() {
        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser())
        val rellCompilerUtils = RellCompilerUtils()
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()

        val fileUriImport = rellFilesErrors.find { it.toString().endsWith("import.rell") }!!
        val fileUriSemanticError = rellFilesErrors.find { it.toString().endsWith("semantic_error.rell") }!!

        val fileContentImport = File(fileUriImport).readText()
        val fileContentSemanticError = File(fileUriSemanticError).readText()

        val parseTreeImport = rellDesc.buildParseTreeWithSyntaxErrors(fileContentImport).first
        val parseTreeSematicError = rellDesc.buildParseTreeWithSyntaxErrors(fileContentSemanticError).first

        val compilerSourcePathImport = rellCompilerUtils.createCompilerSourcePath(fileUriImport, workspaceError.toURI())
        val compilerSourcePathSemanticError = rellCompilerUtils.createCompilerSourcePath(fileUriSemanticError, workspaceError.toURI())

        val sRellFileImport = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePathImport, parseTreeImport).first
        val sRellFileSemanticError = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePathSemanticError, parseTreeSematicError).first

        val rellCompileResultSemanticError = rellDesc.compileResult(
            compilerSourcePathSemanticError,
            sRellFileSemanticError,
            fileMap
        )

        val errorMessagesSemanticError = filterMessages(rellCompileResultSemanticError!!.messages, fileUriSemanticError)
        assertThat(errorMessagesSemanticError.size).isEqualTo(3)

        val rellCompileResultImport = rellDesc.compileResult(
            compilerSourcePathImport,
            sRellFileImport,
            fileMap
        )

        val errorMessages = filterMessages(rellCompileResultImport!!.messages, fileUriImport)
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
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile,
            fileMap
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