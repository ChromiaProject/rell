/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RellApiRunTestsOnSqlTest: BaseRellApiRunTestsTest() {
    @Test fun testBasic() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                object state { x: integer = 123; }
                entity data { v: integer; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test_1() { print(); }
                function test_2() { print(); print(app.data @*{} ( .v )); }
                function test_3() { print(); print(app.state.x); }
                function test_4() { print(); print(app.state.x, app.data @* {}); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)

        chkSqls(map, "test_1")
        chkSqls(map, "test_2", """user|-|SELECT A00."v" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""")
        chkSqls(map, "test_3", """user|-|SELECT A00."x" FROM "c0.state" A00|[]""")

        chkSqls(map, "test_4",
            """user|-|SELECT A00."x" FROM "c0.state" A00|[]""",
            """user|-|SELECT A00."rowid" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""",
        )
    }

    @Test fun testOperations() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { key k: integer; mutable v: integer; }
                operation new_data(k: integer, v: integer) { create data(k, v); }
                operation upd_data(k: integer, v: integer) { update data @* {k} (v); }
                operation del_data(k: integer) { delete data @* {k}; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test_1() { print(); app.new_data(1, 123).run(); print(app.data @* {}); }
                function test_2() { print(); app.new_data(1, 123).run(); app.upd_data(1, 456).run(); }
                function test_3() { print(); app.new_data(1, 123).run(); app.del_data(1).run(); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)

        val ins0 = """INSERT INTO "c0.data"("rowid", "k", "v") VALUES ("c0.make_rowid"(), ?, ?) RETURNING "rowid""""
        val ins = """user|-|$ins0|[1,123]"""
        chkSqls(map, "test_1", ins, """user|-|SELECT A00."rowid" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""")
        chkSqls(map, "test_2", ins, """user|1|UPDATE "c0.data" A00 SET "v" = ? WHERE A00."k" = ?|[456,1]""")
        chkSqls(map, "test_3", ins, """user|1|DELETE FROM "c0.data" A00 WHERE A00."k" = ?|[1]""")
    }

    @Test fun testUpdateRowCount() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { key k: integer; mutable v: integer; }
                operation new_data(k: integer, v: integer) { create data(k, v); }
                operation upd_data(v: integer) { update data @* {} (v); }
                operation del_data() { delete data @*{}; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function init() { rell.test.tx(range(3) @*{} (app.new_data($,123+$))).run(); }
                function test_1() { print(); init(); app.upd_data(456).run(); }
                function test_2() { print(); init(); app.del_data().run(); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)

        val ins0 = """INSERT INTO "c0.data"("rowid", "k", "v") VALUES ("c0.make_rowid"(), ?, ?) RETURNING "rowid""""
        val ins = arrayOf("user|-|$ins0|[0,123]", "user|-|$ins0|[1,124]", "user|-|$ins0|[2,125]")
        chkSqls(map, "test_1", *ins, """user|3|UPDATE "c0.data" A00 SET "v" = ?|[456]""")
        chkSqls(map, "test_2", *ins, """user|3|DELETE FROM "c0.data" A00|[]""")
    }

    @Test fun testDataTableCreation() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                object state { x: integer = 123; }
                entity data { v: integer; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test() { print(); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)
        chkSqls(map, "test")
        chkInitSql(map.getValue("test:test"), """sys|-|create table "c0.data" (""")
        chkInitSql(map.getValue("test:test"), """sys|-|create table "c0.state" (""")
    }

    @Test fun testEntityFromGtv() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { v: integer; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test() { print(); assert_fails(app.data.from_gtv((123).to_gtv(), *)); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)
        chkSqls(map, "test", """sys|-|SELECT "rowid" FROM "c0.data" WHERE "rowid" IN (123)|[]""")
    }

    // Check that sqlLog doesn't interfere with onSql*.
    @Test fun testSqlLog() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { v: integer; }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test() { print(); print(app.data @*{} ( .v )); print(app.data @*{ .v > 0 } ( .v )); }
            """,
        )

        val config = runTestsDbConfig().toBuilder().sqlLog(true).build()
        val map = runTestsOnSqlExec(sourceDir, config)

        chkSqls(map, "test",
            """user|-|SELECT A00."v" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""",
            """user|-|SELECT A00."v" FROM "c0.data" A00 WHERE A00."v" > ? ORDER BY A00."rowid"|[0]""",
        )
    }

    @Test fun testSqlError() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { key k: integer; mutable v: integer; }
                operation new_data(k: integer, v: integer) { create data(k, v); }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test_1() { print(); app.new_data(1,123).run(); }
                function test_2() { print(); app.new_data(1,123).run(); app.new_data(1,456).run(); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir, expectedRes = listOf("test:test_1:OK", "test:test_2:FAILED"))

        val ins = """INSERT INTO "c0.data"("rowid", "k", "v") VALUES ("c0.make_rowid"(), ?, ?) RETURNING "rowid""""
        chkSqls(map, "test_1", "user|-|$ins|[1,123]")
        chkSqls(map, "test_2", "user|-|$ins|[1,123]", "user|-|$ins|[1,456]|rt_err:sqlerr:0")
    }

    private fun runTestsOnSqlExec(
        sourceDir: C_SourceDir,
        baseConfig: RellApiRunTests.Config = runTestsDbConfig(),
        expectedRes: List<String>? = null,
    ): Map<String, TestInfo> {
        class MutTestInfo(val name: String) {
            var initDone = false
            val initSqls = mutableListOf<String>()
            val bodySqls = mutableListOf<String>()
        }

        val tests = mutableListOf<MutTestInfo>()

        val runConfig = baseConfig.toBuilder()
            .onTestCaseStart {
                tests.add(MutTestInfo(it.name))
            }
            .outPrinter {
                if (it == "") tests.last().initDone = true
            }
            .onSqlExecutionFinished { e ->
                val test = tests.last()
                val list = if (test.initDone) test.bodySqls else test.initSqls
                val s = sqlEventToStr(e)
                list.add(s)
            }
            .build()

        val statusList = runTests(runConfig, sourceDir, listOf(), listOf("test"))
        if (expectedRes != null) {
            assertEquals(expectedRes, statusList)
        } else {
            for (v in statusList) {
                assertTrue(v.endsWith(":OK"), v)
            }
        }

        val map = tests
            .associate { it.name to TestInfo(it.name, it.initSqls.toImmList(), it.bodySqls.toImmList()) }
            .toImmMap()

        for (test in map.values) {
            chkInitSqls(test)
        }

        return map
    }

    private fun chkInitSqls(test: TestInfo) {
        // Checking some common init SQL statements to make sure they are intercepted.
        chkInitSql(test, """sys|-|CREATE TABLE "c0.rowid_gen"(""")
        chkInitSql(test, """sys|-|CREATE TABLE "c0.sys.classes"(""")
        chkInitSql(test, """sys|-|CREATE FUNCTION "rell_""", 5, 1000)
    }

    private fun chkInitSql(test: TestInfo, sqlPrefix: String, minCount: Int = 1, maxCount: Int = minCount) {
        val sqls = test.initSqls.filter { it.startsWith(sqlPrefix) }
        if (sqls.size in minCount .. maxCount) {
            return
        }

        val lines = mutableListOf("${test.name} sql [$sqlPrefix] min $minCount max $maxCount actual ${sqls.size}")
        for ((i, sql) in test.initSqls.withIndex()) {
            val trunc = sql.lines().take(2).joinToString("\n")
            lines.add("$i [$trunc]")
        }

        fail(lines.joinToString("\n"))
    }

    private fun chkSqls(tests: Map<String, TestInfo>, name: String, vararg expected: String) {
        val actList = tests.getValue("test:$name").bodySqls
        val expList = expected.toList()
        assertEquals(expList, actList, name)
    }

    private fun sqlEventToStr(e: SqlExecutionEvent): String {
        return listOfNotNull(
            if (e.isSystem) "sys" else "user",
            e.updateRowCount?.toString() ?: "-",
            e.sql,
            e.parameters.joinToString(",", "[", "]"),
            (e.error as? Rt_Exception)?.err?.code(),
        ).joinToString("|")
    }

    private class TestInfo(
        val name: String,
        val initSqls: List<String>,
        val bodySqls: List<String>,
    )
}
