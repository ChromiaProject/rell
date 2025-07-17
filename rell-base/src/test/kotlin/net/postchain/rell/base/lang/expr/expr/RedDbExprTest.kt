/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.QueryTester
import kotlin.test.Test

class RedDbExprTest: BaseRellTest(useSql = true) {
    private fun initDefault() {
        tst.strictToString = false
        def("entity user { name; score: integer; }")
        def("function fi(x: integer, tag: text): integer { print(tag); return x; }")
        insert("c0.user", "name,score", "1,'Bob',123")
        insert("c0.user", "name,score", "2,'Alice',456")
    }

    @Test fun testIf() {
        initDefault()

        chkArgs("a: text?", "= user @* { if (a != null) .name == a else true };") { q ->
            q.chk("Bob", "[user[1]]")
            q.chk("Alice", "[user[2]]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: text?", "= user @* { .name == if (a != null) a else .name };") { q ->
            q.chk("Bob", "[user[1]]")
            q.chk("Alice", "[user[2]]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer?", "= user @* { if (a != null) .score >= a else true };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer?", "= user @* { .score >= if (a != null) a else 0 };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer", "= user @* { .score == if (fi(a, 'A') == -1) .score else fi(a, 'B') };") { q ->
            q.chk(123, "[user[1]]").out("A", "B")
            q.chk(124, "[]").out("A", "B")
            q.chk(456, "[user[2]]").out("A", "B")
            q.chk(457, "[]").out("A", "B")
            q.chk(-1, "[user[1], user[2]]").out("A")
        }
    }

    @Test fun testElvis() {
        initDefault()

        chkArgs("a: text?", "= user @* { .name == a ?: .name };") { q ->
            q.chk("Bob", "[user[1]]")
            q.chk("Alice", "[user[2]]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer?", "= user @* { .score >= (a ?: 0) };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer?", "= user @* { .score >= (a ?: .score) };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[user[1], user[2]]")
        }
    }

    @Test fun testAndOr() {
        initDefault()

        chkArgs("a: text?", "= user @* { a != null and .name == a };") { q ->
            q.chk("Bob", "[user[1]]")
            q.chk("Alice", "[user[2]]")
            q.chk(null, "[]")
        }

        chkArgs("a: text?", "= user @* { a == null or .name == a };") { q ->
            q.chk("Bob", "[user[1]]")
            q.chk("Alice", "[user[2]]")
            q.chk(null, "[user[1], user[2]]")
        }

        chkArgs("a: integer?", "= user @* { a != null and .score >= a };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[]")
        }

        chkArgs("a: integer?", "= user @* { a == null or .score >= a };") { q ->
            q.chk(123, "[user[1], user[2]]")
            q.chk(124, "[user[2]]")
            q.chk(456, "[user[2]]")
            q.chk(457, "[]")
            q.chk(null, "[user[1], user[2]]")
        }
    }

    @Test fun testWhen() {
        initDefault()

        chkArgs("a: integer", "= user @* { .score == when (fi(a, 'A')) { -1 -> .score; else -> fi(a, 'B') } };") { q ->
            chk(q, 123, "[user[1]]", "A,B")
            chk(q, 124, "[]", "A,B")
            chk(q, 456, "[user[2]]", "A,B")
            chk(q, 457, "[]", "A,B")
            chk(q, -1, "[user[1], user[2]]", "A")
        }

        chkExpr("a: integer", "when (fi(a, 'A')) { 1 -> .score; 2 -> .score * 2; 3 -> .score * .score; else -> fi(a, 'B') }") { q ->
            chk(q, 1, "[123, 456]", "A")
            chk(q, 2, "[246, 912]", "A")
            chk(q, 3, "[15129, 207936]", "A")
            chk(q, 4, "[4, 4]", "A,B")
            chk(q, 1000, "[1000, 1000]", "A,B")
        }
    }

    @Test fun testWhenKeyRt1() {
        initWhenSpecial()

        val expr = """
            when (fi(a, 'A')) {
                fi(25, 'C1') -> fi(501, 'R1');
                .value1 -> fi(502, 'R2');
                fi(133, 'C3') -> fi(503, 'R3');
                .value2 -> fi(504, 'R4');
                else -> fi(505, 'R5')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A,C1,R1")
            chk(q, 100, "[502]", "A,C1,C3,R2,R4,R5")
            chk(q, 133, "[503]", "A,C1,C3,R3")
            chk(q, 200, "[504]", "A,C1,C3,R2,R4,R5")
            chk(q, 999, "[505]", "A,C1,C3,R2,R4,R5")
        }
    }

    @Test fun testWhenKeyRt2() {
        initWhenSpecial()

        val expr = """
            when (fi(a, 'A')) {
                fi(25, 'C1'), .value1 -> fi(501, 'R1');
                .value2, fi(133, 'C2') -> fi(502, 'R2');
                else -> fi(503, 'R3')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A,C1,R1")
            chk(q, 100, "[501]", "A,C1,C2,R1,R2,R3")
            chk(q, 133, "[502]", "A,C1,C2,R2")
            chk(q, 200, "[502]", "A,C1,C2,R1,R2,R3")
            chk(q, 999, "[503]", "A,C1,C2,R1,R2,R3")
        }
    }

    @Test fun testWhenKeyDb1() {
        initWhenSpecial()

        val expr = """
            when (.id + fi(a, 'A')) {
                fi(25, 'C1') -> fi(501, 'R1');
                .value1 -> fi(502, 'R2');
                fi(133, 'C3') -> fi(503, 'R3');
                .value2 -> fi(504, 'R4');
                else -> fi(505, 'R5')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A,C1,C3,R1,R2,R3,R4,R5")
            chk(q, 100, "[502]", "A,C1,C3,R1,R2,R3,R4,R5")
            chk(q, 133, "[503]", "A,C1,C3,R1,R2,R3,R4,R5")
            chk(q, 200, "[504]", "A,C1,C3,R1,R2,R3,R4,R5")
            chk(q, 999, "[505]", "A,C1,C3,R1,R2,R3,R4,R5")
        }
    }

    @Test fun testWhenKeyDb2() {
        initWhenSpecial()

        val expr = """
            when (.id + fi(a, 'A')) {
                fi(25, 'C1'), .value1 -> fi(501, 'R1');
                .value2, fi(133, 'C2') -> fi(502, 'R2');
                else -> fi(503, 'R3')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A,C1,C2,R1,R2,R3")
            chk(q, 100, "[501]", "A,C1,C2,R1,R2,R3")
            chk(q, 133, "[502]", "A,C1,C2,R1,R2,R3")
            chk(q, 200, "[502]", "A,C1,C2,R1,R2,R3")
            chk(q, 999, "[503]", "A,C1,C2,R1,R2,R3")
        }
    }

    @Test fun testWhenNoKey1() {
        initWhenSpecial()

        val expr = """
            when {
                fi(a, 'A1') == fi(25, 'C1') -> fi(501, 'R1');
                fi(a, 'A2') < .value1 -> fi(502, 'R2');
                fi(a, 'A3') == fi(133, 'C3') -> fi(503, 'R3');
                fi(a, 'A4') > .value2 -> fi(504, 'R4');
                else -> fi(505, 'R5')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A1,C1,R1")
            chk(q, 99, "[502]", "A1,C1,A2,A3,C3,A4,R2,R4,R5")
            chk(q, 133, "[503]", "A1,C1,A2,A3,C3,R3")
            chk(q, 201, "[504]", "A1,C1,A2,A3,C3,A4,R2,R4,R5")
            chk(q, 111, "[505]", "A1,C1,A2,A3,C3,A4,R2,R4,R5")
        }
    }

    @Test fun testWhenNoKey2() {
        initWhenSpecial()

        val expr = """
            when {
                fi(a, 'A1') == fi(25, 'C1'), fi(a, 'A2') < .value1 -> fi(501, 'R1');
                fi(a, 'A3') > .value2, fi(a, 'A4') == fi(133, 'C2') -> fi(502, 'R2');
                else -> fi(503, 'R3')
            }
        """

        chkExpr("a: integer", expr) { q ->
            chk(q, 25, "[501]", "A1,C1,R1")
            chk(q, 99, "[501]", "A1,C1,A2,A3,A4,C2,R1,R2,R3")
            chk(q, 133, "[502]", "A1,C1,A2,A3,A4,C2,R2")
            chk(q, 201, "[502]", "A1,C1,A2,A3,A4,C2,R1,R2,R3")
            chk(q, 111, "[503]", "A1,C1,A2,A3,A4,C2,R1,R2,R3")
        }
    }

    private fun initWhenSpecial() {
        tst.strictToString = false
        def("entity user { name; id: integer; value1: integer; value2: integer; }")
        def("function fi(x: integer, tag: text): integer { print(tag); return x; }")
        insert("c0.user", "name,id,value1,value2", "1,'Bob',0,100,200")
    }

    private fun chkExpr(params: String, expr: String, tester: (QueryTester) -> Unit) {
        chkArgs(params, "= user @* {} ( $expr );", tester)
    }

    private fun chk(q: QueryTester, arg: Any?, expected: String, out: String? = null) {
        q.chk(arg, expected)

        if (out != null) {
            q.out(*out.split(",").toTypedArray())
        }
    }
}
