/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class AtExprOrderByTest: BaseRellTest(useSql = true) {
    override fun entityDefs() = listOf(
            "entity company { name: text; }",
            "entity user { firstName: text; lastName: text; company; }"
    )

    override fun objInserts() = AtExprTest().objInserts()

    @Test fun testOrderBySqlCardinality() {
        chkSql()

        chkExprSql("company @ {}", "rt_err:at:wrong_count:5", """select A00."rowid" from "c0.company" A00""")
        chkExprSql("company @ { .name == 'Apple' }", "company[200]",
            """select A00."rowid" from "c0.company" A00 where A00."name" = ?""")
        chkExprSql("company @ {} limit 1", "company[100]",
            """select A00."rowid" from "c0.company" A00 order by A00."rowid" LIMIT ?""")
        chkExprSql("company @ {} offset 4", "company[500]",
            """select A00."rowid" from "c0.company" A00 order by A00."rowid" OFFSET ?""")
        chkExprSql("company @? {}", "rt_err:at:wrong_count:5", """select A00."rowid" from "c0.company" A00""")
        chkExprSql("company @? { .name == 'Apple' }", "company[200]",
            """select A00."rowid" from "c0.company" A00 where A00."name" = ?""")
        chkExprSql("company @? {} limit 1", "company[100]",
            """select A00."rowid" from "c0.company" A00 order by A00."rowid" LIMIT ?""")
        chkExprSql("company @? {} offset 4", "company[500]",
            """select A00."rowid" from "c0.company" A00 order by A00."rowid" OFFSET ?""")

        val all = "list<company>[company[100],company[200],company[300],company[400],company[500]]"
        chkExprSql("company @* {}", all, """select A00."rowid" from "c0.company" A00 order by A00."rowid"""")
        chkExprSql("company @+ {}", all, """select A00."rowid" from "c0.company" A00 order by A00."rowid"""")
    }

    @Test fun testOrderBySqlDuplicationAttributes() {
        tst.strictToString = false

        val head = """select A00."rowid" from "c0.user" A00"""
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by A00."firstName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName, @omit @sort .firstName) limit 3",
            "[user[20], user[21], user[50]]",
            """$head order by A00."firstName" DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort_desc .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by A00."firstName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName, @omit @sort_desc .firstName) limit 3",
            "[user[20], user[21], user[50]]",
            """$head order by A00."firstName" DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName, @omit @sort .lastName, @omit @sort .firstName) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by A00."firstName", A00."lastName", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            """
                user @*{}
                (user, @omit @sort .firstName, @omit @sort .lastName, @omit @sort .firstName, @omit @sort .lastName)
                limit 3
            """,
            "[user[40], user[30], user[51]]",
            """$head order by A00."firstName", A00."lastName", A00."rowid" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationParameters() {
        tst.strictToString = false

        val head = """select A00."rowid" from "c0.user" A00"""
        val order = """rell_text_getchar(A00."firstName", (?)::INT)"""
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort .firstName[0]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName[0], @omit @sort .firstName[0]) limit 3",
            "[user[20], user[21], user[50]]",
            """$head order by $order DESC, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort .firstName[1]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by $order, $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort_desc .firstName[0], @omit @sort .firstName[1]) limit 3",
            "[user[50], user[20], user[21]]",
            """$head order by $order DESC, $order, A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (user, @omit @sort .firstName[0], @omit @sort_desc .firstName[1]) limit 3",
            "[user[40], user[30], user[51]]",
            """$head order by $order, $order DESC, A00."rowid" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationOneTable() {
        tst.strictToString = false
        chkExprSql(
            "user @*{} (@sort user) limit 3",
            "[user[10], user[20], user[21]]",
            """select A00."rowid" from "c0.user" A00 order by A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc user) limit 3",
            "[user[51], user[50], user[41]]",
            """select A00."rowid" from "c0.user" A00 order by A00."rowid" DESC LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc user, @omit @sort .firstName) limit 3",
            "[user[51], user[50], user[41]]",
            """select A00."rowid" from "c0.user" A00 order by A00."rowid" DESC, A00."firstName" LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationManyTables() {
        tst.strictToString = false

        val head = """select A00."rowid" from "c0.user" A00, "c0.company" A01"""
        chkExprSql(
            "(user, company) @*{} (@sort user) limit 3",
            "[user[10], user[10], user[10]]",
            """$head order by A00."rowid", A01."rowid" LIMIT ?""",
        )
        chkExprSql(
            "(user, company) @*{} (user, @omit @sort company) limit 3",
            "[user[10], user[20], user[21]]",
            """$head order by A01."rowid", A00."rowid" LIMIT ?""",
        )
        chkExprSql(
            "(user, company) @*{} (user, @omit @sort_desc company, @omit @sort_desc user) limit 3",
            "[user[51], user[50], user[41]]",
            """$head order by A01."rowid" DESC, A00."rowid" DESC LIMIT ?""",
        )
        chkExprSql(
            """
                (user, company) @*{}
                (user, @omit @sort_desc company, @omit @sort .firstName, @omit @sort_desc user)
                limit 3
            """,
            "[user[40], user[30], user[51]]",
            """$head order by A01."rowid" DESC, A00."firstName", A00."rowid" DESC LIMIT ?""",
        )
    }

    @Test fun testOrderBySqlDuplicationGroup() {
        tst.strictToString = false

        val head = """select A00."firstName" from "c0.user" A00 group by A00."firstName""""
        chkExprSql(
            "user @*{} (@group .firstName) limit 3",
            "[Bill, Jeff, Larry]",
            """$head order by A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort @group .firstName) limit 3",
            "[Bill, Jeff, Larry]",
            """$head order by A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@sort_desc @group .firstName) limit 3",
            "[Steve, Sergey, Paul]",
            """$head order by A00."firstName" DESC LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@omit @sort @max .company, @sort @group .firstName) limit 3",
            "[Mark, Steve, Jeff]",
            """$head order by MAX(A00."company"), A00."firstName" LIMIT ?""",
        )
        chkExprSql(
            "user @*{} (@omit @sort @max .company, @sort_desc @group .firstName) limit 3",
            "[Mark, Steve, Jeff]",
            """$head order by MAX(A00."company"), A00."firstName" DESC LIMIT ?""",
        )
    }

    private fun chkExprSql(expr: String, result: String, sql: String) {
        chk(expr, result)
        chkSql(sql)
    }
}
