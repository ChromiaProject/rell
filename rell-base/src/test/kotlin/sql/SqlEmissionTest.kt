/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Regression test pinning the exact SQL emitted by Rell at runtime.
 *
 * Each case compiles + runs a small Rell program, captures every SQL string sent to the database
 * via a [SqlInterceptor], and compares against an inline expected string. The point is to detect
 * any drift in the SQL emission layer (whether from refactors, jOOQ upgrades, or accidental
 * changes), since Rell runs in consensus contexts where SQL byte-stability matters.
 *
 * Captured per query: the SQL string and the ordered list of bind types (NOT bind values — values
 * depend on test data and are not what we're guarding here; the bind type catalogue is the
 * footprint we want stable).
 *
 * Each [SqlCase] carries its own `expected` string formatted as `sql=<sql>\nbinds=[<types>]` blocks
 * separated by blank lines. Mismatches fail with an IDE-friendly diff that names the offending
 * case. To accept an intentional change: edit the `expected` literal on the affected case.
 */
class SqlEmissionTest {
    @Test
    fun testSqlEmission() {
        for (case in CASES) {
            val captured = SqlCapture()
            RellTestContext(useSql = true).use { tstCtx ->
                tstCtx.customSqlInterceptor = captured
                val tst = RellCodeTester(tstCtx)
                try {
                    case.body(tst)
                } finally {
                    captured.suspendCapture()
                }
            }
            val produced = captured.format().trimEnd('\n')
            val expected = case.expected.trimEnd('\n')
            if (expected != produced) {
                assertEquals(expected, produced, "SQL mismatch for case '${case.name}'.")
            }
        }
    }

    private class SqlCase(val name: String, val expected: String, val body: (RellCodeTester) -> Unit)

    /** A single captured query: the SQL text and the ordered list of bind types. */
    private class CapturedQuery(val sql: String, val bindTypes: List<String>) {
        fun format(): String = buildString {
            append("sql=").append(sql).append('\n')
            append("binds=[").append(bindTypes.joinToString(",")).append("]\n")
        }
    }

    private class SqlCapture: SqlInterceptor {
        private val queries = mutableListOf<CapturedQuery>()
        @Volatile private var capturing = true

        fun suspendCapture() { capturing = false }

        fun format(): String = buildString {
            for (q in queries) append(q.format()).append('\n')
        }.trimEnd() + "\n"

        override fun invoke(
            sql: String?,
            attributes: SqlExecutor.Attributes,
            preparator: SqlPreparator?,
            code: (SqlPreparator?) -> Int?,
        ): Int? {
            // Capture only USER-category SQL — that's the program-driven read/write path we're
            // guarding. SYS queries (DDL bootstrap, sysapptables, type fns) run once at init and
            // would dominate the captured output with noise unrelated to the migration target.
            if (!capturing || sql == null || attributes.category != SqlExecutor.Category.USER) {
                return code(preparator)
            }
            val typeRecorder = TypeRecordingPreparator(preparator)
            val rowCount = code(typeRecorder.wrapped)
            queries.add(CapturedQuery(sql, typeRecorder.types()))
            return rowCount
        }
    }

    private class TypeRecordingPreparator(private val original: SqlPreparator?) {
        private val capturedTypes = mutableListOf<String?>()

        val wrapped: SqlPreparator? = if (original == null) null else SqlPreparator { params ->
            val recorder = RecordingParams(params)
            original.prepare(recorder)
            for (t in recorder.types) capturedTypes.add(t)
        }

        fun types(): List<String> = capturedTypes.map { it ?: "?" }

        private class RecordingParams(private val target: PreparedStatementParams): PreparedStatementParams {
            val types = mutableListOf<String?>()

            override fun setBoolean(parameterIndex: Int, x: Boolean) {
                set(parameterIndex, "boolean"); target.setBoolean(parameterIndex, x)
            }
            override fun setInt(parameterIndex: Int, x: Int) {
                set(parameterIndex, "int"); target.setInt(parameterIndex, x)
            }
            override fun setLong(parameterIndex: Int, x: Long) {
                set(parameterIndex, "long"); target.setLong(parameterIndex, x)
            }
            override fun setBigDecimal(parameterIndex: Int, x: BigDecimal?) {
                set(parameterIndex, "decimal"); target.setBigDecimal(parameterIndex, x)
            }
            override fun setString(parameterIndex: Int, x: String?) {
                set(parameterIndex, "text"); target.setString(parameterIndex, x)
            }
            override fun setBytes(parameterIndex: Int, x: ByteArray?) {
                set(parameterIndex, "bytes"); target.setBytes(parameterIndex, x)
            }
            override fun setObject(parameterIndex: Int, x: Any?) {
                set(parameterIndex, "object:" + (x?.javaClass?.simpleName ?: "null"))
                target.setObject(parameterIndex, x)
            }

            private fun set(idx: Int, type: String) {
                while (types.size < idx) types.add(null)
                types[idx - 1] = type
            }
        }
    }

    companion object {
        // ---- Corpus ----------------------------------------------------------------
        // Each case is small, deterministic, and covers a distinct SQL emission pattern.
        // Names are stable: changing a name renames the case. Add new cases at the end.

        private const val USER_DEF =
            "entity user { name; mutable firstName: text = 'F'; mutable lastName: text = 'L'; mutable score: integer = 0; }"
        private const val COMPANY_DEF = "entity company { name; }"
        private const val EMP_DEF = "entity emp { name; company; mutable salary: integer = 0; }"
        private const val DATA_DEF = "entity data { k: integer; v: text; }"
        private const val FLAG_DEF = "entity flag { name; active: boolean = true; }"
        private const val DEC_DEF = "entity acct { name; mutable balance: decimal = 0.0; }"

        private val CASES: List<SqlCase> = listOf(
            // ---- Reads --------------------------------------------------------------
            SqlCase(
                "at_one_simple",
                """
                    sql=SELECT A00."rowid" FROM "c0.user" A00 WHERE A00."name" = ?
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','B','C',10")
                tst.chk("user @ { .name == 'A' }", "user[1]")
            },
            SqlCase(
                "at_list",
                """
                    sql=SELECT A00."rowid" FROM "c0.user" A00 ORDER BY A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','f','l',1", "2,'B','f','l',2")
                tst.chk("user @* {}", "list<user>[user[1],user[2]]")
            },
            SqlCase(
                "at_path_join",
                """
                    sql=SELECT A00."rowid" FROM "c0.emp" A00 JOIN "c0.company" A01 ON A00."company" = A01."rowid" WHERE A01."name" = ? ORDER BY A00."rowid"
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(COMPANY_DEF)
                tst.def(EMP_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.insert("c0.emp", "name,company,salary", "1,'Bob',100,500")
                tst.chk("emp @* { .company.name == 'Acme' }", "list<emp>[emp[1]]")
            },
            SqlCase(
                "at_two_entity_join",
                """
                    sql=SELECT A00."rowid", A01."rowid" FROM "c0.emp" A00, "c0.company" A01 WHERE A00."company" = A01."rowid" ORDER BY A00."rowid", A01."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(COMPANY_DEF)
                tst.def(EMP_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.insert("c0.emp", "name,company,salary", "1,'Bob',100,500")
                tst.chk("(e: emp, c: company) @* { e.company == c }", "list<(e:emp,c:company)>[(emp[1],company[100])]")
            },
            SqlCase(
                "at_aggr_sum",
                """
                    sql=SELECT COALESCE(SUM(A00."k"),0) FROM "c0.data" A00
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,1,'c'")
                tst.chk("data @ {} ( @sum .k )", "int[4]")
            },
            SqlCase(
                "at_aggr_group",
                """
                    sql=SELECT A00."k", COALESCE(SUM(?),0) FROM "c0.data" A00 GROUP BY A00."k" ORDER BY A00."k"
                    binds=[long]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,1,'c'")
                tst.chk(
                    "data @* {} ( @group .k, @sum 1 )",
                    "list<(k:integer,integer)>[(int[1],int[2]),(int[2],int[1])]",
                )
            },
            SqlCase(
                "at_aggr_min_max",
                """
                    sql=SELECT MIN(A00."k"), MAX(A00."k") FROM "c0.data" A00
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,3,'c'")
                tst.chk("data @ {} ( @min .k, @max .k )", "(int[1],int[3])")
            },
            SqlCase(
                "at_sort_asc",
                """
                    sql=SELECT A00."k" FROM "c0.data" A00 ORDER BY A00."k", A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,3,'c'", "2,1,'a'", "3,2,'b'")
                tst.chk("data @* {} ( @sort .k )", "list<integer>[int[1],int[2],int[3]]")
            },
            SqlCase(
                "at_sort_desc",
                """
                    sql=SELECT A00."k" FROM "c0.data" A00 ORDER BY A00."k" DESC, A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,3,'c'", "2,1,'a'", "3,2,'b'")
                tst.chk("data @* {} ( @sort_desc .k )", "list<integer>[int[3],int[2],int[1]]")
            },
            SqlCase(
                "at_limit",
                """
                    sql=SELECT A00."k" FROM "c0.data" A00 ORDER BY A00."k", A00."rowid" LIMIT ?
                    binds=[long]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,3,'c'")
                tst.chk("data @* {} ( @sort .k ) limit 2", "list<integer>[int[1],int[2]]")
            },
            SqlCase(
                "at_offset",
                """
                    sql=SELECT A00."k" FROM "c0.data" A00 ORDER BY A00."k", A00."rowid" OFFSET ?
                    binds=[long]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,3,'c'")
                tst.chk("data @* {} ( @sort .k ) offset 1", "list<integer>[int[2],int[3]]")
            },
            SqlCase(
                "at_limit_offset",
                """
                    sql=SELECT A00."k" FROM "c0.data" A00 ORDER BY A00."k", A00."rowid" LIMIT ? OFFSET ?
                    binds=[long,long]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,3,'c'", "4,4,'d'")
                tst.chk("data @* {} ( @sort .k ) limit 2 offset 1", "list<integer>[int[2],int[3]]")
            },
            SqlCase(
                "at_where_when_if",
                """
                    sql=SELECT A00."rowid" FROM "c0.data" A00 WHERE CASE WHEN A00."k" = ? THEN A00."v" = ? ELSE A00."v" = ? END ORDER BY A00."rowid"
                    binds=[long,text,text]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'")
                tst.chk("data @* { if (.k == 1) .v == 'a' else .v == 'X' }", "list<data>[data[1]]")
            },
            SqlCase(
                "at_in_operator",
                """
                    sql=SELECT A00."rowid" FROM "c0.data" A00 WHERE A00."k" IN (?,?) ORDER BY A00."rowid"
                    binds=[long,long]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'", "3,3,'c'")
                tst.chk("data @* { .k in [1,3] }", "list<data>[data[1],data[3]]")
            },
            SqlCase(
                "at_exists",
                """
                    sql=SELECT A00."rowid" FROM "c0.company" A00 WHERE A00."name" = ? ORDER BY A00."rowid"
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.def(COMPANY_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.chk("exists(company @* { .name == 'Acme' })", "boolean[true]")
            },
            SqlCase(
                "at_empty",
                """
                    sql=SELECT A00."rowid" FROM "c0.user" A00 WHERE A00."name" = ? ORDER BY A00."rowid"
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.chk("empty(user @* { .name == 'Z' })", "boolean[true]")
            },
            SqlCase(
                "at_decimal_aggr",
                """
                    sql=SELECT ROUND(COALESCE(SUM(ROUND(A00."balance", 20)),0), 20) FROM "c0.acct" A00
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DEC_DEF)
                tst.insert("c0.acct", "name,balance", "1,'A',1.5", "2,'B',2.25")
                tst.chk("acct @ {} ( @sum .balance )", "dec[3.75]")
            },
            SqlCase(
                "at_boolean_filter",
                """
                    sql=SELECT A00."rowid" FROM "c0.flag" A00 WHERE A00."active" ORDER BY A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(FLAG_DEF)
                tst.insert("c0.flag", "name,active", "1,'A',TRUE", "2,'B',FALSE")
                tst.chk("flag @* { .active }", "list<flag>[flag[1]]")
            },

            // ---- Writes -------------------------------------------------------------
            SqlCase(
                "create_single_attr",
                """
                    sql=INSERT INTO "c0.user" ("rowid", "name", "firstName", "lastName", "score") VALUES ("c0.make_rowid"(), ?, ?, ?, ?) RETURNING "rowid"
                    binds=[text,text,text,long]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.chkOp("create user(name='Bob');")
            },
            SqlCase(
                "create_with_ref",
                """
                    sql=SELECT A00."rowid" FROM "c0.company" A00 WHERE A00."name" = ?
                    binds=[text]

                    sql=INSERT INTO "c0.emp" ("rowid", "name", "company", "salary") VALUES ("c0.make_rowid"(), ?, ?, ?) RETURNING "rowid"
                    binds=[text,long,long]
                """.trimIndent(),
            ) { tst ->
                tst.def(COMPANY_DEF)
                tst.def(EMP_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.chkOp("create emp(name='Bob', company=company@{.name=='Acme'}, salary=500);")
            },
            SqlCase(
                "update_single_attr",
                """
                    sql=UPDATE "c0.user" A00 SET "score" = ? WHERE A00."name" = ? RETURNING A00."rowid"
                    binds=[long,text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','F','L',1")
                tst.chkOp("update user @ { .name == 'A' } ( score = 99 );")
            },
            SqlCase(
                "update_multi_attr",
                """
                    sql=UPDATE "c0.user" A00 SET "firstName" = ?, "lastName" = ?, "score" = ? WHERE A00."name" = ? RETURNING A00."rowid"
                    binds=[text,text,long,text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','F','L',1")
                tst.chkOp("update user @ { .name == 'A' } ( firstName = 'X', lastName = 'Y', score = 7 );")
            },
            SqlCase(
                "update_with_join",
                """
                    sql=UPDATE "c0.emp" A00 SET "salary" = ? FROM "c0.company" A01 WHERE ((A00."company" = A01."rowid") AND A01."name" = ?) RETURNING A00."rowid"
                    binds=[long,text]
                """.trimIndent(),
            ) { tst ->
                tst.def(COMPANY_DEF)
                tst.def(EMP_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.insert("c0.emp", "name,company,salary", "1,'Bob',100,500")
                tst.chkOp("update emp @* { .company.name == 'Acme' } ( salary = 1000 );")
            },
            SqlCase(
                "delete_simple",
                """
                    sql=DELETE FROM "c0.user" A00 WHERE A00."name" = ? RETURNING A00."rowid"
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','F','L',1")
                tst.chkOp("delete user @ { .name == 'A' };")
            },
            SqlCase(
                "delete_with_join",
                """
                    sql=DELETE FROM "c0.emp" A00 USING "c0.company" A01 WHERE ((A00."company" = A01."rowid") AND A01."name" = ?) RETURNING A00."rowid"
                    binds=[text]
                """.trimIndent(),
            ) { tst ->
                tst.def(COMPANY_DEF)
                tst.def(EMP_DEF)
                tst.insert("c0.company", "name", "100,'Acme'")
                tst.insert("c0.emp", "name,company,salary", "1,'Bob',100,500")
                tst.chkOp("delete emp @* { .company.name == 'Acme' };")
            },
            SqlCase(
                "delete_all",
                """
                    sql=DELETE FROM "c0.data" A00 RETURNING A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(DATA_DEF)
                tst.insert("c0.data", "k,v", "1,1,'a'", "2,2,'b'")
                tst.chkOp("delete data @* {};")
            },

            // ---- Mixed --------------------------------------------------------------
            SqlCase(
                "create_then_read",
                """
                    sql=INSERT INTO "c0.user" ("rowid", "name", "firstName", "lastName", "score") VALUES ("c0.make_rowid"(), ?, ?, ?, ?) RETURNING "rowid"
                    binds=[text,text,text,long]

                    sql=INSERT INTO "c0.user" ("rowid", "name", "firstName", "lastName", "score") VALUES ("c0.make_rowid"(), ?, ?, ?, ?) RETURNING "rowid"
                    binds=[text,text,text,long]

                    sql=SELECT A00."name" FROM "c0.user" A00 ORDER BY A00."name", A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.chkOp("create user(name='Bob'); create user(name='Alice');")
                tst.chk("user @* {} ( @sort .name )", "list<text>[text[Alice],text[Bob]]")
            },
            SqlCase(
                "update_then_read",
                """
                    sql=UPDATE "c0.user" A00 SET "score" = A00."score" * ? RETURNING A00."rowid"
                    binds=[long]

                    sql=SELECT A00."name", A00."score" FROM "c0.user" A00 ORDER BY A00."name", A00."rowid"
                    binds=[]
                """.trimIndent(),
            ) { tst ->
                tst.def(USER_DEF)
                tst.insert("c0.user", "name,firstName,lastName,score", "1,'A','F','L',1", "2,'B','F','L',2")
                tst.chkOp("update user @* {} ( score = .score * 10 );")
                tst.chk(
                    "user @* {} ( @sort .name, .score )",
                    "list<(name:text,score:integer)>[(text[A],int[10]),(text[B],int[20])]",
                )
            },
        )
    }
}
