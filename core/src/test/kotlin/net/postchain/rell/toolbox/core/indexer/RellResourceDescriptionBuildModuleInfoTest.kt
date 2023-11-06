package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI


class RellResourceDescriptionBuildModuleInfoTest {

    @Test
    fun `compiler finds errors in from imported file`() {
        val map: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val fileUri = rellFilesErrors.find { it.toString().endsWith("import.rell") }!!
        val fileContent = File(fileUri).readText()
        val rellCompilerPaths = RellCompilerPaths(workspaceError.toURI())
        val compilerSourcePath = rellCompilerPaths.createCompilerSourcePath(fileUri)

        val rellDesc = RellResourceFactory(workspaceError.toURI())
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(fileContent).first
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePath, parseTree).first

        map[compilerSourcePath] = C_TextSourceFile(compilerSourcePath, File(fileUri).readText())
        val fileCompilerSourceDir = C_SourceDir.mapDir(map)

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile,
            fileCompilerSourceDir
        )

        assertThat(rellCompileResult.messages.size).isEqualTo(2)
        assertThat(rellCompileResult.messages).extracting(C_Message::code)
            .containsAll("import:not_found:src.semantic_error", "unknown_name:c")
    }

    @Test
    fun `compiler finds single errors in single rell file`() {
        val map: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val fileUri = rellFilesErrors.find { it.toString().endsWith("semantic_error.rell") }!!
        val fileContent = File(fileUri).readText()
        val rellCompilerPaths = RellCompilerPaths(workspaceError.toURI())
        val compilerSourcePath = rellCompilerPaths.createCompilerSourcePath(fileUri)

        val rellDesc = RellResourceFactory(workspaceError.toURI())
        val parseTree = rellDesc.buildParseTreeWithSyntaxErrors(fileContent).first
        val sRellFile = rellDesc.buildRellAstWithCompilerErrors(compilerSourcePath, parseTree).first

        map[compilerSourcePath] = C_TextSourceFile(compilerSourcePath, File(fileUri).readText())
        val fileCompilerSourceDir = C_SourceDir.mapDir(map)

        val rellCompileResult = rellDesc.compileResult(
            compilerSourcePath,
            sRellFile,
            fileCompilerSourceDir
        )
        assertThat(rellCompileResult.messages.size).isEqualTo(3)
        assertThat(rellCompileResult.messages).extracting(C_Message::code).containsAll(
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(11:10)",
            "unknown_name:b",
            "name_conflict:user:a:FUNCTION:src/semantic_error.rell(7:10)"
        )
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