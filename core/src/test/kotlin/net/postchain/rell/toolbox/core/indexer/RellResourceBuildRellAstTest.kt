package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxErrorCollector
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

class RellResourceBuildRellAstTest {
    @Test
    fun `buildRellAst returns S_RellFile with no errors`() {
        val (rellCSrcPath, parseTree) = getSrcPathAndParseTree(rellFilesCorrect, "objects.rell")
        val rellResDesc = RellResourceFactory(workspaceError.toURI())

        val (ast, errors) = rellResDesc.buildRellAstWithCompilerErrors(rellCSrcPath, parseTree)
        assertThat(ast.definitions.size).isEqualTo(1)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `buildRellAst can build S_RellFile with no errors on syntax incorrect file`() {
        val (rellCSrcPath, parseTree) = getSrcPathAndParseTree(rellFilesErrors, "syntax_error.rell")
        val rellResDesc = RellResourceFactory(workspaceError.toURI())

        val (ast, errors) = rellResDesc.buildRellAstWithCompilerErrors(rellCSrcPath, parseTree)
        assertThat(ast.definitions.size).isEqualTo(3)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `buildRellAst can build S_RellFile with no errors from a semantic incorrect file`() {
        val (rellCSrcPath, parseTree) = getSrcPathAndParseTree(rellFilesErrors, "semantic_error.rell")
        val rellResDesc = RellResourceFactory(workspaceError.toURI())

        val (ast, errors) = rellResDesc.buildRellAstWithCompilerErrors(rellCSrcPath, parseTree)
        assertThat(ast.definitions.size).isEqualTo(4)
        assertThat(errors).isEmpty()
    }

    private fun getSrcPathAndParseTree(
        workSpace: MutableList<URI>,
        fileName: String
    ): Pair<C_SourcePath, RellParser.RuleX_RootParserContext> {
        val fileUri = workSpace.find { it.toString().endsWith("/$fileName") }!!
        val rellResDesc = RellResourceFactory(workspaceCorrect.toURI())
        val rellCSrcPath = rellResDesc.rellCompilerPaths.createCompilerSourcePath(fileUri)
        val errorListener = SyntaxErrorCollector()
        val parseTree = parser.parse(File(fileUri.path).readText(), errorListeners = listOf(errorListener))
        return Pair(rellCSrcPath, parseTree)
    }

    companion object {
        var rellFilesErrors: MutableList<URI> = mutableListOf()
        var rellFilesCorrect: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
        val workspaceError = File(classLoader.getResource("rellDappWithErrors").file)
        val workspaceCorrect = File(classLoader.getResource("rellDapp").file)
        val parser = AntlrRellParser()

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