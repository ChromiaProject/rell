/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The compatibility mode end to end: a project declaring an older `compile.rellVersion` must get
 * that release's diagnostics from the bundled compiler — a construct or library member added later
 * is reported as an error on the line that uses it.
 *
 * These assert the error *codes* the compiler emits (`version:...`), which is what the language
 * server publishes to the editor.
 */
class VersionDiagnosticsTest {
    private val rellLinter = mockk<AbstractRellLinter>()
    private val formattingStyleLinter = mockk<AbstractFormattingStyleLinter>()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @BeforeEach
    fun setup() {
        every { rellLinter.enhanceWithLintIssues(any(), any()) } returns Unit
        every { formattingStyleLinter.enhanceWithFormatterIssues(any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun `lambda is an error in a 0-15-4 project`(@TempDir dir: File) {
        chkCodes(
            dir,
            "0.15.4",
            "function f(): integer { val g = (x: integer) -> x * 2; return g(5); }",
            "version:feature:expr_lambda:0.16.1:0.15.4",
        )
    }

    @Test
    fun `value block as an if arm is an error in a 0-15-4 project`(@TempDir dir: File) {
        chkCodes(
            dir,
            "0.15.4",
            "function f(): integer = if (true) { val x = 1; x } else 2;",
            "version:feature:expr_value_block:0.16.1:0.15.4",
        )
    }

    @Test
    fun `jump expression is an error in a 0-15-4 project`(@TempDir dir: File) {
        chkCodes(
            dir,
            "0.15.4",
            "function f(): integer = if (true) return 1 else 2;",
            "version:feature:expr_jump:0.16.1:0.15.4",
        )
    }

    @Test
    fun `a library function newer than the declared version is an error`(@TempDir dir: File) {
        chkCodes(
            dir,
            "0.14.15",
            "function f() = 'a'.regex_replace('a', 'b');",
            "version:lib:FUNCTION:[rell:text.regex_replace]:0.14.16:0.14.15",
        )
    }

    @Test
    fun `the same code is clean once the declared version has the feature`(@TempDir dir: File) {
        chkCodes(dir, "0.16.1", LAMBDA)
    }

    /**
     * The regression that made every lambda an error in an ordinary project: with no
     * `compile.rellVersion` the compiler must run at the language server's own version, not at some
     * hardcoded older one.
     */
    @Test
    fun `a project declaring no version is analysed at the language server's own version`(@TempDir dir: File) {
        val indexer = indexerFor(dir, rellVersion = null, code = LAMBDA)
        assertThat(codesOf(indexer)).isEmpty()
    }

    @Test
    fun `a version the compiler cannot honour still yields diagnostics`(@TempDir dir: File) {
        // Not a version at all, older than the oldest compatibility mode, and newer than the
        // compiler: each is clamped into range, and none may abort compilation of the file.
        for (declared in listOf("latest", "0.16", "0.6.0", "99.0.0")) {
            val subDir = File(dir, "v-${declared.replace('.', '_')}").also { it.mkdirs() }
            val indexer = indexerFor(subDir, declared, BROKEN)
            assertThat(codesOf(indexer), "rellVersion: $declared")
                .containsExactly("unknown_name:no_such_function")
        }
    }

    private fun chkCodes(dir: File, rellVersion: String, code: String, vararg expected: String) {
        val indexer = indexerFor(dir, rellVersion, code)
        assertThat(codesOf(indexer)).containsExactly(*expected)
    }

    private fun codesOf(indexer: WorkspaceIndexer): List<String> =
        indexer.getAllIssues().values.flatten().map { it.code }

    private fun indexerFor(dir: File, rellVersion: String?, code: String): WorkspaceIndexer {
        testData(dir) {
            addMainFile("module;\n$code\n")
            if (rellVersion != null) {
                config { compile("compile:\n  rellVersion: $rellVersion") }
            }
        }
        val indexer = WorkspaceIndexer(
            dir.toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions,
            dir.toURI(),
        )
        indexer.initialFileIndexBuild()
        return indexer
    }

    private companion object {
        const val LAMBDA = "function f(): integer { val g = (x: integer) -> x * 2; return g(5); }"

        /** An error every version reports, so a clamped version proves it compiled rather than bailed. */
        const val BROKEN = "function f() = no_such_function();"

        init {
            // The gates under test are 'since' versions above the ones declared below; if a future
            // release moved them the expectations here would silently stop testing anything.
            check(RellVersions.VERSION >= RellVersions.parse("0.16.1"))
        }
    }
}
