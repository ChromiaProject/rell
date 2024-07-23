/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.SqlTestUtils
import org.junit.Test

class UpdateDeleteExprTest: BaseRellTest(useSql = true) {
    override fun entityDefs() = listOf(
            "entity user { name: text; mutable score: integer; }"
    )

    override fun objInserts() = listOf(
            insUser(101, "Bob", 333),
            insUser(102, "Alice", 555)
    )

    @Test fun testUpdateObject() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Alice' }; update u ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,678)")
    }

    @Test fun testDeleteObject() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; delete u;")
        chkData("user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Alice' }; delete u;")
        chkData()
    }

    @Test fun testUpdateNullable() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @? { .name == 'Bob' }; update u ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,555)")

        chkOp("val u = user @? { .name == 'Trudy' }; update u ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,555)")
    }

    @Test fun testDeleteNullable() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @? { .name == 'Bob' }; delete u;")
        chkData("user(102,Alice,555)")

        chkOp("val u = user @? { .name == 'Trudy' }; delete u;")
        chkData("user(102,Alice,555)")
    }

    @Test fun testUpdateCollection() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @* {}; update u ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,678)")

        chkOp("val u = user @* { .name == 'Bob' }; update u ( score += 123 );")
        chkData("user(101,Bob,579)", "user(102,Alice,678)")

        chkOp("val u = user @* { .name == 'Alice' }; update u ( score += 123 );")
        chkData("user(101,Bob,579)", "user(102,Alice,801)")

        chkOp("val u = user @* { .name == 'Trudy' }; update u ( score += 123 );")
        chkData("user(101,Bob,579)", "user(102,Alice,801)")

        chkOp("val u = set(user @* {}); update u ( score += 123 );")
        chkData("user(101,Bob,702)", "user(102,Alice,924)")
    }

    @Test fun testDeleteCollection() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @* { .name == 'Trudy' }; delete u;")
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @* { .name == 'Bob' }; delete u;")
        chkData("user(102,Alice,555)")

        chkOp("val u = user @* {}; delete u;")
        chkData()
    }

    @Test fun testUpdateCollectionDuplicate() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update [u, u, u] ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,555)")

        chkOp("val u = user @* {}; update [u[0], u[1], u[0], u[1], u[0], u[1]] ( score += 123 );")
        chkData("user(101,Bob,579)", "user(102,Alice,678)")
    }

    @Test fun testDeleteCollectionDuplicate() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; delete [u, u, u];")
        chkData("user(102,Alice,555)")
    }

    @Test fun testUpdateParentheses() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("update (user @ { .name == 'Bob' }) ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,555)")

        chkOp("update (user @? { .name == 'Alice' }) ( score += 123 );")
        chkData("user(101,Bob,456)", "user(102,Alice,678)")

        chkOp("update (user @* {}) ( score += 123 );")
        chkData("user(101,Bob,579)", "user(102,Alice,801)")
    }

    @Test fun testDeleteParentheses() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("delete (user @ { .name == 'Bob' });")
        chkData("user(102,Alice,555)")

        chkOp("delete (user @? { .name == 'Trudy' });")
        chkData("user(102,Alice,555)")

        chkOp("delete (user @* {});")
        chkData()
    }

    @Test fun testUpdateWhatSimple() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( .score = 444 );")
        chkData("user(101,Bob,444)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( score = 666 );")
        chkData("user(101,Bob,666)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update (u) ( score = 555 );")
        chkData("user(101,Bob,555)", "user(102,Alice,555)")
    }

    @Test fun testUpdateWhatAttributes() {
        chkData("user(101,Bob,333)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( score = 500 );")
        chkData("user(101,Bob,500)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( score = (user.score * 3 + 500) );")
        chkData("user(101,Bob,2000)", "user(102,Alice,555)")

        chkOp("val u = user @ { .name == 'Bob' }; update u ( score = (u.score * 3 + 500) );")
        chkData("user(101,Bob,6500)", "user(102,Alice,555)")

        chkOp("val u = user @* { .name == 'Bob' }; update u ( score = (u.score * 3 + 500) );",
                "ct_err:unknown_member:[list<user>]:score")
    }

    @Test fun testWrongType() {
        chkOp("update 123 ( .x = 0 );", "ct_err:stmt_update_expr_type:integer")
        chkOp("update 'Hello' ( .x = 0 );", "ct_err:stmt_update_expr_type:text")
        chkOp("update true ( .x = 0 );", "ct_err:stmt_update_expr_type:boolean")
        chkOp("update null ( .x = 0 );", "ct_err:stmt_update_expr_type:null")
        chkOp("update list<text>() ( .x = 0 );", "ct_err:stmt_update_expr_type:list<text>")
        chkOp("val v: integer? = _nullable(123); update v ( .x = 0 );", "ct_err:stmt_update_expr_type:integer?")
        chkOp("val v: list<user>? = null; update v ( .x = 0 );", "ct_err:stmt_update_expr_type:list<user>?")
        chkOp("val v = list<user?>(); update v ( .x = 0 );", "ct_err:stmt_update_expr_type:list<user?>")

        chkOp("delete 123;", "ct_err:stmt_update_expr_type:integer")
        chkOp("delete 'Hello';", "ct_err:stmt_update_expr_type:text")
        chkOp("delete true;", "ct_err:stmt_update_expr_type:boolean")
        chkOp("delete null;", "ct_err:stmt_update_expr_type:null")
        chkOp("delete list<text>();", "ct_err:stmt_update_expr_type:list<text>")
        chkOp("val v: integer? = _nullable(123); delete v;", "ct_err:stmt_update_expr_type:integer?")
        chkOp("val v: list<user>? = null; delete v;", "ct_err:stmt_update_expr_type:list<user>?")
        chkOp("val v = list<user?>(); delete v;", "ct_err:stmt_update_expr_type:list<user?>")
    }

    @Test fun testError() {
        chkOp("update (user @* {} ( .name )) ( .score += 123 );", "ct_err:stmt_update_expr_type:list<text>")
        chkOp("delete (user @* {} ( .name ));", "ct_err:stmt_update_expr_type:list<text>")
    }

    @Test fun testUpdatePortions() {
        tst.inserts = listOf()
        tst.sqlUpdatePortionSize = 5
        val n = 33

        chkOp("for (i in range($n)) { create user(name = 'user_' + i, score = i * i); }")
        chk("(user@*{}).size()", "int[$n]")

        chkOp("val u = user @* {}; update u (score += 100);")

        val code = """{
            val n = $n;
            val us = user @* {} ( user, .name, .score );
            if (us.size() != n) return 'size:' + us.size();
            for (i in range(n)) {
                val exp = i * i + 100;
                val u = us[i];
                if (u.score != exp) return 'score:' + u;
            }
            return 'OK';
        }"""
        chkEx(code, "text[OK]")
    }

    @Test fun testDeletePortions() {
        tst.inserts = listOf()
        tst.sqlUpdatePortionSize = 5
        val n = 33

        chkOp("for (i in range($n)) { create user(name = 'user_' + i, score = i * i); }")
        chk("(user@*{}).size()", "int[$n]")

        chkOp("val u = user @* {}; delete u;")
        chkData()
    }

    @Test fun testUpdateMultiEntities() {
        initUserCompany()
        chkData("user(1,Bob,Google,100)", "user(2,Alice,Amazon,200)", "company(3,Google,250)", "company(4,Amazon,525)")

        chkOp("update (user, company) @* { user.company == company.name } ( score = user.score + company.score );")
        chkData("user(1,Bob,Google,350)", "user(2,Alice,Amazon,725)", "company(3,Google,250)", "company(4,Amazon,525)")
    }

    @Test fun testDeleteMultiEntities() {
        initUserCompany()
        chkData("user(1,Bob,Google,100)", "user(2,Alice,Amazon,200)", "company(3,Google,250)", "company(4,Amazon,525)")

        chkOp("delete (user, company) @* { user.company == company.name, company.score == 250 };")
        chkData("user(2,Alice,Amazon,200)", "company(3,Google,250)", "company(4,Amazon,525)")
    }

    private fun initUserCompany() {
        def("entity user { name: text; company: text; mutable score: integer; }")
        def("entity company { name: text; score: integer; }")
        tst.inserts = listOf()

        chkOp("""
            create user(name = 'Bob', company = 'Google', score = 100);
            create user(name = 'Alice', company = 'Amazon', score = 200);
            create company(name = 'Google', score = 250);
            create company(name = 'Amazon', score = 525);
        """)
    }

    private fun insUser(id: Int, name: String, score: Int): String =
            SqlTestUtils.mkins("c0.user", "name,score", "$id,'$name',$score")
}
