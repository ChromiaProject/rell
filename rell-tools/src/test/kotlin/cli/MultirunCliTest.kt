/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.rell.base.testutils.BaseResourcefulTest
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.RellVersions
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for `multirun.sh` (the Postchain multi-chain launcher).
 *
 * Tests fall into two categories:
 *
 * 1. **Server-based** — start a Postchain node via [MultirunServer], send HTTP queries,
 *    and verify responses. These require a running PostgreSQL instance.
 *
 * 2. **Subprocess-based** — run `multirun.sh --test` (or error cases) to completion
 *    and check stdout/stderr via [CliTestUtils.chkCommand] / [CliTestUtils.chkTests].
 *
 * Each test that touches the database gets an isolated PostgreSQL schema and
 * auto-assigned ports via [IsolatedMultirunConfig] so tests can run in parallel.
 * All test-scoped resources (configs, servers) are registered via
 * [BaseResourcefulTest.resource] so they're closed automatically after each test.
 */
class MultirunCliTest : BaseResourcefulTest() {
    companion object {
        private val STDOUT_IGNORE = listOf(
            "<LOG:INFO><RE>SQLDatabaseAccess - Upgrading to version [0-9]+",
            "<LOG:INFO><RE>SQLDatabaseAccess - Database version has been updated to version: [0-9]+",
            "<LOG:INFO><RE>FluentPropertyBeanIntrospector - Error when creating PropertyDescriptor .*",
        )

        private val CTE_STDOUT = listOf(
            "<LOG:INFO>PostchainApp - STARTING POSTCHAIN APP",
            "<LOG:INFO><RE>PostchainApp -     source directory: /.+",
            "<LOG:INFO><RE>PostchainApp -     run config file: /.+/.+[.]xml",
            "<LOG:INFO><RE>PostchainApp - ",
            "<LOG:INFO><RE>RellToolsUtils - rell: [0-9]+[.][0-9]+[.][0-9]+(-SNAPSHOT)?;.*",
        )

        private val NO_TESTS_STDOUT = listOf(
            "",
            "------------------------------------------------------------------------",
            "TEST RESULTS:",
            "",
            "<TEST>SUMMARY: 0 FAILED / 0 PASSED / 0 TOTAL",
            "",
            "",
            "***** OK *****",
        )

        private val mapper = ObjectMapper()

        /**
         * Find a free TCP port in the range 10000–49151. Postchain rejects ports ≥ 49152
         * (the ephemeral range that `ServerSocket(0)` would normally pick from).
         */
        private fun findFreePort(): Int {
            repeat(100) {
                val port = ThreadLocalRandom.current().nextInt(10000, 49152)
                try {
                    ServerSocket(port).use { /* available */ }
                    return port
                } catch (_: IOException) { /* in use, try another */ }
            }
            error("Could not find a free port in 10000–49151")
        }
    }

    /**
     * Creates a temporary copy of a multirun XML config with an isolated DB schema
     * and auto-assigned ports, so tests can run in parallel without conflicts.
     */
    private class IsolatedMultirunConfig(xmlName: String) : AutoCloseable {
        private val tempDir: Path = Files.createTempDirectory("multirun-cfg-")
        private val dbHandle: AutoCloseable
        val xmlPath: String

        init {
            val (handle, dbUrl) = SqlTestUtils.createTempDbUrl()
            dbHandle = handle

            val schema = dbUrl.substringAfter("currentSchema=").substringBefore("&")
            val origConfigDir = CliTestUtils.REPO_DIR / "work/testproj/config"

            Files.copy(origConfigDir / "private.properties", tempDir / "private.properties")

            val apiPort = findFreePort()
            val nodePort = findFreePort()
            // debug.port=-1 disables the Postchain Debug API (otherwise it defaults to 7750 and collides across parallel tests).
            val nodeConfig = (origConfigDir / "node-config.properties").readText()
                .replace("database.schema=test_app", "database.schema=$schema")
                .replace("api.port=7740", "api.port=$apiPort\ndebug.port=-1")
                .replace("node.0.port=9870", "node.0.port=$nodePort")
            (tempDir / "node-config.properties").writeText(nodeConfig)

            Files.copy(origConfigDir / xmlName, tempDir / xmlName)
            xmlPath = (tempDir / xmlName).toAbsolutePath().toString()
        }

        @OptIn(ExperimentalPathApi::class)
        override fun close() {
            try {
                dbHandle.close()
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    /** Isolated multirun config path, cleaned up when the test ends. */
    private fun isolatedConfigPath(xmlName: String): String = resource(IsolatedMultirunConfig(xmlName)).xmlPath

    /** Starts a [MultirunServer] against an isolated config; both are registered as test resources. */
    private fun multirunServer(xmlName: String): MultirunServer =
        resource(MultirunServer("multirun.sh -d work/testproj/src ${isolatedConfigPath(xmlName)}"))

    // ========================== Server-based tests ==========================

    @Test
    fun testRunSimple() {
        val app = multirunServer("run-simple.xml")
        app.waitTillUp()
        app.checkQuery("""{"type":"sum_digits_int","n":1000}""", status = 200, text = "\"73fb9a5de29b\"")
        app.checkQuery("""{"type":"sum_digits_dec","n":1000}""", status = 200, text = "\"73fb9a5de29b\"")
        app.checkQuery("""{"type":"get_module_args"}""", status = 200, text = """{"x":123456,"y":"Hello!"}""")
        app.checkQuery("""{"type":"get_common_args"}""", status = 200, text = """{"message":"Some common message..."}""")
    }

    @Test
    fun testRunSimpleGetRawConfig() {
        val app = multirunServer("run-simple.xml")
        app.waitTillUp()
        val r = app.sendPost("query/iid_1", """{"type":"get_raw_config"}""")
        assertEquals(200, r.statusCode)

        val json = mapper.readTree(r.body)
        assertEquals(
            "net.postchain.base.BaseBlockBuildingStrategy",
            json.at("/blockstrategy/name").asText(),
        )
        assertEquals(
            "net.postchain.gtx.GTXBlockchainConfigurationFactory",
            json["configurationfactory"].asText(),
        )

        val gtxModules = json.at("/gtx/modules")
        assertEquals("net.postchain.rell.module.RellPostchainModuleFactory", gtxModules[0].asText())
        assertEquals("net.postchain.gtx.StandardOpsGTXModule", gtxModules[1].asText())

        val rell = json.at("/gtx/rell")
        assertEquals(listOf("run_simple"), rell["modules"].map { it.asText() })

        val version = RellVersions.VERSION_STR.let { if ('-' in it) it.substringBefore('-') else it }
        assertEquals(version, rell["version"].asText())

        val sources = rell["sources"]
        assertTrue(sources.has("calc.rell"))
        assertTrue(sources.has("run_common.rell"))
        assertTrue(sources.has("run_simple.rell"))

        val moduleArgs = rell["moduleArgs"]
        assertEquals("Some common message...", moduleArgs.at("/run_common/message").asText())
        assertEquals(123456, moduleArgs.at("/run_simple/x").asInt())
        assertEquals("Hello!", moduleArgs.at("/run_simple/y").asText())

        val signers = json["signers"]
        assertEquals(
            "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57",
            signers[0].asText(),
        )
    }

    @Test
    fun testRunSimpleGetAppStructure() {
        val app = multirunServer("run-simple.xml")
        app.waitTillUp()
        val r = app.sendPost("query/iid_1", """{"type":"rell.get_app_structure"}""")
        assertEquals(200, r.statusCode)

        val modules = mapper.readTree(r.body)["modules"]

        val calc = modules["calc"]
        assertEquals("calc", calc["name"].asText())
        val calcFns = calc["functions"].fieldNames().asSequence().toSet()
        assertTrue("factorial" in calcFns)
        assertTrue("sum_digits_decimal" in calcFns)
        assertTrue("sum_digits_integer" in calcFns)

        val runCommon = modules["run_common"]
        assertEquals("run_common", runCommon["name"].asText())
        assertTrue(runCommon["queries"].has("get_common_args"))
        checkModuleArgsStruct(runCommon, listOf("message" to "text"))

        val runSimple = modules["run_simple"]
        assertEquals("run_simple", runSimple["name"].asText())
        assertTrue(runSimple["functions"].has("get_module_args_0"))
        for (q in listOf("get_args_chksum", "get_module_args", "get_raw_config", "sum_digits_dec", "sum_digits_int")) {
            assertTrue(runSimple["queries"].has(q), "Missing query: $q")
        }
        checkModuleArgsStruct(runSimple, listOf("x" to "integer", "y" to "text"))
    }

    private fun checkModuleArgsStruct(moduleNode: JsonNode, expectedAttrs: List<Pair<String, String>>) {
        val attrs = moduleNode.at("/structs/module_args/attributes")
        assertEquals(expectedAttrs.size, attrs.size())
        for (i in expectedAttrs.indices) {
            assertEquals(expectedAttrs[i].first, attrs[i]["name"].asText())
            assertEquals(expectedAttrs[i].second, attrs[i]["type"].asText())
        }
    }

    @Test
    fun testRunStackTraceMainQ() {
        val app = multirunServer("run-stack_trace.xml")
        app.waitTillUp()
        app.skipOutput()
        app.checkQuery(
            """{"type":"main_q","x":12345}""",
            status = 400,
            text = """{"error":"[stack_trace:calc(stack_trace/main.rell:7)] Query \u0027main_q\u0027 failed: x must be positive, but was 0"}""",
        )
        app.checkOutput(
            listOf(
                "<LOG:INFO>Rell - [stack_trace:main_q(stack_trace/main.rell:34)] main start",
                "<LOG:INFO>Rell - Query 'main_q' failed: x must be positive, but was 0",
                "\tat stack_trace:calc(stack_trace/main.rell:7)",
                "\tat stack_trace:calc(stack_trace/main.rell:11)",
                "\tat stack_trace:calc(stack_trace/main.rell:12)",
                "\tat stack_trace:calc(stack_trace/main.rell:13)",
                "\tat stack_trace:calc(stack_trace/main.rell:14)",
                "\tat stack_trace:calc(stack_trace/main.rell:15)",
                "\tat stack_trace:main_q(stack_trace/main.rell:35)",
            ),
            ignoreRest = true,
        )
    }

    @Test
    fun testRunStackTraceErrorQ() {
        val app = multirunServer("run-stack_trace.xml")
        app.waitTillUp()

        fun errorText(line: Int, msg: String) =
            """{"error":"[stack_trace:error(stack_trace/errors.rell:$line)] Query \u0027error_q\u0027 failed: $msg"}"""

        fun checkError(arg: String, line: Int, msg: String) {
            app.checkQuery("""{"type":"error_q","e":"$arg"}""", status = 400, text = errorText(line, msg))
        }

        checkError("/", 3, """Division by zero: 1 / 0""")
        checkError("%", 4, """Division by zero: 1 % 0""")
        checkError("-", 5, """Integer overflow: -(-9223372036854775808)""")
        checkError("abs", 6, """System function \u0027abs\u0027: Integer overflow: -9223372036854775808""")
        checkError("decimal", 7, """System function \u0027decimal\u0027: Invalid decimal value: \u0027hello\u0027""")
        checkError("integer.from_hex", 8, """System function \u0027integer.from_hex\u0027: Invalid hex number: \u0027hello\u0027""")
        checkError("list[]", 9, """List index out of bounds: 1000 (size 0)""")
        checkError("require", 11, """Requirement error""")
        checkError("text[]", 12, """Index out of bounds: 1000 (length 5)""")
        checkError("text.char_at", 13, """System function \u0027text.char_at\u0027: Index out of bounds: 1000 (length 5)""")

        app.checkQuery("""{"type":"error_q","e":"???"}""", status = 200, text = "0")
    }

    @Test
    fun testRunStackTraceEntities() {
        val app = multirunServer("run-stack_trace_entities.xml")
        app.waitTillUp()
        app.skipOutput()
        app.checkQuery(
            """{"type":"ent_main_q"}""",
            status = 400,
            text = """{"error":"[stack_trace.entities:ent_main(stack_trace/entities.rell:6)] Query \u0027ent_main_q\u0027 failed: No records found"}""",
        )
        app.checkOutput(
            listOf(
                "<LOG:INFO>Rell - Query 'ent_main_q' failed: No records found",
                "\tat stack_trace.entities:ent_main(stack_trace/entities.rell:6)",
                "\tat stack_trace.entities:ent_main_q(stack_trace/entities.rell:11)",
            ),
            ignoreRest = true,
        )
    }

    @Test
    fun testModargsOk() {
        val app = multirunServer("run-modargs-ok.xml")
        app.waitTillUp()
        app.checkQuery("""{"type":"foo_args"}""", status = 200, text = """{"x":123,"y":"Bob"}""")
        app.checkQuery("""{"type":"bar_args"}""", status = 200, text = """{"a":"Alice","b":456}""")
    }

    @Test
    fun testModargsExtra() {
        val app = multirunServer("run-modargs-extra.xml")
        app.waitTillUp()
        app.checkQuery("""{"type":"foo_args"}""", status = 200, text = """{"x":123,"y":"Bob"}""")
        app.checkQuery("""{"type":"bar_args"}""", status = 200, text = """{"a":"Alice","b":456}""")
    }

    // ========================== Test runner tests (--test flag) ==========================

    @Test
    fun testRunTests() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:run_tests.common_test:test_common",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_user",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_create_data",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_create_data",
                "<TEST>OK foo[1]:run_tests.foo_extra_test:test_foo_extra",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_user",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_nop",
                "<TEST>OK bar[2]:run_tests.bar_extra_test:test_bar_extra",
                "<TEST>OK bar[2]:run_tests.common_test:test_common",
                "",
                "<TEST>SUMMARY: 0 FAILED / 15 PASSED / 15 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    @Test
    fun testRunTestsFilterCommon() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-filter run_tests.common*:*",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:run_tests.common_test:test_common",
                "<TEST>OK bar[2]:run_tests.common_test:test_common",
                "",
                "<TEST>SUMMARY: 0 FAILED / 2 PASSED / 2 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    @Test
    fun testRunTestsFilterFoo() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-filter run_tests.foo*:*",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_user",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_create_data",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_create_data",
                "<TEST>OK foo[1]:run_tests.foo_extra_test:test_foo_extra",
                "",
                "<TEST>SUMMARY: 0 FAILED / 6 PASSED / 6 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    @Test
    fun testRunTestsFilterBar() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-filter run_tests.bar.bar_test",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_user",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_nop",
                "",
                "<TEST>SUMMARY: 0 FAILED / 6 PASSED / 6 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    private val allTestsExpected = listOf(
        "TEST RESULTS:",
        "",
        "<TEST>OK foo[1]:run_tests.common_test:test_common",
        "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_module_args",
        "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_module_args",
        "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_user",
        "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_create_data",
        "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_create_data",
        "<TEST>OK foo[1]:run_tests.foo_extra_test:test_foo_extra",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_module_args",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_module_args",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_user",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_create_data",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_create_data",
        "<TEST>OK bar[2]:run_tests.bar.bar_test:test_nop",
        "<TEST>OK bar[2]:run_tests.bar_extra_test:test_bar_extra",
        "<TEST>OK bar[2]:run_tests.common_test:test_common",
        "",
        "<TEST>SUMMARY: 0 FAILED / 15 PASSED / 15 TOTAL",
        "",
        "",
        "***** OK *****",
    )

    @Test
    fun testRunTestsChainFooBar() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-chain foo,bar",
            code = 0,
            expected = allTestsExpected,
        )    }

    @Test
    fun testRunTestsChainBarFoo() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-chain bar,foo",
            code = 0,
            expected = allTestsExpected,
        )    }

    @Test
    fun testRunTestsChainFoo() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-chain foo",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:run_tests.common_test:test_common",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_module_args",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_user",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_create_data",
                "<TEST>OK foo[1]:run_tests.foo_10.foo_10_test:test_common_create_data",
                "<TEST>OK foo[1]:run_tests.foo_extra_test:test_foo_extra",
                "",
                "<TEST>SUMMARY: 0 FAILED / 7 PASSED / 7 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    @Test
    fun testRunTestsChainBar() {
        val xmlPath = isolatedConfigPath("run-tests.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test --test-chain bar",
            code = 0,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_module_args",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_user",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_common_create_data",
                "<TEST>OK bar[2]:run_tests.bar.bar_test:test_nop",
                "<TEST>OK bar[2]:run_tests.bar_extra_test:test_bar_extra",
                "<TEST>OK bar[2]:run_tests.common_test:test_common",
                "",
                "<TEST>SUMMARY: 0 FAILED / 8 PASSED / 8 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
        )    }

    @Test
    fun testRunTestsGeneric() {
        val xmlPath = isolatedConfigPath("run-tests-generic.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            code = 1,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK A[1]:run_tests.generic.tests:test_get_app_name",
                "<TEST>OK A[1]:run_tests.generic.tests:test_add_user",
                "<TEST>OK B[2]:run_tests.generic.tests:test_get_app_name",
                "<TEST>OK B[2]:run_tests.generic.tests:test_add_user",
                "",
                "<TEST>FAILED A[1]:run_tests.generic.tests:test_fail",
                "<TEST>FAILED B[2]:run_tests.generic.tests:test_fail",
                "",
                "<TEST>SUMMARY: 2 FAILED / 4 PASSED / 6 TOTAL",
                "",
                "",
                "***** FAILED *****",
            ),
        )    }

    @Test
    fun testRunExtendTest() {
        val xmlPath = isolatedConfigPath("run-extend.xml")
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            stdout = listOf(
                "------------------------------------------------------------------------",
                "TEST foo[1]:extend.test:test",
                "[f]",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "------------------------------------------------------------------------",
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "<TEST>SUMMARY: 0 FAILED / 1 PASSED / 1 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
            stdoutIgnore = STDOUT_IGNORE,
        )    }

    @Test
    fun testRunExtendGTest() {
        val xmlPath = isolatedConfigPath("run-extend-g.xml")
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            stdout = listOf(
                "------------------------------------------------------------------------",
                "TEST foo[1]:extend.test:test",
                "[g, f]",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "------------------------------------------------------------------------",
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "<TEST>SUMMARY: 0 FAILED / 1 PASSED / 1 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
            stdoutIgnore = STDOUT_IGNORE,
        )    }

    @Test
    fun testRunExtendHTest() {
        val xmlPath = isolatedConfigPath("run-extend-h.xml")
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            stdout = listOf(
                "------------------------------------------------------------------------",
                "TEST foo[1]:extend.test:test",
                "[h, f]",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "------------------------------------------------------------------------",
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:extend.test:test",
                "",
                "<TEST>SUMMARY: 0 FAILED / 1 PASSED / 1 TOTAL",
                "",
                "",
                "***** OK *****",
            ),
            stdoutIgnore = STDOUT_IGNORE,
        )    }

    @Test
    fun testRunTestsSimple() {
        val xmlPath = isolatedConfigPath("run-tests-simple.xml")
        CliTestUtils.chkTests(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            code = 1,
            expected = listOf(
                "TEST RESULTS:",
                "",
                "<TEST>OK foo[1]:tests.calc_test:test_square",
                "<TEST>OK foo[1]:tests.calc_test:test_cube",
                "<TEST>OK foo[1]:tests.data_test:test_add_user",
                "<TEST>OK foo[1]:tests.data_test:test_remove_user",
                "<TEST>OK foo[1]:tests.event_test:test_event",
                "<TEST>OK foo[1]:tests.foobar:test_foo",
                "<TEST>OK foo[1]:tests.lib_test:test_lib",
                "",
                "<TEST>FAILED foo[1]:tests.foobar:test_fail_require",
                "<TEST>FAILED foo[1]:tests.foobar:test_fail_assert_equals",
                "<TEST>FAILED foo[1]:tests.op_mods_test:test__singular__twice_different_args__fails",
                "<TEST>FAILED foo[1]:tests.op_mods_test:test__compound__before_normal__succeeds",
                "<TEST>FAILED foo[1]:tests.op_mods_test:test__compound__twice_no_normal__fails",
                "<TEST>FAILED foo[1]:tests.op_mods_test:test__singular_compound__before_normal__succeeds",
                "",
                "<TEST>SUMMARY: 6 FAILED / 7 PASSED / 13 TOTAL",
                "",
                "",
                "***** FAILED *****",
            ),
        )    }

    // ========================== Module args tests (--test mode) ==========================

    @Test
    fun testModargsOkTest() {
        val xmlPath = isolatedConfigPath("run-modargs-ok.xml")
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            stdout = NO_TESTS_STDOUT,
            stdoutIgnore = STDOUT_IGNORE,
        )    }

    @Test
    fun testModargsExtraTest() {
        val xmlPath = isolatedConfigPath("run-modargs-extra.xml")
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src $xmlPath --test",
            stdout = NO_TESTS_STDOUT,
            stdoutIgnore = STDOUT_IGNORE,
        )    }

    // ========================== Module args error tests ==========================
    // These fail during config validation before touching DB/ports, so no isolation needed.

    @Test
    fun testModargsMissing() {
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src work/testproj/config/run-modargs-missing.xml",
            code = 2,
            stdout = CTE_STDOUT,
            stderr = "ERROR: Missing module_args for module(s): modargs.bar\n",
        )
    }

    @Test
    fun testModargsMissingTest() {
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src work/testproj/config/run-modargs-missing.xml --test",
            code = 2,
            stderr = "ERROR: Missing module_args for module(s): modargs.bar\n",
        )
    }

    @Test
    fun testModargsWrong() {
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src work/testproj/config/run-modargs-wrong.xml",
            code = 2,
            stdout = CTE_STDOUT,
            stderr = "ERROR: Bad module_args for module 'modargs.bar': Decoding type 'text': expected STRING, actual INTEGER (attribute: modargs.bar:module_args.a)\n",
        )
    }

    @Test
    fun testModargsWrongTest() {
        CliTestUtils.chkCommand(
            "multirun.sh -d work/testproj/src work/testproj/config/run-modargs-wrong.xml --test",
            code = 2,
            stderr = "ERROR: Bad module_args for module 'modargs.bar': Decoding type 'text': expected STRING, actual INTEGER (attribute: modargs.bar:module_args.a)\n",
        )
    }
}
