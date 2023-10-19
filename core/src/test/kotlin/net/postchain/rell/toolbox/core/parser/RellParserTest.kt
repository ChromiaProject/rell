package net.postchain.rell.toolbox.core.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Test
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class RellTestCaseSnippet(val files: Map<String, String>, val parsing: Map<String, List<Map<String, String>>>)

class RellParserTest {
    private val testDataPath = "parserTestSnippets"

    @Test
    fun `ANTLR parser correctly parses Rell files`() {
        val testCases = getTestCases()
        testCases.forEach(::validateTestCase)
    }

    private fun getTestCases(): List<RellTestCaseSnippet> {
        val mapper = jacksonObjectMapper()
        return getTestCaseFiles().flatMap {
            mapper.readValue<List<RellTestCaseSnippet>>(it)
        }
    }

    private fun getTestCaseFiles(): List<File> {
        System.err.println(RellParserTest::class.java.classLoader.getResource(".").file)
        val testCasesFolder = File(RellParserTest::class.java.classLoader.getResource(testDataPath).file)
        return testCasesFolder.walk().filter { it.isFile && it.extension == "json" }.toList()
    }

    private fun validateTestCase(case: RellTestCaseSnippet) {
        case.files.forEach { file ->
            val actualNumberOfErrors = tryParsing(file.value)
            val expectedErrors = case.parsing[file.key] ?: listOf()

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