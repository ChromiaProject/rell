/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class AtExprOrderByTest: BaseRellTest(useSql = true) {
    override fun entityDefs() = listOf(
            "entity company { name: text; }",
            "entity user { firstName: text; lastName: text; company; }"
    )

    override fun objInserts() = AtExprTest().objInserts()

    @Test fun testOrderBySqlCardinality() {
        chkSql()

        chkExprSql("company @ {}", "rt_err:at:wrong_count:5", """SELECT A00."rowid" FROM "c0.company" A00""")
        chkExprSql("company @ { .name == 'Apple' }", "company[200]",
            """SELECT A00."rowid" FROM "c0.company" A00 WHERE A00."name" = ?""")
        chkExprSql("company @ {} limit 1", "company[100]",
            """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid" LIMIT ?""")
        chkExprSql("company @ {} offset 4", "company[500]",
            """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid" OFFSET ?""")
        chkExprSql("company @? {}", "rt_err:at:wrong_count:5", """SELECT A00."rowid" FROM "c0.company" A00""")
        chkExprSql("company @? { .name == 'Apple' }", "company[200]",
            """SELECT A00."rowid" FROM "c0.company" A00 WHERE A00."name" = ?""")
        chkExprSql("company @? {} limit 1", "company[100]",
            """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid" LIMIT ?""")
        chkExprSql("company @? {} offset 4", "company[500]",
            """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid" OFFSET ?""")

        val all = "list<company>[company[100],company[200],company[300],company[400],company[500]]"
        chkExprSql("company @* {}", all, """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid"""")
        chkExprSql("company @+ {}", all, """SELECT A00."rowid" FROM "c0.company" A00 ORDER BY A00."rowid"""")
    }

    @Test fun testOrderBySqlDuplicationAttributes() {
        tst.strictToString = false

        val head = """SELECT A00."rowid" FROM "c0.user" A00"""
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY A00."firstName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName, @omit @sort .firstName) limit 3",
            "[user[20], user[21], user[50]]",
            """$head ORDER BY A00."firstName" DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort_desc .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY A00."firstName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName, @omit @sort_desc .firstName) limit 3",
            "[user[20], user[21], user[50]]",
            """$head ORDER BY A00."firstName" DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort .lastName, @omit @sort .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY A00."firstName", A00."lastName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            """
                user @*{}
                (user, @omit @sort .firstName, @omit @sort .lastName, @omit @sort .firstName, @omit @sort .lastName)
                limit 3
            """,
            "[user[40], user[30], user[51]]",
            """$head ORDER BY A00."firstName", A00."lastName", A00."rowid" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationParameters() {
        tst.strictToString = false

        val head = """SELECT A00."rowid" FROM "c0.user" A00"""
        val order = """rell_text_getchar(A00."firstName", (?)::INT)"""
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort .firstName[0]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName[0], @omit @sort .firstName[0]) limit 3",
            "[user[20], user[21], user[50]]",
            """$head ORDER BY $order DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort .firstName[1]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY $order, $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName[0], @omit @sort .firstName[1]) limit 3",
            "[user[50], user[20], user[21]]",
            """$head ORDER BY $order DESC, $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort_desc .firstName[1]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head ORDER BY $order, $order DESC, A00."rowid" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationOneTable() {
        tst.strictToString = false
        chkExprSql(
            "user @*{} (@sort user) limit 3",
            "[user[10], user[20], user[21]]",
            """SELECT A00."rowid" FROM "c0.user" A00 ORDER BY A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc user) limit 3",
            "[user[51], user[50], user[41]]",
            """SELECT A00."rowid" FROM "c0.user" A00 ORDER BY A00."rowid" DESC LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc user, @omit @sort .firstName) limit 3",
            "[user[51], user[50], user[41]]",
            """SELECT A00."rowid" FROM "c0.user" A00 ORDER BY A00."rowid" DESC, A00."firstName" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationManyTables() {
        tst.strictToString = false

        val head = """SELECT A00."rowid" FROM "c0.user" A00, "c0.company" A01"""
        chkExprSql(
            "(user, company) @*{} (@sort user) limit 3",
            "[user[10], user[10], user[10]]",
            """$head ORDER BY A00."rowid", A01."rowid" LIMIT ?""",
        )
        chkExprSql(
            "(user, company) @*{} (user, @omit @sort company) limit 3",
            "[user[10], user[20], user[21]]",
            """$head ORDER BY A01."rowid", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "(user, company) @*{} (user, @omit @sort_desc company, @omit @sort_desc user) limit 3",
            "[user[51], user[50], user[41]]",
            """$head ORDER BY A01."rowid" DESC, A00."rowid" DESC LIMIT ?""",
        )
        chkExprSql(
            """
                (user, company) @*{}
                (user, @omit @sort_desc company, @omit @sort .firstName, @omit @sort_desc user)
                limit 3
            """,
            "[user[40], user[30], user[51]]",
            """$head ORDER BY A01."rowid" DESC, A00."firstName", A00."rowid" DESC LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationGroup() {
        tst.strictToString = false

        val head = """SELECT A00."firstName" FROM "c0.user" A00 GROUP BY A00."firstName""""
        chkExprSql(
            "user @*{} (@group .firstName) limit 3",
            "[Bill, Jeff, Larry]",
            """$head ORDER BY A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort @group .firstName) limit 3",
            "[Bill, Jeff, Larry]",
            """$head ORDER BY A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc @group .firstName) limit 3",
            "[Steve, Sergey, Paul]",
            """$head ORDER BY A00."firstName" DESC LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@omit @sort @max .company, @sort @group .firstName) limit 3",
            "[Mark, Steve, Jeff]",
            """$head ORDER BY MAX(A00."company"), A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@omit @sort @max .company, @sort_desc @group .firstName) limit 3",
            "[Mark, Steve, Jeff]",
            """$head ORDER BY MAX(A00."company"), A00."firstName" DESC LIMIT ?""",
        )
    }

    private fun chkExprSql(expr: String, result: String, sql: String) {
        chk(expr, result)
        chkSql(sql)
    }
}
