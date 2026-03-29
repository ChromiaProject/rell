/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.RellVersions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RellCliTest {
    companion object {
        /** Log lines to ignore in stdout when database initialization output is mixed in. */
        private val STDOUT_IGNORE = listOf(
            "<LOG:INFO><RE>SQLDatabaseAccess - Upgrading to version [0-9]+",
            "<LOG:INFO><RE>SQLDatabaseAccess - Database version has been updated to version: [0-9]+",
            "<LOG:INFO><RE>FluentPropertyBeanIntrospector - Error when creating PropertyDescriptor .*",
        )
    }

    /** Inserts `--db-url <url>` right after the script name, before any `--` separator. */
    private fun withDbUrl(command: String, dbUrl: String): String {
        val parts = command.split(" ", limit = 2)
        return "${parts[0]} --db-url $dbUrl ${parts[1]}"
    }

    /** Runs a DB-dependent CLI command with an isolated temporary schema. */
    private fun checkDbCommand(
        command: String,
        stdout: Any = "",
        stderr: Any = "",
        code: Int = 0,
        stdoutIgnore: List<String> = emptyList(),
        stderrIgnore: List<String> = emptyList(),
    ) {
        val (handle, dbUrl) = SqlTestUtils.createTempDbUrl()
        handle.use {
            CliTestUtils.chkCommand(
                withDbUrl(command, dbUrl),
                stdout = stdout,
                stderr = stderr,
                code = code,
                stdoutIgnore = stdoutIgnore,
                stderrIgnore = stderrIgnore,
            )
        }
    }

    /** Runs a DB-dependent test runner command with an isolated temporary schema. */
    private fun checkDbTests(command: String, code: Int, expected: List<String>) {
        val (handle, dbUrl) = SqlTestUtils.createTempDbUrl()
        handle.use {
            CliTestUtils.chkTests(withDbUrl(command, dbUrl), code = code, expected = expected)
        }
    }

    @Test
    fun testCalcSumDigitsInteger() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src calc sum_digits_integer 1000",
            stdout = "73fb9a5de29b\n",
        )
    }

    @Test
    fun testCalcSumDigitsDecimal() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src calc sum_digits_decimal 1000",
            stdout = "73fb9a5de29b\n",
        )
    }

    @Test
    fun testStairStairq() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src stair stairq 10",
            stdout = "0\n 1\n  2\n   3\n    4\n     5\n      6\n       7\n        8\n         9\n45\n",
        )
    }

    @Test
    fun testStairStairf() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src stair stairf 10",
            stdout = "0\n 1\n  2\n   3\n    4\n     5\n      6\n       7\n        8\n         9\n45\n",
        )
    }

    @Test
    fun testMiscNsFun() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src misc ns.f",
            stdout = listOf(
                "Inside function fun!",
                "<LOG:INFO>Rell - [misc:ns.f(misc.rell:6)] Some logging",
            ),
        )
    }

    @Test
    fun testModMain() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src mod main",
            stdout = "This is main!\nAnd this is helper!\nHello from support!\n",
        )
    }

    @Test
    fun testModComplexFooFoo() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src mod.complex.foo foo",
            stdout = listOf(
                "<LOG:INFO>Rell - [mod.complex.foo:foo(mod/complex/foo/foo.rell:4)] foo start",
                "<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:4)] bar start",
                "<LOG:INFO>Rell - [mod.complex.sub:helper(mod/complex/sub/helper.rell:2)] helper",
                "<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:6)] bar end",
                "<LOG:INFO>Rell - [mod.complex.foo:foo(mod/complex/foo/foo.rell:6)] foo end",
                "0",
            ),
        )
    }

    @Test
    fun testModComplexBarBar() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src mod.complex.bar bar",
            stdout = listOf(
                "<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:4)] bar start",
                "<LOG:INFO>Rell - [mod.complex.sub:helper(mod/complex/sub/helper.rell:2)] helper",
                "<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:6)] bar end",
            ),
        )
    }

    @Test
    fun testAbstrMainMain() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src abstr.main main",
            stdout = "f(123) = 15129\n",
        )
    }

    // Stack trace tests

    @Test
    fun testStackTraceMain12345() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src stack_trace main 12345",
            code = 1,
            stdout = listOf(
                "<LOG:INFO>Rell - [stack_trace:main(stack_trace/main.rell:24)] main start",
            ),
            stderr = listOf(
                "ERROR x must be positive, but was 0",
                "\tat stack_trace:calc(stack_trace/main.rell:7)",
                "\tat stack_trace:calc(stack_trace/main.rell:11)",
                "\tat stack_trace:calc(stack_trace/main.rell:12)",
                "\tat stack_trace:calc(stack_trace/main.rell:13)",
                "\tat stack_trace:calc(stack_trace/main.rell:14)",
                "\tat stack_trace:calc(stack_trace/main.rell:15)",
                "\tat stack_trace:main(stack_trace/main.rell:25)",
            ),
        )
    }

    @Test
    fun testStackTraceErrorDiv() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src -- stack_trace error /",
            code = 1,
            stderr = listOf(
                "ERROR Division by zero: 1 / 0",
                "\tat stack_trace:error(stack_trace/errors.rell:3)",
            ),
        )
    }

    @Test
    fun testStackTraceErrorMod() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src -- stack_trace error %",
            code = 1,
            stderr = listOf(
                "ERROR Division by zero: 1 % 0",
                "\tat stack_trace:error(stack_trace/errors.rell:4)",
            ),
        )
    }

    @Test
    fun testStackTraceErrorMinus() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src -- stack_trace error -",
            code = 1,
            stderr = listOf(
                "ERROR Integer overflow: -(-9223372036854775808)",
                "\tat stack_trace:error(stack_trace/errors.rell:5)",
            ),
        )
    }

    @Test
    fun testStackTraceErrorAbs() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src -- stack_trace error abs",
            code = 1,
            stderr = listOf(
                "ERROR System function 'abs': Integer overflow: -9223372036854775808",
                "\tat stack_trace:error(stack_trace/errors.rell:6)",
            ),
        )
    }

    // Version / build info tests

    @Test
    fun testCalcRellGetRellVersion() {
        val version = RellVersions.VERSION_STR.let { if ('-' in it) it.substringBefore('-') else it }
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src calc rell.get_rell_version",
            stdout = "$version\n",
        )
    }

    @Test
    fun testCalcRellGetBuild() {
        val result = CliTestUtils.runCommand("rell.sh -d work/testproj/src calc rell.get_build")
        assertEquals(0, result.exitCode)
        assertEquals("", result.stderr)

        val parts = result.stdout.split("; ")
        assertTrue(Regex("""rell: \d{1,3}\.\d{1,3}\.\d{1,3}(-SNAPSHOT)?""").matches(parts[0]))
        assertTrue(Regex("""postchain: \d{1,3}\.\d{1,3}\.\d{1,3}(-SNAPSHOT)?""").matches(parts[1]))
        assertTrue(Regex("""branch: .+""").matches(parts[2]))
        assertTrue(Regex("""commit: [0-9a-f]{5,} \(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+\d{4}\)""").matches(parts[3]))
        assertTrue(Regex("""dirty: (false|true)\n""").matches(parts[4]))
    }

    // Test runner tests (no DB required)

    @Test
    fun testTestsFoobar() {
        CliTestUtils.chkTests(
            "rell.sh -d work/testproj/src tests.foobar",
            code = 1,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.foobar:test_foo",
                "",
                "<TEST>FAILED tests.foobar:test_fail_require",
                "<TEST>FAILED tests.foobar:test_fail_assert_equals",
                "",
                "<TEST>SUMMARY: 2 FAILED / 1 PASSED / 3 TOTAL",
                "",
                "",
                "***** FAILED *****",
            ),
        )
    }

    // Error handling tests

    @Test
    fun testTestNotFound() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src --test notfound",
            code = 1,
            stderr = "ERROR: Module 'notfound' not found\n",
        )
    }

    @Test
    fun testTestNotFoundDotted() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src --test not.found",
            code = 1,
            stderr = "ERROR: Module 'not.found' not found\n",
        )
    }

    @Test
    fun testModuleNotFound() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src not_found",
            code = 1,
            stderr = "ERROR: Module 'not_found' not found\n",
        )
    }

    @Test
    fun testCompilationError() {
        CliTestUtils.chkCommand(
            "rell.sh -d work/testproj/src compilation_error",
            code = 1,
            stderr = "compilation_error.rell(2:10) ERROR: Return type mismatch: text instead of integer\nErrors: 1 Warnings: 0\n",
        )
    }

    // Version flag test (validates clikt option parsing)

    @Test
    fun testVersionFlag() {
        val result = CliTestUtils.runCommand("rell.sh -v")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.isNotEmpty(), "Expected version output")
    }

    // Database-dependent tests (each gets an isolated schema via createTempDbUrl)

    @Test
    fun testTestsDataTest() {
        checkDbTests(
            "rell.sh -d work/testproj/src tests.data_test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.data_test:test_add_user",
                "<TEST>OK tests.data_test:test_remove_user",
                "",
                "<TEST>SUMMARY: 0 FAILED / 2 PASSED / 2 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )
    }

    @Test
    fun testTestAll() {
        checkDbTests(
            "rell.sh -d work/testproj/src --test tests",
            code = 1,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.calc_test:test_square",
                "<TEST>OK tests.calc_test:test_cube",
                "<TEST>OK tests.data_test:test_add_user",
                "<TEST>OK tests.data_test:test_remove_user",
                "<TEST>OK tests.event_test:test_event",
                "<TEST>OK tests.foobar:test_foo",
                "<TEST>OK tests.lib_test:test_lib",
                "<TEST>OK tests.op_mods_test:test__singular__twice_different_args__fails",
                "<TEST>OK tests.op_mods_test:test__compound__before_normal__succeeds",
                "<TEST>OK tests.op_mods_test:test__compound__twice_no_normal__fails",
                "<TEST>OK tests.op_mods_test:test__singular_compound__before_normal__succeeds",
                "",
                "<TEST>FAILED tests.foobar:test_fail_require",
                "<TEST>FAILED tests.foobar:test_fail_assert_equals",
                "",
                "<TEST>SUMMARY: 2 FAILED / 11 PASSED / 13 TOTAL",
                "",
                "",
                "***** FAILED *****",
            ),
        )
    }

    @Test
    fun testTestCalcAndData() {
        checkDbTests(
            "rell.sh -d work/testproj/src --test tests.calc_test tests.data_test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.calc_test:test_square",
                "<TEST>OK tests.calc_test:test_cube",
                "<TEST>OK tests.data_test:test_add_user",
                "<TEST>OK tests.data_test:test_remove_user",
                "",
                "<TEST>SUMMARY: 0 FAILED / 4 PASSED / 4 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )
    }

    @Test
    fun testTestEventTest() {
        checkDbTests(
            "rell.sh -d work/testproj/src --test tests.event_test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.event_test:test_event",
                "",
                "<TEST>SUMMARY: 0 FAILED / 1 PASSED / 1 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )
    }

    @Test
    fun testTestOpModsTest() {
        checkDbTests(
            "rell.sh -d work/testproj/src --test tests.op_mods_test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK tests.op_mods_test:test__singular__twice_different_args__fails",
                "<TEST>OK tests.op_mods_test:test__compound__before_normal__succeeds",
                "<TEST>OK tests.op_mods_test:test__compound__twice_no_normal__fails",
                "<TEST>OK tests.op_mods_test:test__singular_compound__before_normal__succeeds",
                "",
                "<TEST>SUMMARY: 0 FAILED / 4 PASSED / 4 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )
    }

    @Test
    fun testStackTraceEntitiesEntMain() {
        checkDbCommand(
            "rell.sh --resetdb -d work/testproj/src -- stack_trace.entities ent_main",
            code = 1,
            stdoutIgnore = STDOUT_IGNORE,
            stderr = listOf(
                "ERROR No records found",
                "\tat stack_trace.entities:ent_main(stack_trace/entities.rell:6)",
            ),
        )
    }
}
