package net.postchain.rell.toolbox.core.parser

import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcAPI.validateSimple
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test


class RellParserTest {

    @Test
    fun `ANTLR parser correctly parses Rell files`() {
        val testCases = TestCaseSnippets.getTestCases()
        testCases.forEach(::validateTestCase)
    }

    @Test
    fun `Rell code is validated by compiler`() {
        val code = "query q() = 123; query p(x: integer) { return 'Hello ' + x; }"
        val files = mapOf("main.rell" to code)

        val parser = AntlrRellParser()
        val srcDir: C_SourceDir = TestSourceDir.create(parser, files)

        val result = validateSimple(srcDir, "")
        assertThat(result).isEqualTo("OK")
    }

    @Test
    fun `ANTLR parsed AST is correctly transformed to Rell AST`() {
        val testCases = TestCaseSnippets.getTestCases()
        val parser = AntlrRellParser()

        testCases.forEach { testCase ->
            for (file in testCase.snippet.files) {
                try {
                    compareAntlrAndCompilerParsedAst(file.value, file.key, parser)
                } catch (error: C_CommonError) {
                    // We are skipping cases with syntax errors, as compiler parser isn't recovering from them,
                    // Thus AST cannot be constructed for later comparison with ANTLR AST
                    continue
                }
            }
        }
    }

    private fun compareAntlrAndCompilerParsedAst(
        sourceCode: String,
        sourceFile: String,
        parser: AntlrRellParser
    ) {
        val sourcePath = IdeDirApi.parseSourcePath(sourceFile)
        val idePath = IdeSourcePathFilePath(sourcePath!!)

        val transformedAst = TestSourceFile(parser, sourcePath, sourceCode).readAst()
        val compilerAst = C_Parser.parse(sourcePath, idePath, sourceCode)

        assertThat(transformedAst.definitions).hasSameSizeAs(compilerAst.definitions)

        for (i in transformedAst.definitions.indices) {
            val transformedDefinition = transformedAst.definitions[i]
            val compilerDefinition = compilerAst.definitions[i]
            assertThat(transformedDefinition).isSimilarTo(compilerDefinition)
        }
    }

    private fun validateTestCase(testCase: RellTestCaseSnippet) {
        testCase.snippet.files.forEach { file ->
            val actualNumberOfErrors = tryParsing(file.value)
            val expectedErrors = testCase.snippet.parsing[file.key] ?: listOf()

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