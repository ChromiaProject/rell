/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import net.postchain.rell.base.testutils.BaseResourcefulTest
import net.postchain.rell.base.testutils.SqlTestUtils
import org.junit.jupiter.api.Test

/**
 * Integration tests for the Rell REPL (interactive shell mode of `rell.sh`).
 *
 * Each test starts a REPL subprocess via [CliProcessRunner], sends commands
 * via stdin, and verifies stdout output against expected patterns.
 *
 * DB-dependent tests use [SqlTestUtils.createTempDbUrl] for isolation.
 * Process handles and DB handles are registered via [BaseResourcefulTest.resource]
 * so they're closed automatically after each test.
 */
class RellReplCliTest : BaseResourcefulTest() {
    companion object {
        private const val MSG_META_TABLE_DOES_NOT_EXIST =
            "<LOG:INFO>SQLDatabaseAccess - Meta table does not exist. Assume database does not exist and create it (version: 2)."

        private val STDOUT_IGNORE = listOf(
            "<LOG:INFO><RE>SQLDatabaseAccess - Upgrading to version [0-9]+",
            "<LOG:INFO><RE>SQLDatabaseAccess - Database version has been updated to version: [0-9]+",
            "<LOG:INFO><RE>FluentPropertyBeanIntrospector - Error when creating PropertyDescriptor .*",
        )
    }

    private fun checkIntro(app: CliProcessRunner) {
        app.checkOutput(
            "<RE>Rell \\d+\\.\\d+\\.\\d+(-SNAPSHOT)?",
            "Type '\\q' to quit or '\\?' for help.",
            ">>> ",
        )
    }

    private fun checkQuit(app: CliProcessRunner) {
        app.input("\\q\n")
        app.checkOutput(emptyList())
        app.waitForExit()
    }

    // ========================== Basic REPL tests ==========================

    @Test
    fun testNone() {
        val app = resource(CliProcessRunner("rell.sh -d work/testproj/src"))
        checkIntro(app)
        checkQuit(app)
    }

    @Test
    fun testSumDigitsInteger() {
        val app = resource(CliProcessRunner("rell.sh -d work/testproj/src"))
        checkIntro(app)
        app.input("import calc;\n")
        app.checkOutput(">>> ")
        app.input("calc.sum_digits_integer(1000)\n")
        app.checkOutput("73fb9a5de29b", ">>> ")
        checkQuit(app)
    }

    // ========================== Output format tests ==========================

    private fun initOutputFormat(app: CliProcessRunner) {
        checkIntro(app)
        app.input("val l = [(123,'Hello'),(456,'Bye'),(789,'Ciao')];\n")
        app.checkOutput(">>> ")
        app.input("val m = [123:'Hello',456:'Bye',789:'Ciao'];\n")
        app.checkOutput(">>> ")
        app.input("val m2 = ['Hello':123,'Bye':456,'Ciao':789];\n")
        app.checkOutput(">>> ")
    }

    @Test
    fun testOutputFormatOd() {
        val app = resource(CliProcessRunner("rell.sh"))
        initOutputFormat(app)
        app.input("\\od\n")
        app.checkOutput(">>> ")
        app.input("l\n")
        app.checkOutput("[(123,Hello), (456,Bye), (789,Ciao)]", ">>> ")
        app.input("m\n")
        app.checkOutput("{123=Hello, 456=Bye, 789=Ciao}", ">>> ")
        app.input("m2\n")
        app.checkOutput("{Hello=123, Bye=456, Ciao=789}", ">>> ")
        checkQuit(app)
    }

    @Test
    fun testOutputFormatOl() {
        val app = resource(CliProcessRunner("rell.sh"))
        initOutputFormat(app)
        app.input("\\ol\n")
        app.checkOutput(">>> ")
        app.input("l\n")
        app.checkOutput("(123,Hello)", "(456,Bye)", "(789,Ciao)", ">>> ")
        app.input("m\n")
        app.checkOutput("123=Hello", "456=Bye", "789=Ciao", ">>> ")
        app.input("m2\n")
        app.checkOutput("Hello=123", "Bye=456", "Ciao=789", ">>> ")
        checkQuit(app)
    }

    @Test
    fun testOutputFormatOg() {
        val app = resource(CliProcessRunner("rell.sh"))
        initOutputFormat(app)
        app.input("\\og\n")
        app.checkOutput(">>> ")

        app.input("123\n")
        app.checkOutput("123", ">>> ")
        app.input("123L\n")
        app.checkOutput("123L", ">>> ")
        app.input("123.0\n")
        app.checkOutput("\"123\"", ">>> ")
        app.input("true\n")
        app.checkOutput("1", ">>> ")
        app.input("x\"1234\"\n")
        app.checkOutput("x\"1234\"", ">>> ")
        app.input("\"Hello\"\n")
        app.checkOutput("\"Hello\"", ">>> ")

        app.input("l\n")
        app.checkOutput("[[123, \"Hello\"], [456, \"Bye\"], [789, \"Ciao\"]]", ">>> ")
        app.input("m\n")
        app.checkOutput("[[123, \"Hello\"], [456, \"Bye\"], [789, \"Ciao\"]]", ">>> ")
        app.input("m2\n")
        app.checkOutput("[\"Bye\": 456, \"Ciao\": 789, \"Hello\": 123]", ">>> ")
        checkQuit(app)
    }

    @Test
    fun testOutputFormatOj() {
        val app = resource(CliProcessRunner("rell.sh"))
        initOutputFormat(app)
        app.input("\\oj\n")
        app.checkOutput(">>> ")

        app.input("l\n")
        app.checkOutput(
            "[",
            "  [",
            "    123,",
            "    \"Hello\"",
            "  ],",
            "  [",
            "    456,",
            "    \"Bye\"",
            "  ],",
            "  [",
            "    789,",
            "    \"Ciao\"",
            "  ]",
            "]",
            ">>> ",
        )

        app.input("m\n")
        app.checkOutput(
            "[",
            "  [",
            "    123,",
            "    \"Hello\"",
            "  ],",
            "  [",
            "    456,",
            "    \"Bye\"",
            "  ],",
            "  [",
            "    789,",
            "    \"Ciao\"",
            "  ]",
            "]",
            ">>> ",
        )

        app.input("m2\n")
        app.checkOutput(
            "{",
            "  \"Bye\": 456,",
            "  \"Ciao\": 789,",
            "  \"Hello\": 123",
            "}",
            ">>> ",
        )

        checkQuit(app)
    }

    @Test
    fun testOutputFormatOx() {
        val app = resource(CliProcessRunner("rell.sh"))
        initOutputFormat(app)
        app.input("\\ox\n")
        app.checkOutput(">>> ")

        app.input("l\n")
        app.checkOutput(
            "<array>",
            "    <array>",
            "        <int>123</int>",
            "        <string>Hello</string>",
            "    </array>",
            "    <array>",
            "        <int>456</int>",
            "        <string>Bye</string>",
            "    </array>",
            "    <array>",
            "        <int>789</int>",
            "        <string>Ciao</string>",
            "    </array>",
            "</array>",
            ">>> ",
        )

        app.input("m\n")
        app.checkOutput(
            "<array>",
            "    <array>",
            "        <int>123</int>",
            "        <string>Hello</string>",
            "    </array>",
            "    <array>",
            "        <int>456</int>",
            "        <string>Bye</string>",
            "    </array>",
            "    <array>",
            "        <int>789</int>",
            "        <string>Ciao</string>",
            "    </array>",
            "</array>",
            ">>> ",
        )

        app.input("m2\n")
        app.checkOutput(
            "<dict>",
            "    <entry key=\"Bye\">",
            "        <int>456</int>",
            "    </entry>",
            "    <entry key=\"Ciao\">",
            "        <int>789</int>",
            "    </entry>",
            "    <entry key=\"Hello\">",
            "        <int>123</int>",
            "    </entry>",
            "</dict>",
            ">>> ",
        )

        checkQuit(app)
    }

    // ========================== DB-dependent REPL tests ==========================

    @Test
    fun testCompanyUserNoTables() {
        val (handle, dbUrl) = SqlTestUtils.createTempDbUrl()
        resource(handle)

        val app = resource(CliProcessRunner("rell.sh --resetdb --db-url $dbUrl -d work/testproj/src"))
        app.ignoreOutput(MSG_META_TABLE_DOES_NOT_EXIST)
        app.ignoreOutput("<LOG:INFO><RE>SqlInit - Step: .+")
        app.ignoreOutput("<LOG:INFO><RE>SqlInit - Database init plan: \\d+ step\\(s\\)")
        app.ignoreOutput(*STDOUT_IGNORE.toTypedArray())

        app.checkOutput(
            listOf("<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)"),
            ignoreRest = true,
        )

        checkIntro(app)

        app.input("import c: repl.company;\n")
        app.checkOutput(">>> ")

        app.input("\\od\n")
        app.checkOutput(">>> ")

        app.input("\\db-update\n")
        app.checkOutput("<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)", ">>> ")

        app.input("import u: repl.user;\n")
        app.checkOutput(">>> ")

        app.input("c.company @* {}\n")
        app.checkOutput("[]", ">>> ")

        app.input("u.user @* {}\n")
        app.checkOutput(
            "Run-time error: SQL Error: ERROR: relation \"c0.user\" does not exist",
            "  Position: 25",
            "<RE>  Location: File: parse_relation.c, Routine: parserOpenTable, Line: [0-9]+",
            "  Server SQLState: 42P01",
            ">>> ",
        )

        app.input("c.company @* {}\n")
        app.checkOutput("[]", ">>> ")

        app.input("\\db-update\n")
        app.checkOutput("<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)", ">>> ")

        app.input("u.user @* {}\n")
        app.checkOutput("[]", ">>> ")

        app.input("c.company @* {}\n")
        app.checkOutput("[]", ">>> ")

        checkQuit(app)
    }

    @Test
    fun testCompanyUserExistingTables() {
        val (handle, dbUrl) = SqlTestUtils.createTempDbUrl()
        resource(handle)

        // First session: create tables
        CliProcessRunner("rell.sh --resetdb --db-url $dbUrl -d work/testproj/src").use { app ->
            app.ignoreOutput(MSG_META_TABLE_DOES_NOT_EXIST)
            app.ignoreOutput("<LOG:INFO><RE>SqlInit - .+")
            app.ignoreOutput(*STDOUT_IGNORE.toTypedArray())
            checkIntro(app)
            app.input("import repl.company; import repl.user;\n")
            app.checkOutput(">>> ")
            app.input("\\db-update\n")
            app.checkOutput(">>> ")
            checkQuit(app)
        }

        // Second session: verify tables are detected
        val app = resource(CliProcessRunner("rell.sh --db-url $dbUrl -d work/testproj/src"))
        app.ignoreOutput(MSG_META_TABLE_DOES_NOT_EXIST)
        app.ignoreOutput(*STDOUT_IGNORE.toTypedArray())
        app.checkOutput(
            listOf(
                "<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)",
                "<LOG:WARN>SqlInit - Table for undefined entity 'company' found",
                "<LOG:WARN>SqlInit - Table for undefined entity 'user' found",
            ),
            ignoreRest = true,
        )
        checkIntro(app)

        app.input("\\od\n")
        app.checkOutput(">>> ")

        app.input("import c: repl.company;\n")
        app.checkOutput(">>> ")

        app.input("c.company @* {}\n")
        app.checkOutput("[]", ">>> ")

        app.input("import u: repl.user;\n")
        app.checkOutput(">>> ")

        app.input("u.user @* {}\n")
        app.checkOutput("[]", ">>> ")

        checkQuit(app)
    }
}
