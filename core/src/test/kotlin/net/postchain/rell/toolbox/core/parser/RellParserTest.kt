package net.postchain.rell.toolbox.core.parser

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcAPI.validateSimple
import net.postchain.rell.toolbox.core.indexer.AstSourceFile
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test


class RellParserTest {

    @Test
    fun `ANTLR parser correctly parses Rell files`() {
        val testCases = TestCaseSnippets.getTestCases()
        assertThat(testCases.size).isGreaterThan(0)
        testCases.forEach(::validateTestCase)
    }

    @Test
    fun `Rell code is validated by compiler`() {
        val code = "query q() = 123; query p(x: integer) { return 'Hello ' + x; }"
        val files = mapOf("syntax_error.rell" to code)

        val parser = AntlrRellParser()
        val srcDir: C_SourceDir = TestSourceDir.create(parser, files)

        val result = validateSimple(srcDir, "")
        assertThat(result).isEqualTo("OK")
    }

    @Test
    fun `Function name containing invalid character is reported as syntax error`() {
        val code = "function hello#() { }"

        val parser = AntlrRellParser()
        val syntaxErrorCollector = SyntaxErrorCollector()
        parser.parse(code, listOf(), listOf(syntaxErrorCollector))

        val expectedError = SyntaxError("extraneous input '#' expecting '('", 1, 14, "<unknown>")
        assertThat(syntaxErrorCollector.errors).containsExactly(expectedError)
    }

    @Test
    fun `ANTLR parsed AST is correctly transformed to Rell AST`() {
        val testCases = TestCaseSnippets.getTestCases()
        val parser = AntlrRellParser()
        testCases.forEach { testCaseSnippet ->
            val parsingArtifacts = mutableListOf<ParsingArtifacts>()

            for (file in testCaseSnippet.files) {
                try {
                    parsingArtifacts += compareAntlrAndCompilerParsedAst(
                        file.value,
                        file.key,
                        parser,
                    )
                } catch (error: C_CommonError) {
                    // We are skipping cases with syntax errors, as compiler parser isn't recovering from them,
                    // Thus AST cannot be constructed for later comparison with ANTLR AST
                    continue
                }
            }

            validateUserDocs(testCaseSnippet, parsingArtifacts)
        }
    }

    private fun validateUserDocs(testCaseSnippet: IdeCodeSnippet, parsingArtifacts: MutableList<ParsingArtifacts>) {
        if (testCaseSnippet.options.ideDocSymbolsEnabled) {
            val expectedComments = testCaseSnippet.comments
            val actualComments = try {
                getUserDocComments(parsingArtifacts, testCaseSnippet.options)
            } catch (e: Exception) {
                // Ignore cases with compilation issues
                null
            }
            if (actualComments != null) {
                assertThat(actualComments).isEqualTo(expectedComments)
            }
        }
    }

    private fun compareAntlrAndCompilerParsedAst(
        sourceCode: String,
        sourceFile: String,
        parser: AntlrRellParser
    ): ParsingArtifacts {
        val sourcePath = IdeDirApi.parseSourcePath(sourceFile)
        val idePath = IdeSourcePathFilePath(sourcePath!!)

        val transformedAst = TestSourceFile(parser, sourcePath, sourceCode).readAst()
        val compilerAst = C_Parser.parse(sourcePath, idePath, sourceCode)

        assertThat(transformedAst).isSimilarTo(compilerAst)

        return ParsingArtifacts(sourcePath, idePath, transformedAst)
    }

    private fun getUserDocComments(parsingArtifacts: List<ParsingArtifacts>, options: C_CompilerOptions): Map<String, String> {
        val fileMap = mutableMapOf<C_SourcePath, C_SourceFile>()
        val modules = mutableListOf<R_ModuleName>()
        parsingArtifacts.forEach { (sourcePath, idePath, transformedAst) ->
            fileMap[sourcePath] = AstSourceFile.make(transformedAst, idePath)
            modules.add(IdeApi.getModuleName(sourcePath, transformedAst)!!)
        }
        val selfDir = IdeDirApi.mapDir(fileMap)
        return IdeApi.getAllComments(selfDir, modules, options)
    }

    private fun validateTestCase(testCaseSnippet: IdeCodeSnippet) {
        testCaseSnippet.files.forEach { file ->
            val actualNumberOfErrors = tryParsing(file.value)
            val expectedErrors = testCaseSnippet.parsing[file.key] ?: listOf()

            val parsedWithoutErrors = actualNumberOfErrors == 0
            val shouldNotHaveErrors = expectedErrors.isEmpty()

            assertThat(parsedWithoutErrors).isEqualTo(shouldNotHaveErrors)
            assertThat(actualNumberOfErrors).isGreaterThanOrEqualTo(expectedErrors.size)
        }
    }

    private fun tryParsing(text: String): Int {
        val input = CharStreams.fromString(text)
        val lexer = RellLexer(input)
        lexer.removeErrorListeners()
        val tokens = CommonTokenStream(lexer)
        val parser = RellParser(tokens)
        parser.removeErrorListeners()
        parser.ruleX_RootParser()

        return parser.numberOfSyntaxErrors
    }
}

data class ParsingArtifacts(
    val sourcePath: C_SourcePath,
    val idePath: IdeSourcePathFilePath,
    val transformedAst: S_RellFile,
)
