/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:OptIn(net.postchain.rell.base.sql.RawSqlAccess::class)

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.runtime.RawSqlBoundQuery
import net.postchain.rell.base.runtime.RawSqlBoundStatement
import net.postchain.rell.base.runtime.RawSqlStatement
import net.postchain.rell.base.testutils.BaseRellTest
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checking how BigDecimal values are handled in SQL.
 * Main issue: Java BigDecimal type allows multiple distinct (not equal to each other) representations of the same
 * number.
 */
class DecimalSqlTest: BaseRellTest(useSql = true) {
    @Test fun testPureSqlRead() {
        initPureSql()
        chkPureSqlRead(makeDec(123, -6), "(123000000,0)")
        chkPureSqlRead(makeDec(123000, -3), "(123000000,0)")
        chkPureSqlRead(makeDec(123000000, 0), "(123000000,0)")
        chkPureSqlRead(makeDec(123, 3), "(123,3)")
        chkPureSqlRead(makeDec(123000, 6), "(123000,6)")
        chkPureSqlRead(makeDec(123000000, 9), "(123000000,9)")
    }

    private fun chkPureSqlRead(value: BigDecimal, expected: String) {
        val table = "decimal_pure_sql_test"
        val sqlMgr = tstCtx.sqlMgr()

        sqlMgr.transaction { sqlExec ->
            sqlExec.execute(RawSqlStatement("DELETE FROM $table;"))
            sqlExec.execute(RawSqlBoundStatement("INSERT INTO $table(k, v) VALUES (?, ?);") { stmt ->
                stmt.setInt(1, 100)
                stmt.setBigDecimal(2, value)
            })
        }

        val list = mutableListOf<Pair<Int, BigDecimal>>()
        sqlMgr.access { sqlExec ->
            sqlExec.executeQuery(RawSqlBoundQuery("SELECT k, v FROM $table ORDER BY k;")) { rs ->
                list.add(rs.getInt(1) to rs.getBigDecimal(2)!!)
            }
        }

        assertEquals(1, list.size)
        assertEquals(100, list[0].first)
        assertEquals(expected, decToPartsStr(list[0].second))
    }

    @Test fun testPureSqlFilter() {
        initPureSql()

        chkPureSqlFilter(makeDec(123, -6), makeDec(123000, -3))
        chkPureSqlFilter(makeDec(123, -6), makeDec(123000000, 0))
        chkPureSqlFilter(makeDec(123000, -3), makeDec(123, -6))
        chkPureSqlFilter(makeDec(123000, -3), makeDec(123000000, 0))
        chkPureSqlFilter(makeDec(123000000, 0), makeDec(123, -6))
        chkPureSqlFilter(makeDec(123000000, 0), makeDec(123000, -3))

        chkPureSqlFilter(makeDec(123, 3), makeDec(123000, 6))
        chkPureSqlFilter(makeDec(123, 3), makeDec(123000000, 9))
        chkPureSqlFilter(makeDec(123000, 6), makeDec(123, 3))
        chkPureSqlFilter(makeDec(123000, 6), makeDec(123000000, 9))
        chkPureSqlFilter(makeDec(123000000, 9), makeDec(123, 3))
        chkPureSqlFilter(makeDec(123000000, 9), makeDec(123000, 6))
    }

    private fun chkPureSqlFilter(value1: BigDecimal, value2: BigDecimal) {
        check(value1 != value2)

        val table = "decimal_pure_sql_test"
        val sqlMgr = tstCtx.sqlMgr()

        sqlMgr.transaction { sqlExec ->
            sqlExec.execute(RawSqlStatement("DELETE FROM $table;"))
            sqlExec.execute(RawSqlBoundStatement("INSERT INTO $table(k, v) VALUES (?, ?);") { stmt ->
                stmt.setInt(1, 100)
                stmt.setBigDecimal(2, value1)
            })
        }

        val list = mutableListOf<Int>()
        sqlMgr.access { sqlExec ->
            sqlExec.executeQuery(
                RawSqlBoundQuery("SELECT k FROM $table WHERE v <> ?;") { s -> s.setBigDecimal(1, value2) },
            ) { rs -> list.add(rs.getInt(1)) }
        }
        assertEquals(listOf(), list)

        sqlMgr.access { sqlExec ->
            sqlExec.executeQuery(
                RawSqlBoundQuery("SELECT k FROM $table WHERE v = ?;") { s -> s.setBigDecimal(1, value2) },
            ) { rs -> list.add(rs.getInt(1)) }
        }
        assertEquals(listOf(100), list)
    }

    private fun initPureSql() {
        val table = "decimal_pure_sql_test"
        val sqlMgr = tstCtx.sqlMgr()
        sqlMgr.transaction { sqlExec ->
            sqlExec.execute(RawSqlStatement("CREATE TABLE $table(k INT NOT NULL PRIMARY KEY, v NUMERIC NOT NULL);"))
        }
    }

    private fun makeDec(unscaled: Long, scale: Int): BigDecimal = BigDecimal(BigInteger.valueOf(unscaled), scale)
    private fun decToPartsStr(v: BigDecimal) = "(${v.unscaledValue()},${v.scale()})"
}
