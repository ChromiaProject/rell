/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.associateToImmMap
import net.postchain.rell.base.utils.toImmList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

internal class RellApiRunTestsOnSqlTest: BaseRellApiRunTestsTest() {
    private val commit = "sys|-|COMMIT TRANSACTION|[]"

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

        chkSqls(map, "test_1", commit)
        chkSqls(map, "test_2", """user|0|SELECT A00."v" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""", commit)
        chkSqls(map, "test_3", """user|1|SELECT A00."x" FROM "c0.state" A00|[]""", commit)

        chkSqls(map, "test_4",
            """user|1|SELECT A00."x" FROM "c0.state" A00|[]""",
            """user|0|SELECT A00."rowid" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""",
            commit,
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
        val ins = """user|1|$ins0|[1,123]"""
        chkSqls(map, "test_1", ins, """user|1|SELECT A00."rowid" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""", commit)
        chkSqls(map, "test_2", ins, """user|1|UPDATE "c0.data" A00 SET "v" = ? WHERE A00."k" = ?|[456,1]""", commit)
        chkSqls(map, "test_3", ins, """user|1|DELETE FROM "c0.data" A00 WHERE A00."k" = ?|[1]""", commit)
    }

    @Test fun testRowCountSelect() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { key k: integer; }
                operation new_data(k: integer) { create data(k); }
            """,
            "test.rell" to """
                @test module;
                import app;
                function init() { rell.test.tx(range(5) @*{} (app.new_data($*100))).run(); }
                function test_1() { print(); init(); print(app.data @* { .k <= 0 }); }
                function test_2() { print(); init(); print(app.data @* { .k <= 100 }); }
                function test_3() { print(); init(); print(app.data @* { .k <= 300 }); }
                function test_4() { print(); init(); print(app.data @* { .k <= 400 }); }
                function test_5() { print(); init(); print(app.data @* { .k <= 700 }); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)

        val ins0 = """user|1|INSERT INTO "c0.data"("rowid", "k") VALUES ("c0.make_rowid"(), ?) RETURNING "rowid""""
        val ins = arrayOf("$ins0|[0]", "$ins0|[100]", "$ins0|[200]", "$ins0|[300]", "$ins0|[400]")
        val sel = """SELECT A00."rowid" FROM "c0.data" A00 WHERE A00."k" <= ? ORDER BY A00."rowid""""
        chkSqls(map, "test_1", *ins, """user|1|$sel|[0]""", commit)
        chkSqls(map, "test_2", *ins, """user|2|$sel|[100]""", commit)
        chkSqls(map, "test_3", *ins, """user|4|$sel|[300]""", commit)
        chkSqls(map, "test_4", *ins, """user|5|$sel|[400]""", commit)
        chkSqls(map, "test_5", *ins, """user|5|$sel|[700]""", commit)
    }

    @Test fun testRowCountInsert() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app.rell" to """
                module;
                entity data { key k: integer; }
                operation new_data(n: integer) {
                    create data(range(n) @*{} (struct<data>(${'$'}*100)));
                }
            """,
            "test.rell" to """
                @test module;
                import app;
                function test_1() { print(); app.new_data(0).run(); }
                function test_2() { print(); app.new_data(1).run(); }
                function test_3() { print(); app.new_data(2).run(); }
                function test_4() { print(); app.new_data(3).run(); }
            """,
        )

        val map = runTestsOnSqlExec(sourceDir)

        val (ins, vals) = arrayOf("""INSERT INTO "c0.data"("rowid", "k") VALUES""", """("c0.make_rowid"(), ?)""")
        chkSqls(map, "test_1", commit)
        chkSqls(map, "test_2", """user|1|$ins $vals RETURNING "rowid"|[0]""", commit)
        chkSqls(map, "test_3", """user|2|$ins $vals, $vals RETURNING "rowid"|[0,100]""", commit)
        chkSqls(map, "test_4", """user|3|$ins $vals, $vals, $vals RETURNING "rowid"|[0,100,200]""", commit)
    }

    @Test fun testRowCountUpdateDelete() {
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
        val ins = arrayOf("user|1|$ins0|[0,123]", "user|1|$ins0|[1,124]", "user|1|$ins0|[2,125]")
        chkSqls(map, "test_1", *ins, """user|3|UPDATE "c0.data" A00 SET "v" = ?|[456]""", commit)
        chkSqls(map, "test_2", *ins, """user|3|DELETE FROM "c0.data" A00|[]""", commit)
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
        chkSqls(map, "test", commit)
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
        chkSqls(map, "test", """sys|0|SELECT "rowid" FROM "c0.data" WHERE "rowid" IN (123)|[]""", commit)
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
            """user|0|SELECT A00."v" FROM "c0.data" A00 ORDER BY A00."rowid"|[]""",
            """user|0|SELECT A00."v" FROM "c0.data" A00 WHERE A00."v" > ? ORDER BY A00."rowid"|[0]""",
            commit,
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
        chkSqls(map, "test_1", "user|1|$ins|[1,123]", commit)
        chkSqls(map, "test_2", "user|1|$ins|[1,123]", "user|-|$ins|[1,456]|org.postgresql.util.PSQLException", commit)
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
            .associateToImmMap { it.name to TestInfo(it.name, it.initSqls.toImmList(), it.bodySqls.toImmList()) }

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
            e.rowCount?.toString() ?: "-",
            e.sql,
            e.parameters.joinToString(",", "[", "]"),
            when (e.error) {
                null -> null
                is Rt_Exception -> e.error.err.code()
                else -> e.error.javaClass.canonicalName
            },
        ).joinToString("|")
    }

    private class TestInfo(
        val name: String,
        val initSqls: List<String>,
        val bodySqls: List<String>,
    )
}
