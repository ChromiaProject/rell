/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

// Detecting discrepancies between Java and Postgres locales.
//
// Checking every valid Java code point: 1,112,063 total code points, 136,104 letters.

package net.postchain.rell.base.testutils.tools

import net.postchain.rell.base.lib.type.Rt_ByteArrayValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.model.expr.ParameterizedSql
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.*

private class CharInfo(
    val codePoint: Int,
    c1: Char,
    c2: Char?,
) {
    val isLetter = Character.isLetter(codePoint)
    val plainString: String = String(listOfNotNull(c1, c2).toCharArray())
    val utf8: Bytes = plainString.toByteArray(Charsets.UTF_8).toBytes()
}

fun main() {
    val cs = getCharInfos()
    printStats(cs)

    val fcs = cs
    //val fcs = cs.filter { it.codePoint < 0x10000 }

    SqlTestUtils.createSqlConnection().use { con ->
        val sqlMgr = ConnectionSqlManager(SqlManagerConnection.create(con))
        sqlCheck(sqlMgr, fcs)
    }
}

private class SqlCheckerCtx(
    val sqlExec: SqlExecutor,
    val cs: List<CharInfo>,
) {
    val sorted: List<CharInfo> by lazy {
        cs.sortedBy { it.plainString }
    }

    private val messages = mutableListOf<String>()

    fun <T> select(sql: String, getter: (ResultSetRow) -> T): List<T> {
        val buf = mutableListOf<T>()
        sqlExec.executeQuery(sql, {}) { row ->
            buf.add(getter(row))
        }
        return buf.toList()
    }

    fun message(s: String) {
        if (messages.size < 10_000) {
            messages.add(s)
        }
    }

    fun messages() = messages.toList()
}

private abstract class SqlChecker {
    abstract fun check(ctx: SqlCheckerCtx): Int
}

private fun sqlCheck(sqlMgr: SqlManager, cs: List<CharInfo>) {
    sqlInit(sqlMgr, cs)

    val checks: Map<String, SqlChecker> = immMapOf(
        "to_bytes" to SqlChecker_ToBytes,
        "from_bytes" to SqlChecker_FromBytes,
        "sort_ranges" to SqlChecker_SortRanges,
//        "sort_asc" to SqlChecker_SortAsc,
//        "sort_desc" to SqlChecker_SortDesc,
        "upper_case" to SqlChecker_UpperCase,
        "lower_case" to SqlChecker_LowerCase,
    )

    sqlMgr.access { sqlExec ->
        for ((name, checker) in checks) {
            val ctx = SqlCheckerCtx(sqlExec, cs)
            val t0 = System.currentTimeMillis()
            val errs = checker.check(ctx)
            val dt = System.currentTimeMillis() - t0

            println("$name errs $errs time ${"%.3f".format(dt/1000.0)}")
            val msgs = ctx.messages()
            val msgs2 = msgs.take(10)
            for (msg in msgs2) {
                println("    $msg")
            }
            if (msgs2.size < msgs.size) {
                println("    ... <${msgs.size - msgs2.size} more> ...")
            }
        }
    }
}

private abstract class SelectSqlChecker<T>: SqlChecker() {
    abstract val sql: String

    abstract fun expectedList(ctx: SqlCheckerCtx): List<T>
    abstract fun rowToValue(row: ResultSetRow): T

    final override fun check(ctx: SqlCheckerCtx): Int {
        val actList = ctx.select(sql, ::rowToValue)
        val expList = expectedList(ctx)
        checkEquals(actList.size, expList.size)

        var res = 0

        for ((i, expValue) in expList.withIndex()) {
            val actValue = actList[i]
            if (actValue != expValue) {
                ctx.message("err ${javaClass.simpleName} $i $expValue $actValue")
                ++res
            }
        }

        return res
    }
}

private object SqlChecker_ToBytes: SelectSqlChecker<Bytes>() {
    override val sql = "SELECT CONVERT_TO(s, 'UTF8') FROM chars ORDER BY code;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.cs.map { it.utf8 }
    override fun rowToValue(row: ResultSetRow) = row.getBytes(1)!!.toBytes()
}

private object SqlChecker_FromBytes: SelectSqlChecker<String>() {
    override val sql = "SELECT CONVERT_FROM(b, 'UTF8') FROM chars ORDER BY code;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.cs.map { it.plainString }
    override fun rowToValue(row: ResultSetRow) = row.getString(1)!!
}

private object SqlChecker_SortRanges: SqlChecker() {
    override fun check(ctx: SqlCheckerCtx): Int {
        val codes = ctx.select("SELECT code FROM chars ORDER BY s;") { it.getInt(1) }
        val expected = calcRanges(ctx.sorted.map { it.codePoint })
        val actual = calcRanges(codes)
        ctx.message("expected: " + rangesToStr(actual))
        ctx.message("actual:   " + rangesToStr(expected))
        return if (actual == expected) 0 else 1
    }

    private fun rangesToStr(ranges: List<IntRange>): String {
        return "(${ranges.size}) " + ranges.joinToString(",") {
            if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}"
        }
    }

    private fun calcRanges(codes: List<Int>): List<IntRange> {
        var tail = codes
        val res = mutableListOf<IntRange>()
        while (tail.isNotEmpty()) {
            val x = tail.first()
            val n = tail.withIndex().takeWhile { it.value == x + it.index }.size
            for (i in 0 until n) {
                checkEquals(tail[i], tail[0] + i)
            }
            res.add(x .. tail[n - 1])
            tail = tail.subList(n, tail.size)
        }
        return res.toList()
    }
}

private object SqlChecker_SortAsc: SelectSqlChecker<Int>() {
    override val sql = "SELECT code FROM chars ORDER BY s;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.sorted.map { it.codePoint }
    override fun rowToValue(row: ResultSetRow) = row.getInt(1)
}

private object SqlChecker_SortDesc: SelectSqlChecker<Int>() {
    override val sql = "SELECT code FROM chars ORDER BY s DESC;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.sorted.asReversed().map { it.codePoint }
    override fun rowToValue(row: ResultSetRow) = row.getInt(1)
}

private object SqlChecker_UpperCase: SelectSqlChecker<String>() {
    override val sql = "SELECT UPPER(s) FROM chars ORDER BY code;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.cs.map { it.plainString.toUpperCaseEx() }
    override fun rowToValue(row: ResultSetRow) = row.getString(1)!!
}

private object SqlChecker_LowerCase: SelectSqlChecker<String>() {
    override val sql = "SELECT LOWER(s) FROM chars ORDER BY code;"
    override fun expectedList(ctx: SqlCheckerCtx) = ctx.cs.map { it.plainString.toLowerCaseEx() }
    override fun rowToValue(row: ResultSetRow) = row.getString(1)!!
}

private fun sqlInit(sqlMgr: SqlManager, cs: List<CharInfo>) {
    fun isTableExisting(sqlExec: SqlExecutor): Boolean {
        val sql = "SELECT * FROM information_schema.tables WHERE table_name = 'chars' AND table_schema = 'public';"
        var exists = false
        sqlExec.executeQuery(sql, {}) {
            exists = true
        }
        exists || return false

        var index = 0
        sqlExec.executeQuery("SELECT code, b, s FROM chars ORDER BY code;", {}) { row ->
            val ci = cs[index]
            exists = exists && row.getInt(1) == ci.codePoint
            exists = exists && row.getBytes(2)!!.toBytes() == ci.utf8
            exists = exists && row.getString(3) == ci.plainString
            ++index
        }

        return exists
    }

    fun createTable(sqlExec: SqlExecutor) {
        sqlExec.execute("DROP TABLE IF EXISTS chars;")
        sqlExec.execute("""
            CREATE TABLE chars(
                code INT NOT NULL PRIMARY KEY,
                b BYTEA NOT NULL,
                s TEXT NOT NULL
            );
        """
        )

        for (part in cs.partition(5_000)) {
            val sql = ParameterizedSql.generate { b ->
                b.append("INSERT INTO chars(code, b, s) VALUES ")
                b.append(part, ", ") { ci ->
                    b.append("(")
                    b.append(ci.codePoint.toLong())
                    b.append(", ")
                    b.append(Rt_ByteArrayValue.get(ci.utf8.toByteArray()))
                    b.append(", ")
                    b.append(Rt_TextValue.get(ci.plainString))
                    b.append(")")
                }
            }
            sql.execute(sqlExec)
        }
    }

    sqlMgr.transaction { sqlExec ->
        if (!isTableExisting(sqlExec)) {
            println("creating the table...")
            createTable(sqlExec)
        }
    }
}

private fun printStats(cs: List<CharInfo>) {
    val lets = cs.count { it.isLetter }
    println("${cs.size} $lets")

    val lens = (0 .. 4).map { 0 }.toMutableList()
    for (c in cs) {
        val s = c.plainString
        val b = c.utf8
        val s2 = String(b.toByteArray(), Charsets.UTF_8)
        check(s2 == s) { "[${strToCodes(s)}]" to "[${strToCodes(s2)}]" }
        lens[b.size()] += 1
    }

    println(lens)
}

private fun getCharInfos(): List<CharInfo> {
    val cs = mutableListOf<CharInfo>()

    for (c in (1).toChar() .. Character.MAX_VALUE) {
        if (!c.isSurrogate()) {
            cs.add(CharInfo(c.code, c, null))
        } else if (c.isHighSurrogate()) {
            for (c2 in Character.MIN_LOW_SURROGATE .. Character.MAX_LOW_SURROGATE) {
                val code = Character.toCodePoint(c, c2)
                cs.add(CharInfo(code, c, c2))
            }
        }
    }

    return cs.sortedBy { it.codePoint }.toImmList()
}

private fun strToCodes(s: String): String {
    return s.toCharArray().joinToString(",") { "0x%04x".format(it.code) }
}
