/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class NullAnalysisTest: BaseRellTest() {
    @Test fun testInit() {
        def("function f(a: integer?): integer? = a;")

        chkEx("{ val x: integer? = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x: integer? = f(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x: integer? = 123; return x + 1; }", "int[124]")

        chkEx("{ val x: integer?; x = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x: integer?; x = f(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x: integer?; x = 123; return x + 1; }", "int[124]")

        chkEx("{ val x: integer? = null; return abs(x); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x: integer? = f(123); return abs(x); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x: integer? = 123; return abs(x); }", "int[123]")

        chkEx("{ val x: integer? = 123; val y: integer? = x; val z: integer? = y; return _type_of(z); }",
            "text[integer]")
    }

    @Test fun testAssignment() {
        def("function f(a: integer?): integer? = a;")

        chkEx("{ var x: integer? = null; x = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = null; x = f(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = null; x = 123; return x + 1; }", "int[124]")

        chkEx("{ var x: integer? = f(123); x = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = f(123); x = f(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = f(123); x = 123; return x + 1; }", "int[124]")

        chkEx("{ var x: integer? = 123; x = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; x = f(456); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; x = 456; return x + 1; }", "int[457]")

        chkEx("{ var x: integer?; x = null; return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer?; x = f(456); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer?; x = 456; return x + 1; }", "int[457]")

        chkEx("{ var x: integer? = _nullable(123); return _type_of(x); }", "text[integer?]")
        chkEx("{ var x: integer? = _nullable(123); if (x == null) return ''; return _type_of(x); }", "text[integer]")
        chkEx("{ var x: integer? = _nullable(123); if (x == null) return ''; x = _nullable(456); return _type_of(x); }",
                "text[integer?]")
    }

    @Test fun testAssignmentIf() {
        initLib()

        chkType("var x = intz(null); if (bool(1)) x = 123; else x = 456;", "integer")
        chkType("var x = intz(123); if (bool(1)) x = 456; else x = null;", "integer?")
        chkType("var x = intz(123); if (bool(1)) x = null; else x = null;", "integer?")

        chkType("var x = intz(null); if (x == null) x = 123;", "integer")
        chkType("var x = intz(null); if (x != null) x = 123;", "integer?")
        chkType("var x = intz(null); if (x != null) x = null;", "integer?")

        chkType("var x = intz(123); if (bool(1)) x = 456;", "integer?")
        chkType("var x = intz(123); if (bool(1)) x = 456; else x = 789;", "integer")
        chkType("var x = intz(123); if (bool(1)) x = 456; else x!!;", "integer")
        chkType("var x = intz(123); if (bool(1)) x!!; else x = 789;", "integer")

        chkType("var x = intz(123); x!!; if (bool(1)) x = 456;", "integer")
        chkType("var x = intz(123); x!!; if (bool(1)) x = intz(456);", "integer?")
        chkType("var x = intz(123); x!!; if (bool(1)) x = 456; else x = 789;", "integer")
        chkType("var x = intz(123); x!!; if (bool(1)) x = intz(456); else x = 789;", "integer?")
        chkType("var x = intz(123); x!!; if (bool(1)) x = 456; else x = intz(789);", "integer?")
        chkType("var x = intz(123); x!!; if (bool(1)) { x = intz(456); x!!; } else x = 789;", "integer")
        chkType("var x = intz(123); x!!; if (bool(1)) x = 456; else { x = intz(789); x!!; }", "integer")
    }

    @Test fun testAssignmentWhen() {
        initLib()

        chkType("var x = intz(null); when { bool(1) -> x = 123; else -> x = 456; }", "integer")
        chkType("var x = intz(123); when { bool(1) -> x = 456; else -> x = null; }", "integer?")
        chkType("var x = intz(123); when { bool(1) -> x = null; else -> x = null; }", "integer?")
        chkType("var x = intz(null); when { x == null -> x = 123; }", "integer")
        chkType("var x = intz(null); when { x != null -> x = 123; }", "integer?")

        chkType("var x = intz(123); when { bool(1) -> x = 456; }", "integer?")
        chkType("var x = intz(123); when { bool(1) -> x = 456; bool(0) -> x = 789; }", "integer?")
        chkType("var x = intz(123); when { bool(1) -> x = 456; else -> x = 789; }", "integer")
        chkType("var x = intz(123); when { bool(1) -> x = 456; bool(0) -> x!!; }", "integer?")
        chkType("var x = intz(123); when { bool(1) -> x = 456; else -> x!!; }", "integer")
        chkType("var x = intz(123); when { bool(1) -> x!!; bool(0) -> x = 789; }", "integer?")
        chkType("var x = intz(123); when { bool(1) -> x!!; else -> x = 789; }", "integer")

        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = intz(456); }", "integer?")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; bool(0) -> x = 789; }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; else -> x = 789; }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = intz(456); else -> x = 789; }", "integer?")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; else -> x = intz(789); }", "integer?")
        chkType("var x = intz(123); x!!; when { bool(1) -> { x = intz(456); x!!; } bool(0) -> x = 789; }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> { x = intz(456); x!!; } else -> x = 789; }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; bool(0) -> { x = intz(789); x!!; } }", "integer")
        chkType("var x = intz(123); x!!; when { bool(1) -> x = 456; else -> { x = intz(789); x!!; } }", "integer")
    }

    @Test fun testCompoundAssignment() {
        initLib()
        chkEx("{ var x = intz(123); x += 1; return x; }", "ct_err:binop_operand_type:+=:[integer?]:[integer]")
        chkEx("{ var x: integer? = null; x += 1; return x; }", "ct_err:binop_operand_type:+=:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; x += 1; return x; }", "124")

        chkEx("{ var x = intz(123); if (x != null) x += 1; return x; }", "124")
        chkEx("{ var x = intz(null); if (x != null) x += 1; return x; }", "null")
    }

    @Test fun testIncrement() {
        initLib()
        chkEx("{ var x = intz(123); x++; return x; }", "ct_err:expr_incdec_type:++:integer?")

        chkEx("{ var x: integer? = null; x++; return x; }", "ct_err:expr_incdec_type:++:integer?")
        chkEx("{ var x: integer? = null; ++x; return x; }", "ct_err:expr_incdec_type:++:integer?")
        chkEx("{ var x: integer? = null; x--; return x; }", "ct_err:expr_incdec_type:--:integer?")
        chkEx("{ var x: integer? = null; --x; return x; }", "ct_err:expr_incdec_type:--:integer?")

        chkEx("{ var x: integer? = 123; x++; return x; }", "124")
        chkEx("{ var x: integer? = 123; ++x; return x; }", "124")
        chkEx("{ var x: integer? = 123; x--; return x; }", "122")
        chkEx("{ var x: integer? = 123; --x; return x; }", "122")

        chkEx("{ var x = intz(123); if (x != null) x++; return x; }", "124")
        chkEx("{ var x = intz(123); if (x != null) ++x; return x; }", "124")
        chkEx("{ var x = intz(123); if (x != null) x--; return x; }", "122")
        chkEx("{ var x = intz(123); if (x != null) --x; return x; }", "122")

        chkEx("{ var x = intz(null); if (x != null) x++; return x; }", "null")
        chkEx("{ var x = intz(null); if (x != null) ++x; return x; }", "null")
        chkEx("{ var x = intz(null); if (x != null) x--; return x; }", "null")
        chkEx("{ var x = intz(null); if (x != null) --x; return x; }", "null")
    }

    @Test fun testBooleanExpressions() {
        chkFull("query q(x: integer?) = x > 5;", 6, "ct_err:binop_operand_type:>:[integer?]:[integer]")
        chkFull("query q(x: integer?) = x != null;", 5, "boolean[true]")
        chkFull("query q(x: integer?) = x == null;", 5, "boolean[false]")

        @Suppress("JoinDeclarationAndAssignment")
        var q: String

        q = "query q(x: integer?) = x != null and x > 5;"
        chkFull(q, 6, "boolean[true]")
        chkFull(q, 5, "boolean[false]")
        chkFull(q, null as Long?, "boolean[false]")
        chkFull("query q(x: integer?) = x == null and x > 5;", 0, "ct_err:binop_operand_type:>:[integer?]:[integer]")

        q = "query q(x: integer?) = x == null or x > 5;"
        chkFull(q, 6, "boolean[true]")
        chkFull(q, 5, "boolean[false]")
        chkFull(q, null as Long?, "boolean[true]")
        chkFull("query q(x: integer?) = x != null or x > 5;", 0, "ct_err:binop_operand_type:>:[integer?]:[integer]")

        q = "query q(x: integer?, y: integer?) = x == null or y == null or x > y;"
        chkFull(q, 6, 5, "boolean[true]")
        chkFull(q, 5, 6, "boolean[false]")
        chkFull(q, null as Long?, 5, "boolean[true]")
        chkFull(q, 5, null as Long?, "boolean[true]")
        chkFull(q, null as Long?, null as Long?, "boolean[true]")

        chkFull("query q(x: integer?, y: integer?) = x == null or x > y;", 0, 0,
                "ct_err:binop_operand_type:>:[integer]:[integer?]")
        chkFull("query q(x: integer?, y: integer?) = y == null or x > y;", 0, 0,
                "ct_err:binop_operand_type:>:[integer?]:[integer]")

        q = "query q(x: integer?) = not (x == null) and x > 5;"
        chkFull(q, 6, "boolean[true]")
        chkFull(q, 5, "boolean[false]")
        chkFull(q, null as Long?, "boolean[false]")

        q = "query q(x: integer?, y: integer?) = not (x == null or y == null) and x > y;"
        chkFull(q, 6, 5, "boolean[true]")
        chkFull(q, 5, 6, "boolean[false]")
        chkFull(q, null as Long?, 5, "boolean[false]")
        chkFull(q, 5, null as Long?, "boolean[false]")
        chkFull(q, null as Long?, null as Long?, "boolean[false]")
    }

    @Test fun testImplicationsOperatorNotNull() {
        chkImplicationsExpr("x!!")
    }

    @Test fun testImplicationsRequire() {
        chkImplicationsExpr("require(x)")
        chkImplicationsExpr("require_not_empty(x)")
        chkImplicationsExpr("require(x, 'hello')")
        chkImplicationsExpr("require_not_empty(x, 'hello')")
        chkImplicationsExpr("require(message = 'hello', value = x)")
        chkImplicationsExpr("require_not_empty(message = 'hello', value = x)")
    }

    @Test fun testSafeAccess() {
        def("struct rec { mutable x: integer; }")
        chkEx("{ val r: rec? = rec(123); return _type_of(r.x); }", "text[integer]")
        chkEx("{ val r: rec? = rec(123); return _type_of(r?.x); }", "text[integer]")
        chkEx("{ val r: rec? = _nullable(rec(123)); return _type_of(r?.x); }", "text[integer?]")
    }

    private fun chkImplicationsExpr(expr: String) {
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = _nullable(123); $expr; return _type_of(x); }", "text[integer]")
        chkEx("{ val x = _nullable(123); val y = $expr; return _type_of(x); }", "text[integer]")
        chkEx("{ val x = _nullable(123); val y: integer?; y = $expr; return _type_of(x); }", "text[integer]")
        chkEx("{ val x = _nullable(123); var y: integer?; y = $expr; return _type_of(x); }", "text[integer]")

        chkEx("{ val x = _nullable(false); return _type_of(x); }", "text[boolean?]")
        chkEx("{ val x = _nullable(false); if ($expr) return ''; return _type_of(x); }", "text[boolean]")
        chkEx("{ val x = _nullable(true); if ($expr) return _type_of(x); return ''; }", "text[boolean]")
    }

    @Test fun testIf() {
        initLib()

        chkEx("{ val x = intz(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x = intz(123); if (x != null) return x + 1; return -1; }", "124")
        chkEx("{ val x = intz(123); if (x != null) {} else return -1; return x + 1; }", "124")
        chkEx("{ val x = intz(123); if (x == null) return -1; return x + 1; }", "124")
        chkEx("{ val x = intz(123); if (x == null) return -1; else return x + 1; }", "124")

        chkEx("{ val x = intz(123); return abs(x); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x = intz(123); if (x == null) return 0; return abs(x); }", "123")
        chkEx("{ val x = intz(123); if (x != null) return 0; return abs(x); }",
            "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x = intz(123); if (x != null) return abs(x); return 0; }", "123")
        chkEx("{ val x = intz(123); if (x == null) return abs(x); return 0; }",
            "ct_err:expr_call_badargs:[abs]:[integer?]")
    }

    @Test fun testIfMore() {
        initLib()

        val xy = "val x = intz(123); val y = intz(456);"
        chkEx("{ $xy if (x == null and y == null) return 'A'; return _type_of(x); }", "integer?")
        chkEx("{ $xy if (x == null or y == null) return 'A'; return _type_of(x); }", "integer")

        val code = "if (x == null and y == null) return 'A'; else if (x == null) return 'B';"
        chkEx("{ $xy $code return _type_of(x); }", "integer")
        chkEx("{ $xy $code return _type_of(y); }", "integer?")
    }

    @Test fun testIfType() {
        def("function f(a: integer?): integer? = a;")
        def("function g(a: integer?): rec? = if (a == null) null else rec(a);")
        def("struct rec { a: integer; }")

        chkEx("{ val x = f(123); return _type_of(x); }", "text[integer?]")

        chkEx("{ val x = f(123); if (x != null) return _type_of(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (x != null) return _type_of(x); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = f(123); if (x != null) {} else return _type_of(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (x != null) {} else return _type_of(x); return _type_of(x); }", "text[integer?]")

        chkEx("{ val x = f(123); if (x == null) return _type_of(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (x == null) return _type_of(x); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = f(123); if (x == null) return _type_of(x); else return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (x == null) return _type_of(x); else return _type_of(x); }", "text[integer?]")

        chkEx("{ val x = f(123); if (null != x) return _type_of(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (null != x) return _type_of(x); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = f(123); if (null == x) return _type_of(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = f(null); if (null == x) return _type_of(x); return _type_of(x); }", "text[integer?]")

        chkEx("{ val x = g(123); if (x !== null) return _type_of(x); return _type_of(x); }", "text[rec]")
        chkEx("{ val x = g(null); if (x !== null) return _type_of(x); return _type_of(x); }", "text[rec?]")
        chkEx("{ val x = g(123); if (x === null) return _type_of(x); return _type_of(x); }", "text[rec]")
        chkEx("{ val x = g(null); if (x === null) return _type_of(x); return _type_of(x); }", "text[rec?]")

        chkEx("{ val x = g(123); if (null !== x) return _type_of(x); return _type_of(x); }", "text[rec]")
        chkEx("{ val x = g(null); if (null !== x) return _type_of(x); return _type_of(x); }", "text[rec?]")
        chkEx("{ val x = g(123); if (null === x) return _type_of(x); return _type_of(x); }", "text[rec]")
        chkEx("{ val x = g(null); if (null === x) return _type_of(x); return _type_of(x); }", "text[rec?]")
    }

    @Test fun testIfBlockVar() {
        initLib()
        chkEx("{ val x = intz(123); if (x != null) { val y = x; return _type_of(y); } return ''; }", "integer")
        chkEx("{ val x = intz(null); if (x == null) { val y = x; return _type_of(y); } return ''; }", "integer?")
        chkEx("{ val x = intz(null); if (x != null) return ''; val y = x; return _type_of(y); }", "integer?")
        chkEx("{ val x = intz(123); if (x == null) return ''; val y = x; return _type_of(y); }", "integer")
    }

    @Test fun testIfExpr() {
        chkFull("query q(x: integer?) = x + 1;", 0, "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkFull("query q(x: integer?) = if (x == null) x + 1 else 0;", 0, "ct_err:binop_operand_type:+:[integer?]:[integer]")

        chkFull("query q(x: integer?) = _type_of(x + 1);", 0, "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkFull("query q(x: integer?) = _type_of(if (x == null) 0 else x + 1);", 0, "text[integer]")
        chkFull("query q(x: integer?) = _type_of(if (x == null) null else x + 1);", 0, "text[integer?]")
        chkFull("query q(x: integer?) = _type_of(if (not x??) 0 else x + 1);", 0, "text[integer]")
        chkFull("query q(x: integer?) = _type_of(if (not x??) null else x + 1);", 0, "text[integer?]")
        chkFull("query q(x: integer?) = _type_of(if (x??) x + 1 else 0);", 0, "text[integer]")
        chkFull("query q(x: integer?) = _type_of(if (x??) x + 1 else null);", 0, "text[integer?]")

        var q = "query q(x: integer?) = if (x == null) 0 else x + 1;"
        chkFull(q, null as Long?, "int[0]")
        chkFull(q, 123, "int[124]")

        q = "query q(x: integer?) = if (x != null) x + 1 else 0;"
        chkFull(q, null as Long?, "int[0]")
        chkFull(q, 123, "int[124]")

        chkFull("query q(x: integer?, y: integer?) = x + y;", 0, "ct_err:binop_operand_type:+:[integer?]:[integer?]")
        chkFull("query q(x: integer?, y: integer?) = if (x != null) x + y else 0;", 0,
                "ct_err:binop_operand_type:+:[integer]:[integer?]")
        chkFull("query q(x: integer?, y: integer?) = if (y != null) x + y else 0;", 0,
                "ct_err:binop_operand_type:+:[integer?]:[integer]")

        q = "query q(x: integer?, y: integer?) = if (x != null) if (y != null) x + y else 1 else 0;"
        chkFull(q, null as Long?, null as Long?, "int[0]")
        chkFull(q, null as Long?, 456, "int[0]")
        chkFull(q, 123, null as Long?, "int[1]")
        chkFull(q, 123, 456, "int[579]")

        q = "query q(x: integer?, y: integer?) = if (x != null and y != null) x + y else 0;"
        chkFull(q, null as Long?, null as Long?, "int[0]")
        chkFull(q, null as Long?, 456, "int[0]")
        chkFull(q, 123, null as Long?, "int[0]")
        chkFull(q, 123, 456, "int[579]")

        q = "query q(x: integer?, y: integer?) = if (x == null) 0 else if (y == null) 1 else x + y;"
        chkFull(q, null as Long?, null as Long?, "int[0]")
        chkFull(q, null as Long?, 456, "int[0]")
        chkFull(q, 123, null as Long?, "int[1]")
        chkFull(q, 123, 456, "int[579]")
    }

    @Test fun testWhen() {
        chkEx("{ val x = _nullable(123); return x + 1; }", "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ val x = _nullable(123); when { x != null -> return x + 1; } return -1; }", "int[124]")
        chkEx("{ val x = _nullable(123); when { x == null -> return -1; } return x + 1; }", "int[124]")

        chkEx("{ val x = _nullable(123); return abs(x); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x = _nullable(123); when { x == null -> return 0; } return abs(x); }", "int[123]")
        chkEx("{ val x = _nullable(123); when { x != null -> return 0; } return abs(x); }",
            "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val x = _nullable(123); when { x != null -> return abs(x); } return 0; }", "int[123]")
        chkEx("{ val x = _nullable(123); when { x == null -> return abs(x); } return 0; }",
            "ct_err:expr_call_badargs:[abs]:[integer?]")
    }

    @Test fun testWhenMultipleConditions() {
        initLib()
        val err = "ct_err:binop_operand_type:+:[integer?]:[integer]"

        chkEx("{ val x = intz(123); return x + 1; }", err)
        chkEx("{ val x = intz(123); when { x != null -> return x + 1; } return -1; }", "124")

        chkEx("{ val x = intz(123); when { x != null, bool(1) -> return x + 1; } return -1; }", err)
        chkEx("{ val x = intz(123); when { bool(1), x != null -> return x + 1; } return -1; }", err)

        chkEx("{ val x = intz(123); when { x == null -> {} else -> return x + 1; } return -1; }", "124")
        chkEx("{ val x = intz(123); when { x == null, bool(0) -> {} else -> return x + 1; } return -1; }", "124")
        chkEx("{ val x = intz(123); when { bool(0), x == null -> {} else -> return x + 1; } return -1; }", "124")

        chkEx("{ val x = intz(123); when { x == null -> return 0; } return x + 1; }", "124")
        chkEx("{ val x = intz(123); when { x == null, bool(0) -> return 0; } return x + 1; }", "124")
        chkEx("{ val x = intz(123); when { bool(0), x == null -> return 0; } return x + 1; }", "124")

        chkEx("{ val x = intz(123); return when { x < 0 -> 0; else -> -1; }; }",
            "ct_err:binop_operand_type:<:[integer?]:[integer]")
        chkEx("{ val x = intz(123); return when { x == null, x < 0 -> 0; else -> -1; }; }", "-1")
        chkEx("{ val x = intz(123); return when { x == null -> 0; x < 0 -> 1; else -> -1; }; }", "-1")
    }

    @Test fun testWhenExpr() {
        chkFull("query q(x: integer?) = x + 1;", 0, "ct_err:binop_operand_type:+:[integer?]:[integer]")

        chkFull("query q(x: integer?) = _type_of(x + 1);", 0, "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkFull("query q(x: integer?) = _type_of(when { x == null -> 0; else -> x + 1 });", 0, "text[integer]")
        chkFull("query q(x: integer?) = _type_of(when { x == null -> null; else -> x + 1 });", 0, "text[integer?]")
        chkFull("query q(x: integer?) = _type_of(when(x) { null -> 0; else -> x + 1 });", 0, "text[integer]")
        chkFull("query q(x: integer?) = _type_of(when(x) { null -> null; else -> x + 1 });", 0, "text[integer?]")

        chkFull("query q(x: integer?) = when(x) { null -> ''; else -> _type_of(x) };", 0, "text[integer]")
        chkFull("query q(x: integer?) = when(x) { 123 -> ''; else -> _type_of(x) };", 0, "text[integer?]")

        chkFull("query q(x: integer?) = when { x == null -> 0; else -> x + 1 };", 123, "int[124]")
        chkFull("query q(x: integer?) = when { x != null -> x + 1; else -> 0 };", 123, "int[124]")

        chkFull("query q(x: integer?, y: integer?) = x + y;", 0, "ct_err:binop_operand_type:+:[integer?]:[integer?]")
        chkFull("query q(x: integer?, y: integer?) = when { x != null -> x + y; else -> 0 };", 0,
                "ct_err:binop_operand_type:+:[integer]:[integer?]")
        chkFull("query q(x: integer?, y: integer?) = when { y != null -> x + y; else -> 0 };", 0,
                "ct_err:binop_operand_type:+:[integer?]:[integer]")

        chkFull("query q(x: integer?, y: integer?) = when { x != null -> when { y != null -> x + y; else -> 1 }; else -> 0 };",
                123, 456, "int[579]")

        chkFull("query q(x: integer?, y: integer?) = when { x != null and y != null -> x + y; else -> 0 };",
                123, 456, "int[579]")

        chkFull("query q(x: integer?, y: integer?) = when { x == null -> 0; else -> when { y == null -> 1; else -> x + y }};",
                123, 456, "int[579]")
    }

    @Test fun testIfReturn() {
        initLib()
        chkEx("{ val x = intz(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); if (x == null) return ''; return _type_of(x); }", "integer")
        chkEx("{ val x = intz(null); if (x != null) return ''; return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(null); if (x == null) {} else return ''; return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); if (x != null) {} else return ''; return _type_of(x); }", "integer")
    }

    @Test fun testWhenReturn() {
        initLib()

        chkEx("{ val x = intz(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); when { x == null -> return ''; } return _type_of(x); }", "integer")
        chkEx("{ val x = intz(null); when { x != null -> return ''; } return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(null); when { x == null -> {} else -> return ''; } return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); when { x != null -> {} else -> return ''; } return _type_of(x); }", "integer")

        chkEx("{ val x = intz(123); when(x) { null -> return ''; } return _type_of(x); }", "integer")
        chkEx("{ val x = intz(123); when(x) { 456 -> return ''; } return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); when(x) { 456 -> return ''; null -> return ''; } return _type_of(x); }", "integer")
        chkEx("{ val x = intz(123); when(x) { 456, null -> return ''; } return _type_of(x); }", "integer")
    }

    @Test fun testExists() {
        initLib()
        chkEx("{ val x = intz(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); if (exists(x)) return _type_of(x); return ''; }", "integer")
        chkEx("{ val x = intz(123); if (not exists(x)) return ''; return _type_of(x); }", "integer")
        chkEx("{ val x = intz(123); if (exists(x)) return _type_of(x); return ''; }", "integer")
        chkEx("{ val x = intz(null); if (not exists(x)) return _type_of(x); return ''; }", "integer?")
    }

    @Test fun testExistsCollection() {
        tst.strictToString = false
        chkEx("{ val x = _nullable([123]); return _type_of(x); }", "list<integer>?")
        chkEx("{ val x = _nullable([123]); if (exists(x)) return _type_of(x); return ''; }", "list<integer>")
        chkEx("{ val x = _nullable([123]); if (not exists(x)) return ''; return _type_of(x); }", "list<integer>")
        chkEx("{ val x = _nullable([123]); if (exists(x)) return _type_of(x); return ''; }", "list<integer>")
        chkEx("{ val x = if(2>1) null else [123]; if (not exists(x)) return _type_of(x); return ''; }", "list<integer>?")
    }

    @Test fun testEmpty() {
        initLib()
        chkEx("{ val x = intz(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); if (not empty(x)) return _type_of(x); return ''; }", "integer")
        chkEx("{ val x = intz(123); if (empty(x)) return ''; return _type_of(x); }", "integer")
        chkEx("{ val x = intz(123); if (not empty(x)) return _type_of(x); return ''; }", "integer")
        chkEx("{ val x = intz(null); if (empty(x)) return _type_of(x); return ''; }", "integer?")
    }

    @Test fun testEmptyCollection() {
        tst.strictToString = false
        chkEx("{ val x = _nullable([123]); return _type_of(x); }", "list<integer>?")
        chkEx("{ val x = _nullable([123]); if (not empty(x)) return _type_of(x); return ''; }", "list<integer>")
        chkEx("{ val x = _nullable([123]); if (empty(x)) return ''; return _type_of(x); }", "list<integer>")
        chkEx("{ val x = _nullable([123]); if (not empty(x)) return _type_of(x); return ''; }", "list<integer>")
        chkEx("{ val x = if(2>1) null else [123]; if (empty(x)) return _type_of(x); return ''; }", "list<integer>?")
    }

    @Test fun testList() {
        initLib()
        chkEx("{ val x: integer? = intz(123); return _type_of([x]); }", "list<integer?>")
        chkEx("{ val x: integer? = 123; return _type_of([x]); }", "list<integer>")
        chkEx("{ val x: integer? = null; return _type_of([x]); }", "list<integer?>")
        chkEx("{ val x: integer? = intz(123); if (x != null) return _type_of([x]); return ''; }", "list<integer>")
    }

    @Test fun testWhileNull() {
        def("struct node { next: node?; value: integer; }")
        def("function make_nodes(): node? = node(123, node(456, node(789, null)));")

        chkEx("{ var p = make_nodes(); while (p != null) { p = p.next; } return p; }", "null")

        chkEx("{ var p = make_nodes(); var s = 0; while (p != null) { s += p.value; p = p.next; } return s; }", "int[1368]")

        chkEx("{ var p = make_nodes(); var s = 0; while (p != null) { p = p.next; s += p.value; } return s; }",
                "ct_err:expr_mem_null:node?:value")

        chkEx("{ var p = make_nodes(); var s = 0; while (p == null) { s += p.value; p = p.next; } return s; }",
                "ct_err:[expr_mem_null:node?:value][expr_mem_null:node?:next]")

        chkEx("{ var p = make_nodes(); var s = 0; while (s == 0) { s += p.value; p = p.next; } return s; }",
                "ct_err:[expr_mem_null:node?:value][expr_mem_null:node?:next]")

        chkEx("{ var p = make_nodes(); while (p != null) { p = p.next; } return _type_of(p); }", "text[node?]")
        chkEx("{ var p = make_nodes(); p!!; return _type_of(p); }", "text[node]")
        chkEx("{ var p = make_nodes(); p!!; while (p != null) { p = p.next; } return _type_of(p); }", "text[node?]")
    }

    @Test fun testLoopVarModification() {
        chkEx("{ var x: integer? = 123; var b = true; while (x > 0 and b) { b = false; } return 0; }", "int[0]")
        chkEx("{ var x: integer? = 123; var b = true; while (x > 0 and b) { b = false; x = null; } return 0; }",
                "ct_err:binop_operand_type:>:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; var b = true; while (x > 0 and b) { b = false; x = 123; } return 0; }",
                "ct_err:binop_operand_type:>:[integer?]:[integer]")

        chkEx("{ var x: integer? = 123; var b = true; while (b) { print(x+1); b = false; } return 0; }", "int[0]")
        chkEx("{ var x: integer? = 123; var b = true; while (b) { print(x+1); x = 123; b = false; } return 0; }",
                "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; var b = true; while (b) { x = 123; print(x+1); b = false; } return 0; }", "int[0]")

        chkEx("{ var x: integer? = 123; for (k in [0]) { print(x+1); } return 0; }", "int[0]")
        chkEx("{ var x: integer? = 123; for (k in [0]) { print(x+1); x = 123; } return 0; }",
                "ct_err:binop_operand_type:+:[integer?]:[integer]")
        chkEx("{ var x: integer? = 123; for (k in [0]) { x = 123; print(x+1); } return 0; }", "int[0]")
    }

    @Test fun testDefiniteFactNullEquality() {
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkDefiniteFactNullEquality("==", true)
        chkDefiniteFactNullEquality("!=", false)
        chkDefiniteFactNullEquality("===", true)
        chkDefiniteFactNullEquality("!==", false)
    }

    private fun chkDefiniteFactNullEquality(op: String, eq: Boolean) {
        val resNull = "boolean[$eq]"
        val resNotNull = "boolean[${!eq}]"
        chkDefiniteFactExpr("x $op null", resNull, resNotNull, "ct_err:binop_operand_type:$op:[rec]:[null]")
        chkDefiniteFactExpr("null $op x", resNull, resNotNull, "ct_err:binop_operand_type:$op:[null]:[rec]")
    }

    @Test fun testDefiniteFactIsNull() {
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkDefiniteFactExpr("x??", "boolean[false]", "boolean[true]", "ct_err:unop_operand_type:??:[rec]")
    }

    @Test fun testDefiniteFactElvis() {
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkDefiniteFactExpr("x ?: rec(-1)", "rec[a=int[-1]]", "rec[a=int[123]]", "ct_err:binop_operand_type:?::[rec]:[rec]")
    }

    @Test fun testDefiniteFactSafeMember() {
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkDefiniteFactExpr("x?.a", "null", "int[123]", "ct_err:expr_safemem_type:[rec]:a")
    }

    @Test fun testDefiniteFactExists() {
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkDefiniteFactExpr("exists(x)", "boolean[false]", "boolean[true]", "ct_err:expr_call_badargs:[exists]:[rec]")
        chkDefiniteFactExpr("not exists(x)", "boolean[true]", "boolean[false]", "ct_err:expr_call_badargs:[exists]:[rec]")
    }

    private fun chkDefiniteFactExpr(expr: String, resNull: String, resNotNull: String, ctErr: String) {
        chkEx("{ val x = rec(123); return $expr; }", ctErr)
        chkWarn()

        chkEx("{ val x: rec? = rec(123); return $expr; }", resNotNull)
        chkWarn("expr:smartnull:var:never:[x]")

        chkEx("{ val x: rec? = null; return $expr; }", resNull)
        chkWarn("expr:smartnull:var:always:[x]")

        chkEx("{ val x = f(rec(123)); if (x == null) return null; return $expr; }", resNotNull)
        chkWarn("expr:smartnull:var:never:[x]")

        chkEx("{ val x = f(null); if (x != null) return null; return $expr; }", resNull)
        chkWarn("expr:smartnull:var:always:[x]")
    }

    @Test fun testDefiniteFactOperatorNotNull() {
        initLib()
        chkDefiniteFactNullCast("x!!", "ct_err:unop_operand_type:!!:[integer]", "rt_err:null_value")
    }

    @Test fun testDefiniteFactRequire() {
        initLib()
        chkDefiniteFactNullCast("require(x)", "ct_err:expr_call_badargs:[require]:[integer]", "req_err:null")
        chkDefiniteFactNullCast("require_not_empty(x)",
            "ct_err:expr_call_badargs:[require_not_empty]:[integer]", "req_err:null")
    }

    private fun chkDefiniteFactNullCast(expr: String, ctErr: String, rtErr: String) {
        chkEx("{ val x = 123; return $expr; }", ctErr)
        chkWarn()

        chkEx("{ val x: integer? = 123; return $expr; }", "123")
        chkWarn("expr:smartnull:var:never:[x]")

        chkEx("{ val x: integer? = null; return $expr; }", rtErr)
        chkWarn("expr:smartnull:var:always:[x]")

        chkEx("{ val x = _nullable(123); if (x == null) return 0; return $expr; }", "123")
        chkWarn("expr:smartnull:var:never:[x]")

        chkEx("{ val x = intz(null); if (x != null) return 0; return $expr; }", rtErr)
        chkWarn("expr:smartnull:var:always:[x]")
    }

    @Test fun testAtExpr() {
        initEntity()
        chkAtExpr("123", "if (x == null) true else .score >= x", "user[33]")
        chkAtExpr("123", "if (x != null) .score >= x else true", "user[33]")
        chkAtExpr("123", "when { x == null -> true; else -> .score >= x }", "user[33]")
        chkAtExpr("123", "when { x != null -> .score >= x; else -> true }", "user[33]")
        chkAtExpr("123", ".score >= if (x == null) 0 else x", "user[33]")
        chkAtExpr("123", ".score >= if (x != null) x else 0", "user[33]")
        chkAtExpr("123", ".score >= when { x == null -> 0; else -> x }", "user[33]")
        chkAtExpr("123", ".score >= when { x != null -> x; else -> 0 }", "user[33]")
        chkAtExpr("123", ".score >= if (x == null) .score else x", "user[33]")
        chkAtExpr("123", ".score >= if (x != null) x else .score", "user[33]")
        chkAtExpr("123", ".score >= when { x == null -> .score; else -> x }", "user[33]")
        chkAtExpr("123", ".score >= when { x != null -> x; else -> .score }", "user[33]")
        chkAtExpr("123", "x != null and .score >= x", "user[33]")
        chkAtExpr("123", "x == null or .score >= x", "user[33]")
        chkAtExpr("123", ".score >= (x ?: 0)", "user[33]")
        chkAtExpr("123", ".score >= (x ?: .score)", "user[33]")
    }

    private fun initEntity() {
        tstCtx.useSql = true
        initLib()
        def("entity user { name; score: integer; }")
        insert("c0.user", "name,score", "33,'Bob',1000")
    }

    @Suppress("SameParameterValue")
    private fun chkAtExpr(v: String, expr: String, expected: String) {
        chkEx("{ val x = intz($v); return user @? { $expr }; }", expected)
    }

    @Test fun testAtExprPlaceholder() {
        def("function data(): list<integer?> = [123, null, 456, null, 789];")
        chk("data()", "list<integer?>[int[123],null,int[456],null,int[789]]")
        chk("data() @* {}", "list<integer?>[int[123],null,int[456],null,int[789]]")
        chk("data() @* { $ != null }", "list<integer?>[int[123],int[456],int[789]]")
        chk("data() @* { $ != null } ( $ )", "list<integer>[int[123],int[456],int[789]]")
        chk("data() @* {} ( if ($ != null) _type_of($) else '?' )",
                "list<text>[text[integer],text[?],text[integer],text[?],text[integer]]")
        chk("data() @* {} ( if ($ != null) $ * $ else 0 )", "list<integer>[int[15129],int[0],int[207936],int[0],int[622521]]")
    }

    @Test fun testOperationCall() {
        tst.testLib = true
        initLib()
        def("operation op(x: integer) {}")
        chkEx("{ val x = intz(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(123); op(x); return _type_of(x); }", "ct_err:expr_call_argtype:[op]:0:x:integer:integer?")
        chkEx("{ val x = intz(123); op(x!!); return _type_of(x); }", "integer")
    }

    @Test fun testEntityAttr() {
        initEntity()
        def("function f(x: integer) = user @ { .score >= x };")
        chkEx("{ val x = intz(10); print(f(x?:0).name); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(10); print(f(x!!).name); return _type_of(x); }", "integer")
    }

    @Test fun testEntityToStruct() {
        initEntity()
        def("function f(x: integer) = user @ { .score >= x };")
        chkEx("{ val x = intz(10); print(f(x?:0).to_struct()); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(10); print(f(x!!).to_struct()); return _type_of(x); }", "integer")
    }

    @Test fun testEntityCreate() {
        initEntity()
        val base = "val x = intz(10);"

        chkOpOut("$base create user(name = 'Alice', score = x?:0); print(_type_of(x));", "integer?")
        chkOpOut("$base create user(name = 'Alice', score = x!!); print(_type_of(x));", "integer")
        chkOpOut("$base create user('Alice', x?:0); print(_type_of(x));", "integer?")
        chkOpOut("$base create user('Alice', x!!); print(_type_of(x));", "integer")

        chkOpOut("$base create user(struct<user>(name = 'Alice', score = x?:0)); print(_type_of(x));", "integer?")
        chkOpOut("$base create user(struct<user>(name = 'Alice', score = x!!)); print(_type_of(x));", "integer")
        chkOpOut("$base create user(struct<user>('Alice', x?:0)); print(_type_of(x));", "integer?")
        chkOpOut("$base create user(struct<user>('Alice', x!!)); print(_type_of(x));", "integer")
    }

    @Test fun testEnumAttr() {
        initLib()
        def("enum colors { red, green, blue }")
        def("function f(x: integer) = colors.value(x);")

        chkEx("{ val x = intz(1); print(f(x?:0).value); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(1); print(f(x?:0).name); return _type_of(x); }", "integer?")
        chkEx("{ val x = intz(1); print(f(x!!).value); return _type_of(x); }", "integer")
        chkEx("{ val x = intz(1); print(f(x!!).name); return _type_of(x); }", "integer")
    }

    @Test fun testStructConstructor() {
        initLib()
        def("struct s { a: integer; }")

        val base = "val x = intz(10);"
        chkEx("{ $base print(s(a = x?:0)); return _type_of(x); }", "integer?")
        chkEx("{ $base print(s(a = x!!)); return _type_of(x); }", "integer")
        chkEx("{ $base print(s(x?:0)); return _type_of(x); }", "integer?")
        chkEx("{ $base print(s(x!!)); return _type_of(x); }", "integer")
    }

    @Test fun testIncDecExpr() {
        initLib()
        def("struct s { mutable a: integer; }")

        val base = "val x = intz(10);"
        chkEx("{ $base print(s(a = x?:0).a++); return _type_of(x); }", "integer?")
        chkEx("{ $base print(s(a = x!!).a++); return _type_of(x); }", "integer")
        chkEx("{ $base print(++s(a = x?:0).a); return _type_of(x); }", "integer?")
        chkEx("{ $base print(++s(a = x!!).a); return _type_of(x); }", "integer")
    }

    @Test fun testContradictoryState() {
        initLib()

        val ret = "return _type_of(x);"
        chkEx("{ val x = intz(1); if (x != null) { _test.fake_assert(x == null); $ret } return ''; }", "integer?")
        chkEx("{ val x = intz(1); if (x!! > 0) { $ret } return ''; }", "integer")
        chkEx("{ val x = intz(1); if (x!! > 0) { _test.fake_assert(x == null); $ret } return ''; }", "integer?")
        chkEx("{ val x = intz(1); _test.fake_assert(x!! > 0 and x == null); $ret }", "integer?")
    }

    @Test fun testBugConditionalAssignmentInLoop() {
        initLib()

        val code = """
            function test_bug() {
                var t: integer? = null;
                for (x in [1,2,3]) {
                    if (x != 0) t = x;
                }
                return t??;
            }
        """
        chkCompile(code, "OK")
        chkWarn()

        chkEx("{ var x: integer? = null; for (t in [1,2,3]) { print(t); } return x == null; }", "true")
        chkWarn("expr:smartnull:var:always:[x]")
        chkEx("{ var x: integer? = null; for (t in [1,2,3]) { x = t; } return x == null; }", "false")
        chkWarn()
        chkEx("{ var x: integer? = null; for (t in [1,2,3]) { if (bool(1)) x = t; } return x == null; }", "false")
        chkWarn()
        chkEx("{ var x: integer? = null; for (t in [1,2,3]) { x = null; } return x == null; }", "true")
        chkWarn("expr:smartnull:var:always:[x]")
        chkEx("{ var x: integer? = null; for (t in [1,2,3]) { if (bool(1)) x = null; } return x == null; }", "true")
        chkWarn("expr:smartnull:var:always:[x]")
    }

    private fun initLib() {
        tst.strictToString = false
        def("function intz(x: integer?): integer? = x;")
        def("function bool(x: integer): boolean = x != 0;")
    }

    private fun chkType(code: String, exp: String) {
        chkEx("{ $code return _type_of(x); }", exp)
    }
}
