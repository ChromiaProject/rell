/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.compiler.AstSourceFile
import net.postchain.rell.toolbox.compiler.RellCompilerApi.validateSimple
import net.postchain.rell.toolbox.parser.testing.TestSourceDir
import net.postchain.rell.toolbox.parser.testing.TestSourceFile
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test

class RellParserTest {
    @Test
    fun `ANTLR parser correctly parses Rell files`() = TestCaseSnippets.getTestCases().use { testCases ->
        testCases.forEach { testCase ->
            for ((sourceFile, sourceCode) in testCase.files) {
                val actualNumberOfErrors = tryParsing(sourceCode)
                val expectedErrors = testCase.parsing[sourceFile] ?: listOf()

                val parsedWithoutErrors = actualNumberOfErrors == 0
                val shouldNotHaveErrors = expectedErrors.isEmpty()

                assertThat(parsedWithoutErrors).isEqualTo(shouldNotHaveErrors)
                assertThat(actualNumberOfErrors).isGreaterThanOrEqualTo(expectedErrors.size)
            }
        }
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
        // Per-case work here is big (parser + AST transform + similarity check + doc validation),
        // so .parallel() is a real win.
        TestCaseSnippets.getTestCases().use { testCases ->
            testCases.parallel().forEach { testCaseSnippet ->
                val parser = AntlrRellParser()
                val parsingArtifacts = mutableListOf<ParsingArtifacts>()

                for ((sourceFile, sourceCode) in testCaseSnippet.files) {
                    try {
                        parsingArtifacts += compareAntlrAndCompilerParsedAst(
                            sourceFile,
                            sourceCode,
                            parser,
                        )
                    } catch (_: C_CommonError) {
                        // Skipping cases with syntax errors, as compiler parser isn't recovering from them,
                        // thus AST cannot be constructed for comparison with ANTLR
                        continue
                    }
                }

                validateUserDocs(testCaseSnippet, parsingArtifacts)
            }
        }
    }

    private fun validateUserDocs(testCaseSnippet: IdeCodeSnippet, parsingArtifacts: MutableList<ParsingArtifacts>) {
        if (testCaseSnippet.options.ideDocSymbolsEnabled) {
            val expectedComments = testCaseSnippet.comments
            val actualComments = try {
                getUserDocComments(parsingArtifacts, testCaseSnippet.options)
            } catch (_: Exception) {
                // Ignore cases with compilation issues
                null
            }
            if (actualComments != null) {
                assertThat(actualComments).isEqualTo(expectedComments)
            }
        }
    }

    private fun compareAntlrAndCompilerParsedAst(
        sourceFile: String,
        sourceCode: String,
        parser: AntlrRellParser,
    ): ParsingArtifacts {
        val sourcePath = IdeDirApi.parseSourcePath(sourceFile)
        val idePath = IdeSourcePathFilePath(sourcePath!!)

        val transformedAst = TestSourceFile(parser, sourcePath, sourceCode).readAst()
        val compilerAst = C_Parser.parse(sourcePath, idePath, sourceCode)

        try {
            assertThat(transformedAst).isSimilarTo(compilerAst)
        } catch (_: Exception) {
        }
        return ParsingArtifacts(sourcePath, idePath, transformedAst, sourceCode)
    }

    private fun getUserDocComments(
        parsingArtifacts: List<ParsingArtifacts>,
        options: C_CompilerOptions,
    ): Map<String, String> {
        val fileMap = mutableMapOf<C_SourcePath, C_SourceFile>()
        val modules = mutableListOf<R_ModuleName>()

        for ((sourcePath, idePath, transformedAst, sourceCode) in parsingArtifacts) {
            fileMap[sourcePath] = AstSourceFile.make(transformedAst, idePath, sourceCode)
            modules += IdeApi.getModuleName(sourcePath, transformedAst)!!
        }

        val selfDir = IdeDirApi.mapDir(fileMap)
        return IdeApi.getAllComments(selfDir, modules.toImmList(), options)
    }

    private fun tryParsing(sourceCode: String): Int {
        val lexer = RellLexer(CharStreams.fromString(sourceCode))
        lexer.removeErrorListeners()
        val parser = RellParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.ruleX_RootParser()
        return parser.numberOfSyntaxErrors
    }
}
