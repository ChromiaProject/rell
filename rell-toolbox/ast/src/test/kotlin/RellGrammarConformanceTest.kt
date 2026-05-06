/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

class RellGrammarConformanceTest {

    private val parseHarness: ThreadLocal<GrammarParseHarness> =
        ThreadLocal.withInitial { GrammarParseHarness() }

    private class GrammarParseHarness {
        private val lexer = RellLexer(CharStreams.fromString(""))
        private val tokens = CommonTokenStream(lexer)
        private val parser = RellParser(tokens)

        init {
            lexer.removeErrorListeners()
            parser.removeErrorListeners()
        }

        fun parse(sourceCode: String): Int {
            lexer.inputStream = CharStreams.fromString(sourceCode)
            tokens.tokenSource = lexer
            parser.reset()
            parser.file()
            return parser.numberOfSyntaxErrors
        }
    }

    private val realWorldSamples: List<Pair<String, String>> by lazy {
        val root = locateRepoRoot()
        val paths = listOf(
            "deathmatch.rell"
                to "rell-toolbox/indexer/src/test/resources/realWorldExamples/deathmatch/rell/src/deathmatch/main.rell",
            "operations_queries_api.rell"
                to "rell-toolbox/indexer/src/test/resources/realWorldExamples/share-registry-backend-vinnova/rell/src/ecosec/operations_queries_api.rell",
            "performance-dapp.rell"
                to "performance/dapp/src/main.rell",
        )
        paths.map { (name, rel) ->
            val file = root.resolve(rel)
            check(Files.isRegularFile(file)) { "Missing real-world sample: $file" }
            name to Files.readString(file)
        }
    }

    private fun locateRepoRoot(): Path {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir
            }
            dir = dir.parent
        }
        error("Could not find repo root from " + System.getProperty("user.dir"))
    }

    @Test
    fun `Rell grammar parses real-world samples`() {
        val harness = parseHarness.get()
        for ((name, code) in realWorldSamples) {
            val errors = harness.parse(code)
            assertThat(errors, name).isEqualTo(0)
        }
    }

    @Test
    fun `Rell grammar agrees with internal parser on real-world samples`() {
        val harness = parseHarness.get()
        for ((name, code) in realWorldSamples) {
            val sourcePath = C_SourcePath.parse(name)
            val idePath = IdeSourcePathFilePath(sourcePath)
            val internalAccepts = try {
                C_Parser.parse(sourcePath, idePath, code)
                true
            } catch (_: Throwable) {
                false
            }
            val manualErrors = harness.parse(code)
            val manualAccepts = manualErrors == 0
            assertThat(manualAccepts, "Rell grammar vs C_Parser on $name").isEqualTo(internalAccepts)
        }
    }

    @Tag("grammar")
    @Test
    fun `Rell grammar parser correctly parses Rell files`() = TestCaseSnippets.getTestCases().use { testCases ->
        val falsePositives = ConcurrentLinkedQueue<String>()
        val falseNegatives = ConcurrentLinkedQueue<String>()
        var fp = 0L
        var fn = 0L
        var checked = 0L
        for (testCase in testCases) {
            for ((sourceFile, sourceCode) in testCase.files) {
                val actualNumberOfErrors = parseHarness.get().parse(sourceCode)
                val expectedErrors = testCase.parsing[sourceFile] ?: listOf()
                val parsedWithoutErrors = actualNumberOfErrors == 0
                val shouldNotHaveErrors = expectedErrors.isEmpty()
                checked++
                if (parsedWithoutErrors != shouldNotHaveErrors) {
                    if (parsedWithoutErrors) {
                        fp++
                        if (falsePositives.size < MAX_REPORTED) {
                            falsePositives += formatCase(sourceFile, sourceCode, expectedErrors.size, actualNumberOfErrors)
                        }
                    } else {
                        fn++
                        if (falseNegatives.size < MAX_REPORTED) {
                            falseNegatives += formatCase(sourceFile, sourceCode, expectedErrors.size, actualNumberOfErrors)
                        }
                    }
                }
            }
        }
        if (fp > 0 || fn > 0) {
            error(buildReport(checked, fp, fn, falsePositives, falseNegatives))
        }
    }

    @Tag("grammar")
    @Test
    fun `Rell grammar parser agrees with the internal Rell parser`() = TestCaseSnippets.getTestCases().use { testCases ->
        val mismatches = ConcurrentLinkedQueue<String>()
        var m = 0L
        var checked = 0L
        for (testCaseSnippet in testCases) {
            for ((sourceFile, sourceCode) in testCaseSnippet.files) {
                val internalAccepts = try {
                    val sourcePath = C_SourcePath.parse(sourceFile)
                    val idePath = IdeSourcePathFilePath(sourcePath)
                    C_Parser.parse(sourcePath, idePath, sourceCode)
                    true
                } catch (_: Throwable) {
                    false
                }
                if (!internalAccepts) continue
                checked++
                val errors = parseHarness.get().parse(sourceCode)
                if (errors != 0) {
                    m++
                    if (mismatches.size < MAX_REPORTED) {
                        mismatches += formatCase(sourceFile, sourceCode, 0, errors)
                    }
                }
            }
        }
        if (m > 0) {
            error(
                buildString {
                    append("Rell grammar disagreed with C_Parser on ").append(m)
                        .append('/').append(checked).append(" accepted snippets.\n")
                    append("First ").append(MAX_REPORTED)
                        .append(" mismatch(es) (C_Parser accepted, Rell grammar rejected):\n")
                    mismatches.forEachIndexed { i, c ->
                        append('[').append(i + 1).append("]\n").append(c).append('\n')
                    }
                }
            )
        }
    }

    private fun buildReport(
        checked: Long,
        fp: Long,
        fn: Long,
        falsePositives: ConcurrentLinkedQueue<String>,
        falseNegatives: ConcurrentLinkedQueue<String>,
    ): String = buildString {
        append("Rell grammar disagreed on ").append(checked).append(" snippets: ")
        append(fp).append(" false-positive(s), ").append(fn).append(" false-negative(s).\n")
        if (fp > 0) {
            append("First ").append(MAX_REPORTED).append(" false-positive(s) (manual accepted, expected rejected):\n")
            falsePositives.forEachIndexed { i, c ->
                append('[').append(i + 1).append("]\n").append(c).append('\n')
            }
        }
        if (fn > 0) {
            append("First ").append(MAX_REPORTED).append(" false-negative(s) (manual rejected, expected accepted):\n")
            falseNegatives.forEachIndexed { i, c ->
                append('[').append(i + 1).append("]\n").append(c).append('\n')
            }
        }
    }

    private fun formatCase(sourceFile: String, sourceCode: String, expectedErrors: Int, actualErrors: Int): String {
        val excerpt = sourceCode.lineSequence().take(40).joinToString("\n")
        return buildString {
            append("  file=").append(sourceFile).append('\n')
            append("  expected_errors=").append(expectedErrors)
            append("  manual_errors=").append(actualErrors).append('\n')
            append("  ----- source (first 40 lines) -----\n")
            append(excerpt)
            if (sourceCode.lineSequence().count() > 40) append("\n  ...[truncated]")
        }
    }

    private companion object {
        const val MAX_REPORTED = 8
    }
}
