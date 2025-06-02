/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.testutils.BaseContextTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.*
import kotlin.test.assertEquals

abstract class BaseSqlInitTest: BaseContextTest(useSql = true) {
    protected var compatibility: String? = null

    private var lastDefs = ""

    protected fun chkInit(code: String, expected: String = "OK", warn: String = "") {
        val tst = createTester()
        tst.chainId = 0
        createSysTables(tst)

        val globalCtx = tst.createInitGlobalCtx()

        var actualWarnings = ""

        val actual = RellTestUtils.processApp(code) { app ->
            RellTestUtils.catchRtErr {
                tstCtx.sqlMgr().transaction { sqlExec ->
                    val appCtx = tst.createExeCtx(globalCtx, sqlExec, app.rApp)
                    val initLogging = SqlInitLogging.ofLevel(SqlInitLogging.LOG_ALL)
                    val warnings = SqlInit.init(appCtx, NullSqlInitProjExt, initLogging)
                    actualWarnings = warnings.joinToString(",")
                    "OK"
                }
            }
        }

        assertEquals(expected, actual)
        assertEquals(warn, actualWarnings)

        lastDefs = code
    }

    private fun createSysTables(t: RellCodeTester) {
        tstCtx.sqlMgr().transaction { sqlExec ->
            SqlTestUtils.createSysAppTables(sqlExec)
            SqlTestUtils.createSysBlockchainTables(sqlExec, t.chainId)
        }
    }

    protected fun chkAll(metaEnts: String, metaAttrs: String, cols: String? = null, indexes: Boolean = false) {
        fun split(s: String) = s.split(" ").filter { it.isNotEmpty() }.toTypedArray()
        chkMetaEntities(*split(metaEnts))
        chkMetaAttrs(*split(metaAttrs))

        if (cols != null) {
            val actual = dumpTables(meta = false, columns = true, indexes = indexes)
            val expected = split(cols)
            assertEquals(expected.toList(), actual)
        }
    }

    private fun chkMetaEntities(vararg expected: String) {
        val sql = """SELECT C.id, C.name, C.type, C.log FROM "c0.sys.classes" C ORDER BY C.id;"""
        chkDataSql("c0.sys.classes", sql, *expected)
    }

    private fun chkMetaAttrs(vararg expected: String) {
        val sql = """SELECT A.class_id, A.name, A.type FROM "c0.sys.attributes" A ORDER BY A.class_id, A.name;"""
        chkDataSql("c0.sys.attributes", sql, *expected)
    }

    private fun chkDataSql(table: String, sql: String, vararg expected: String) {
        val actual = dumpDataSql(table, sql)
        assertEquals(expected.toList(), actual)
    }

    private fun dumpDataSql(table: String, sql: String): List<String> {
        val tables = dumpTablesStructure(false)
        if (table !in tables) return listOf("NO_TABLE")
        return tstCtx.sqlMgr().access { sqlExec ->
            SqlTestUtils.dumpSql(sqlExec, sql)
        }
    }

    protected fun chkColumns(vararg expected: String) {
        val actual = dumpTables(meta = false, columns = true, indexes = false)
        assertEquals(expected.toList(), actual)
    }

    protected fun chkTables(vararg expected: String) {
        val actual = dumpTables(meta = true, columns = false, indexes = false)
        assertEquals(expected.toList(), actual)
    }

    private val DUMP_SKIP_TABLES = immSetOf(
        "c0.rowid_gen",
        "c0.blocks",
        "c0.transactions",
        "c0.configurations",
        "c0.sys.faulty_configuration",
    )

    private fun dumpTables(meta: Boolean, columns: Boolean, indexes: Boolean): List<String> {
        val map = dumpTablesStructure(indexes)

        val res = mutableListOf<String>()
        for (table in map.keys) {
            if (table in DUMP_SKIP_TABLES) continue
            if (!meta && (table == "c0.sys.attributes" || table == "c0.sys.classes")) continue

            val tableStr = if (columns || indexes) {
                val tableDump = map.getValue(table)
                val members = tableDump.cols.map { (name, type) -> "$name:$type" } + tableDump.indexes
                val membersStr = members.joinToString(",")
                "$table($membersStr)"
            } else {
                table
            }

            res.add(tableStr)
        }

        return res
    }

    protected fun chkFunctions(expected: List<String>) {
        val actual = tstCtx.sqlMgr().access { sqlExec ->
            SqlUtils.getExistingFunctions(sqlExec).sorted()
        }
        assertEquals(expected, actual)
    }

    protected fun insert(table: String, columns: String, values: String) {
        val sql = SqlTestUtils.mkins(table, columns, values)
        execSql(sql)
    }

    protected fun chkData(vararg expected: String) {
        val actualMap = SqlTestUtils.dumpDatabaseTables(tstCtx.sqlMgr())
        val actual = actualMap.keys
                .filter { it != "c0.rowid_gen" && it != "c0.sys.classes" && it != "c0.sys.attributes" }
                .flatMap { table -> actualMap.getValue(table).map { "$table($it)" } }
        assertEquals(expected.toList(), actual)
    }

    @Suppress("SameParameterValue")
    protected fun chk(expr: String, expected: String) {
        val t = createChkTester()
        t.chk(expr, expected)
    }

    protected fun chkOp(code: String, expected: String) {
        val t = createChkTester()
        t.chkOp(code, expected)
    }

    private fun createTester(): RellCodeTester {
        val tst = RellCodeTester(tstCtx)
        tst.compatibilityVer(compatibility)
        return tst
    }

    private fun createChkTester(): RellCodeTester {
        val t = createTester()
        t.def(lastDefs)
        t.dropTables = false
        return t
    }

    protected fun execSql(sql: String) {
        tstCtx.sqlMgr().transaction { sqlExec ->
            sqlExec.execute(sql)
        }
    }

    private fun dumpTablesStructure(indexes: Boolean): Map<String, TableDump> {
        val sqlMgr = tstCtx.sqlMgr()
        val res = sqlMgr.access { sqlExec ->
            sqlExec.connection { con ->
                val map = SqlTestUtils.dumpTablesStructure(con)
                map.mapValues { (table, cols) ->
                    val idxList = if (!indexes) immListOf() else {
                        val idxs = SqlUtils.getTableIndexes(con, con.schema, table)
                        idxs.sortedWith(::compareIndexes).mapToImmList(::indexToStr)
                    }
                    TableDump(cols, idxList)
                }
            }
        }
        return res
    }

    private fun indexToStr(idx: SqlIndex): String {
        val type = if (idx.unique) "key" else "index"
        return "$type(${idx.cols.joinToString(",")})"
    }

    private fun compareIndexes(i1: SqlIndex, i2: SqlIndex): Int {
        fun isRowid(idx: SqlIndex) = idx.unique && idx.cols == listOf("rowid")
        var d = -isRowid(i1).compareTo(isRowid(i2))
        if (d == 0) d = CommonUtils.compareLists(i1.cols, i2.cols)
        if (d == 0) d = -i1.unique.compareTo(i2.unique)
        return d
    }

    private class TableDump(val cols: ImmMap<String, String>, val indexes: ImmList<String>)
}
