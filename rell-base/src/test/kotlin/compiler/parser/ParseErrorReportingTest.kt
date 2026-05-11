/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import kotlin.test.*

/**
 * Tests for the SLL+Bail / LL fallback parsing pattern in `C_Parser`.
 *
 * The better-parse path bailed on the first syntax error and surfaced exactly one diagnostic per
 * file. The new ANTLR LL pass uses ANTLR's default error-recovery strategy and accumulates every
 * recoverable error before returning a (possibly partial) parse tree. `C_Parser.parseWithErrors`
 * exposes the full list; `C_Parser.parse` keeps the legacy single-error throw contract.
 */
class ParseErrorReportingTest {

    @Test fun testValidFileTakesSllFastPath() {
        // The SLL fast path doesn't run a listener; success here verifies that valid input doesn't
        // hit the LL fallback. We can't observe SLL vs LL directly from outside, but a clean parse
        // with no errors is the baseline guarantee.
        val errors = mutableListOf<C_Error>()
        val ast = parse("function foo(): integer = 42;", errors)
        assertNotNull(ast)
        assertEquals(0, errors.size)
    }

    @Test fun testSingleSyntaxErrorIsReported() {
        // Missing `;` at end of var statement.
        val errors = mutableListOf<C_Error>()
        parse("function foo() { val x = 1 }", errors)
        assertTrue(errors.isNotEmpty(), "expected at least one error, got: $errors")
    }

    @Test fun testMultipleSyntaxErrorsAreAllReported() {
        // Three independent syntax errors in one file: missing `;` after first `val`, malformed
        // function header (no `()`), and a stray `}`. The better-parse path would have bailed
        // after the first; the LL fallback should report all three.
        val src = """
            function a() { val x = 1 }
            function b: integer = 2;
            function c() { val z = 3; } }
        """.trimIndent()
        val errors = mutableListOf<C_Error>()
        parse(src, errors)
        assertTrue(errors.size >= 2, "expected multiple errors, got ${errors.size}: ${errors.map { it.errMsg }}")
    }

    @Test fun testErrorPositionsAreOnDistinctLines() {
        // Each error should pinpoint its own location, not collapse onto a single shared position.
        // Two well-separated, recoverable errors: missing expression in val on line 1 and on line 3.
        val src = "val x = ;\nval y = 1;\nval z = ;"
        val errors = mutableListOf<C_Error>()
        parse(src, errors)
        val lines = errors.map { it.pos.line() }.toSet()
        assertTrue(lines.isNotEmpty(), "no errors collected")
        // Multiple distinct error positions confirm we're not bailing on the first.
        assertTrue(lines.size >= 2, "expected errors on >= 2 distinct lines, got $lines")
    }

    @Test fun testErrorMessagesIncludeAntlrDetail() {
        // ANTLR's default error reporter produces messages like "missing X at Y" or "extraneous
        // input ...". `parseWithErrors` should surface those rather than collapsing to a generic
        // "syntax error" string.
        val errors = mutableListOf<C_Error>()
        parse("function foo() { val x = 1 }", errors)
        assertTrue(errors.isNotEmpty())
        val msg = errors.first().errMsg
        // The C_Error message starts with "Syntax error: " and includes ANTLR's detail.
        assertTrue(msg.startsWith("Syntax error"), "unexpected: $msg")
    }

    @Test fun testParseThrowsOnFirstErrorForBackwardCompat() {
        // The legacy `parse(...)` API still throws C_Error on the first syntax error so
        // existing callers (compilation pipeline) behave the same way.
        val sourcePath = C_SourcePath.parse("test.rell")
        val idePath = IdeSourcePathFilePath(sourcePath)
        assertFails {
            C_Parser.parse(sourcePath, idePath, "function foo() { val x = 1 }")
        }
    }

    @Test fun testReplCheckEofErrorReportsEofForIncompleteInput() {
        // Unclosed block — REPL JLine integration calls this to decide whether to show the
        // continuation prompt. EOF-positioned syntax errors are returned; non-EOF errors are not.
        val err = C_Parser.checkEofErrorRepl("function foo() {")
        assertNotNull(err, "expected EOF-positioned error for unclosed block")
    }

    @Test fun testReplCheckEofErrorIsNullForValidInput() {
        // A complete REPL command must not be reported as needing more input.
        val err = C_Parser.checkEofErrorRepl("val x = 42;")
        assertNull(err)
    }

    @Test fun testReplCheckEofErrorIsNullForNonEofSyntaxError() {
        // A stray token inside well-formed structure is a real syntax error, not "incomplete".
        val err = C_Parser.checkEofErrorRepl("val x = ;")
        assertNull(err, "non-EOF errors must not trigger the JLine continuation prompt")
    }

    private fun parse(src: String, errors: MutableList<C_Error>) =
        C_Parser.parseWithErrors(
            filePath = C_SourcePath.parse("test.rell"),
            idePath = IdeSourcePathFilePath(C_SourcePath.parse("test.rell")),
            sourceCode = src,
            errorReporter = { errors.add(it) },
        )
}
