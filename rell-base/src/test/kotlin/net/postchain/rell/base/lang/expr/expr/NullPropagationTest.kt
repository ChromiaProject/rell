/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.iff
import kotlin.test.Test

class NullPropagationTest: BaseRellTest() {
    @Test fun testExprUnary() {
        tst.strictToString = false
        chkExprUnary("+x!!", "integer", "_nullable(123)")
        chkExprUnary("-x!!", "integer", "_nullable(123)")
        chkExprUnary("not x!!", "boolean", "_nullable(true)")
    }

    private fun chkExprUnary(expr: String, type: String, init: String) {
        chkEx("{ var x = $init; return _type_of(x); }", "$type?")
        chkEx("{ var x = $init; val t = $expr; return _type_of(x); }", type)
    }

    @Test fun testExprUnaryIncDec() {
        tst.strictToString = false
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); return _type_of(x); }", "integer?")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[x!!]++; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[x!!]--; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); ++a[x!!]; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); --a[x!!]; return _type_of(x); }", "integer")
    }

    @Test fun testExprUnaryNotNull() {
        tst.strictToString = false
        def("function f(x: integer): integer? = x;")
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = f(x!!)!!; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = f(require(x))!!; return _type_of(x); }", "integer")
    }

    @Test fun testExprUnaryIsNull() {
        tst.strictToString = false
        def("function f(x: integer): integer? = x;")
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = f(x!!)??; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = f(require(x))??; return _type_of(x); }", "integer")
    }

    @Test fun testExprBinary() {
        tst.strictToString = false
        def("struct rec { a: integer; }")

        chkExprBinaryInt("==")
        chkExprBinaryInt("!=")
        chkExprBinaryInt("<")
        chkExprBinaryInt(">")
        chkExprBinaryInt("<=")
        chkExprBinaryInt(">=")

        chkExprBinaryInt("+")
        chkExprBinaryInt("-")
        chkExprBinaryInt("*")
        chkExprBinaryInt("/")
        chkExprBinaryInt("%")

        chkExprBinary("===", "rec", "_nullable(rec(123))", "rec(456)")
        chkExprBinary("!==", "rec", "_nullable(rec(123))", "rec(456)")

        chkExprBinary("+", "integer", "_nullable_int(123)", "'Hello'")
    }

    private fun chkExprBinaryInt(op: String) {
        val init = "_nullable_int(123)"
        chkExprBinary(op, "integer", init, "456")

        //TODO make this work
        val err = "ct_err:binop_operand_type:+:[integer?]:[integer]"
        chkEx("{ val x = $init; val t = x!! $op (x+1); return _type_of(x); }", err)
        chkEx("{ val x = $init; val t = (x+1) $op x!!; return _type_of(x); }", err)
    }

    private fun chkExprBinary(op: String, type: String, init: String, operand: String) {
        chkEx("{ val x = $init; return _type_of(x); }", "$type?")
        chkEx("{ val x = $init; val t = x!! $op $operand; return _type_of(x); }", type)
        chkEx("{ val x = $init; val t = $operand $op x!!; return _type_of(x); }", type)
    }

    @Test fun testExprBinaryAndOr() {
        tst.strictToString = false
        def("function f(x: boolean, y: boolean): (boolean?, boolean?) = (x, y);")

        chkEx("{ val (x, y) = f(true, true); return _type_of(x); }", "boolean?")
        chkEx("{ val (x, y) = f(true, true); return _type_of(y); }", "boolean?")

        chkEx("{ val (x, y) = f(true, true); if (x!! and y!!) return _type_of(x); return ''; }", "boolean")
        chkEx("{ val (x, y) = f(true, true); if (x!! and y!!) return _type_of(y); return ''; }", "boolean")

        chkEx("{ val (x, y) = f(true, false); if (x!! and y!!) return ''; return _type_of(x); }", "boolean")
        chkEx("{ val (x, y) = f(false, true); if (x!! and y!!) return ''; return _type_of(x); }", "boolean")
        chkEx("{ val (x, y) = f(false, false); if (x!! and y!!) return ''; return _type_of(x); }", "boolean")
        chkEx("{ val (x, y) = f(true, false); if (x!! and y!!) return ''; return _type_of(y); }", "boolean?")
        chkEx("{ val (x, y) = f(false, true); if (x!! and y!!) return ''; return _type_of(y); }", "boolean?")
        chkEx("{ val (x, y) = f(false, false); if (x!! and y!!) return ''; return _type_of(y); }", "boolean?")

        chkEx("{ val (x, y) = f(true, true); if (x!! or y!!) return _type_of(x); return ''; }", "boolean")
        chkEx("{ val (x, y) = f(true, false); if (x!! or y!!) return _type_of(x); return ''; }", "boolean")
        chkEx("{ val (x, y) = f(false, true); if (x!! or y!!) return _type_of(x); return ''; }", "boolean")
        chkEx("{ val (x, y) = f(true, true); if (x!! or y!!) return _type_of(y); return ''; }", "boolean?")
        chkEx("{ val (x, y) = f(true, false); if (x!! or y!!) return _type_of(y); return ''; }", "boolean?")
        chkEx("{ val (x, y) = f(false, true); if (x!! or y!!) return _type_of(y); return ''; }", "boolean?")

        chkEx("{ val (x, y) = f(false, false); if (x!! or y!!) return ''; return _type_of(x); }", "boolean")
        chkEx("{ val (x, y) = f(false, false); if (x!! or y!!) return ''; return _type_of(y); }", "boolean")
    }

    @Test fun testExprBinaryIn() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = x!! in [456]; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable([123]); return _type_of(x); }", "list<integer>?")
        chkEx("{ val x = _nullable([123]); val t = 456 in x!!; return _type_of(x); }", "list<integer>")
    }

    @Test fun testExprBinaryElvis() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = _nullable(x!!) ?: 456; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = _nullable_int(456) ?: x!!; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = _nullable_int(null) ?: x!!; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val y: integer? = 456; val t = y ?: x!!; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val y: integer? = null; val t = y ?: x!!; return _type_of(x); }", "integer?")
    }

    @Test fun testExprSubscript() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(3); return _type_of(x); }", "integer?")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); val t = a[x!!]; return _type_of(x); }", "integer")
        chkEx("{ val a = [[0],[1],[2,3,4,5]]; val x = _nullable(2); val t = a[x!!][3]; return _type_of(x); }", "integer")
        chkEx("{ val a = [[0],[1],[2,3,4,5]]; val x = _nullable(2); val t = a[2][x!!]; return _type_of(x); }", "integer")
    }

    @Test fun testExprTuple() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = (x!!); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = (x!!, 'Hello'); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = ('Hello', x!!); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = (a = x!!, b = 'Hello'); return _type_of(x); }", "integer")
    }

    @Test fun testExprCollection() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = [x!!]; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = [x!! : 'Hello']; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = ['Hello' : x!!]; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = list([x!!]); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = set([x!!]); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = map([x!! : 'Hello']); return _type_of(x); }", "integer")
    }

    @Test fun testExprMember() {
        tst.strictToString = false
        def("struct rec { a: integer; }")
        def("function f(r: rec?): rec? = r;")
        chkEx("{ val x = _nullable(rec(123)); return _type_of(x); }", "rec?")
        chkEx("{ val x = _nullable(rec(123)); val t = (x!!).a; return _type_of(x); }", "rec")
        chkEx("{ val x = _nullable(rec(123)); val y: rec? = null; val t = f(x!!)?.a; return _type_of(x); }", "rec")
    }

    @Test fun testExprCall() {
        tst.strictToString = false
        def("function f(a: integer, b: integer, c: integer): integer = a * b * c;")

        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = f(x!!, 1, 2); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = f(1, x!!, 2); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = f(1, 2, x!!); return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = (x!!).to_hex(); return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = abs(x!!); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); print(x!!); return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); require(_nullable(456 + x!!)); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); require(_nullable(456), x!!.to_hex()); return _type_of(x); }", "integer?")

        chkEx("{ val x = _nullable(123); _nullable(x!!); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); _nullable_int(x!!); return _type_of(x); }", "integer")
    }

    @Test fun testExprStructConstructor() {
        tst.strictToString = false
        def("struct rec { a: integer; }")
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = rec(x!!); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = rec(a = x!!); return _type_of(x); }", "integer")
    }

    @Test fun testExprIf() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(true); return _type_of(x); }", "boolean?")
        chkEx("{ val x = _nullable(true); val t = if (x!!) 1 else 2; return _type_of(x); }", "boolean")
        chkEx("{ val x = _nullable(123); val t = if (5 > 0) x!! else 2; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = if (5 > 0) 1 else x!!; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = if (5 > 0) x!! + 1 else x!! * 3; return _type_of(x); }", "integer")
    }

    @Test fun testExprWhen() {
        tst.strictToString = false
        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = when(x!!) { 123 -> 1; else -> 2; }; return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = when(abs(5)) { 123 -> x!!; else -> 2; }; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = when(abs(5)) { 123 -> 1; else -> x!!; }; return _type_of(x); }", "integer?")
        //chkEx("{ val x = _nullable(123); val t = when(abs(5)) { x!! -> 1; else -> 2; }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = when(abs(5)) { 123->1; x!!->2; else->3; }; return _type_of(x); }", "integer?")

        chkEx("{ val x = _nullable(123); val t = when { 5 > 0 -> x!!; else -> 2 }; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = when { 5 > 0 -> 1; else -> x!! }; return _type_of(x); }", "integer?")
        //chkEx("{ val x = _nullable(123); val t = when { x!! > 0 -> 1; else -> 2; }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = when { 5>0 -> 1; x!!>0 -> 2; else->3 }; return _type_of(x); }", "integer?")
    }

    @Test fun testExprCreate() {
        tstCtx.useSql = true
        def("entity user { name; score: integer; }")

        chkOp("val x = _nullable(123); print(_type_of(x));")
        chkOut("integer?")

        chkOp("val x = _nullable(123); create user('Bob', x!!); print(_type_of(x));")
        chkOut("integer")

        chkOp("val x = _nullable(123); create user('Alice', score = x!!); print(_type_of(x));")
        chkOut("integer")
    }

    @Test fun testExprAt() {
        tst.strictToString = false
        tstCtx.useSql = true
        def("entity user { name; score: integer; }")

        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = user @* { x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* { .score == x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* { .score * 5 > x!! - 10 }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* { 'Bob', x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* { .name == 'Bob', x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* { .name == 'Bob', .score == x!! }; return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = user @* {} ( x!! ); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* {} ( .score, x!! ); return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = user @* {} limit x!!; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = user @* {} offset x!!; return _type_of(x); }", "integer")
    }

    @Test fun testExprAtCollection() {
        tst.strictToString = false
        def("struct user { name; score: integer; }")
        def("function from() = [user('Bob', 123), user('Alice', 456)];")

        chkEx("{ val x = _nullable(123); return _type_of(x); }", "integer?")
        //chkEx("{ val x = _nullable(123); val t = from() @* { x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = from() @* { .score == x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = from() @* { .score * 5 > x!! - 10 }; return _type_of(x); }", "integer")
        //chkEx("{ val x = _nullable(123); val t = from() @* { 'Bob', x!! }; return _type_of(x); }", "integer")
        //chkEx("{ val x = _nullable(123); val t = from() @* { .name == 'Bob', x!! }; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = from() @* { .name == 'Bob', $.score == x!! }; return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = from() @* {} ( x!! ); return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = from() @* {} ( $.score, x!! ); return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = from() @* {} limit x!!; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = from() @* {} offset x!!; return _type_of(x); }", "integer")

        chkEx("{ val x = _nullable(123); val t = [x] @* {}; return _type_of(x); }", "integer?")
        chkEx("{ val x = _nullable(123); val t = [x!!] @* {}; return _type_of(x); }", "integer")
        chkEx("{ val x = _nullable(123); val t = (a:[x!!]) @* {}; return _type_of(x); }", "integer")
    }

    @Test fun testStmtAssignment() {
        tst.strictToString = false
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); return _type_of(x); }", "integer?")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[x!!] = 456; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[x!!] += 456; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[x!!] *= 456; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[0] = a[x!!]; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[0] += a[x!!]; return _type_of(x); }", "integer")
        chkEx("{ val a = [1,2,3,4,5]; val x = _nullable(3); a[0] *= a[x!!]; return _type_of(x); }", "integer")

        chkEx("{ val a = ['@']; val x = _nullable(0); a[0] = _type_of(x); return a; }", "[integer?]")
        chkEx("{ val a = ['@']; val x = _nullable(0); a[0] += _type_of(x); return a; }", "[@integer?]")
        chkEx("{ val a = ['@']; val x = _nullable(0); a[x!!] = _type_of(x); return a; }", "[integer]")
        chkEx("{ val a = ['@']; val x = _nullable(0); a[x!!] += _type_of(x); return a; }", "[@integer]")
        chkEx("{ val a = ['@']; val x = _nullable(0); a[x!!] = '' + (x + 1); return a; }", "[1]")
        chkEx("{ val a = ['@']; val x = _nullable(0); a[x!!] += '' + (x + 1); return a; }", "[@1]")
    }

    @Test fun testStmtWhile() {
        tst.strictToString = false
        chkEx("{ var x = _nullable(true); return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); while (true){ return _type_of(x); } return ''; }", "boolean?")
        chkEx("{ var x = _nullable(true); while (x!!){ return _type_of(x); } return ''; }", "boolean")
        chkEx("{ var x = _nullable(true); while (x!!){ x = true; return _type_of(x); } return ''; }", "boolean")
        chkEx("{ var x = _nullable(true); while (x!!){ x = null; return _type_of(x); } return ''; }", "boolean?")
        chkEx("{ var x = _nullable(true); while (x!!){ x = _nullable(true); return _type_of(x); } return ''; }", "boolean?")
        chkEx("{ var x = _nullable(false); while (x!!){} return _type_of(x); }", "boolean")
        chkEx("{ var x = _nullable(true); while (x!!){ x = _nullable(false); } return _type_of(x); }", "boolean?")
        chkEx("{ val x = _nullable_int(null); while (x != null) {} return _type_of(x); }", "integer?")

        chkVer("0.14.0", true) {
            chkEx("{ var x = _nullable(false); while (x!!){ x = true; } return _type_of(x); }",
                it.iff("boolean?", "boolean"))
        }
    }

    @Test fun testStmtFor() {
        tst.strictToString = false
        def("function i(x: integer?): integer? = x;")

        chkEx("{ var x = i(123); return _type_of(x); }", "integer?")
        chkEx("{ var x = i(123); for (y in [456]) { return _type_of(x); } return ''; }", "integer?")
        chkEx("{ var x = i(123); for (y in [x]) { return _type_of(x); } return ''; }", "integer?")
        chkEx("{ var x = i(123); for (y in [x!!]) { return _type_of(x); } return ''; }", "integer")
        chkEx("{ var x = i(123); for (y in [x]) {} return _type_of(x); }", "integer?")
        chkEx("{ var x = i(123); for (y in [x!!]) {} return _type_of(x); }", "integer")
        chkEx("{ var x = i(123); for (y in [x!!]) { x = i(456); } return _type_of(x); }", "integer?")
        chkEx("{ var x = i(123); for (y in [x!!]) { x = 456; } return _type_of(x); }", "integer")
        chkEx("{ var x = i(123); for (y in [x!!]) { x = 456; } return _type_of(x); }", "integer")

        val xyz = "var x = i(1); var y = i(2); var z = i(3);"
        chkEx("{ $xyz x!!; y!!; for (j in [0]) {} return _type_of(x); }", "integer")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) {} return _type_of(y); }", "integer")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) {} return _type_of(z); }", "integer?")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) { x = y; } return _type_of(x); }", "integer")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) { x = y; y = x; } return _type_of(x); }", "integer?")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) { x = y; y = z; } return _type_of(x); }", "integer?")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) { y = x; x = y; } return _type_of(y); }", "integer?")
        chkEx("{ $xyz x!!; y!!; for (j in [0]) { z = x; x = y; } return _type_of(z); }", "integer?")
    }

    @Test fun testStmtIf() {
        tst.strictToString = false
        chkEx("{ var x = _nullable(true); return _type_of(x); }", "boolean?")

        chkEx("{ var x = _nullable(true); if (x!!){} return _type_of(x); }", "boolean")
        chkEx("{ var x = _nullable(true); if (x!!){ x = true; } return _type_of(x); }", "boolean")
        chkEx("{ var x = _nullable(true); if (x!!){} else { x = false; } return _type_of(x); }", "boolean")
        chkEx("{ var x = _nullable(true); if (x!!){ x = null; } return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); if (x!!){} else { x = null; } return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); if (x!!){ x = _nullable(true); } return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); if (x!!){} else { x = _nullable(true); } return _type_of(x); }", "boolean?")

        chkEx("{ var x = _nullable(true); if (1 > 0) { x = true; } return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); if (1 > 0) {} else { x = true; } return _type_of(x); }", "boolean?")
        chkEx("{ var x = _nullable(true); if (1 > 0) { x = true; } else { x = false; } return _type_of(x); }", "boolean")
    }

    @Test fun testStmtWhen() {
        tst.strictToString = false
        chkEx("{ var x = _nullable(123); return _type_of(x); }", "integer?")

        chkEx("{ var x = _nullable(123); when (x!!) { 123 -> {} } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); when (x!!) { 123 -> x = 456; } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); when (x!!) { else -> x = 456; } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); when (x!!) { 123 -> x = _nullable(456); } return _type_of(x); }", "integer?")
        chkEx("{ var x = _nullable(123); when (x!!) { else -> x = _nullable(456); } return _type_of(x); }", "integer?")

        chkEx("{ var x = _nullable(123); x!!; when (x) { 123 -> {} } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); x!!; when (x) { 123 -> x = 456; } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); x!!; when (x) { else -> x = 456; } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); x!!; when (x) { 123 -> x = _nullable(456); } return _type_of(x); }", "integer?")
        chkEx("{ var x = _nullable(123); x!!; when (x) { else -> x = _nullable(456); } return _type_of(x); }", "integer?")

        chkEx("{ var x = _nullable(123); when (x!!) { else -> x = null; } return x != null; }", "false")
        chkWarn("expr:smartnull:var:always:[x]")

        chkEx("{ var x = _nullable(123); when { 1>0-> x = 456; } return _type_of(x); }", "integer?")
        chkEx("{ var x = _nullable(123); when { 1>0-> {} else-> x = 456; } return _type_of(x); }", "integer?")
        chkEx("{ var x = _nullable(123); when { 1>0-> x = 456; else-> x = 789; } return _type_of(x); }", "integer")
        chkEx("{ var x = _nullable(123); when { 1>0-> x = _nullable(456); else-> x = 789; } return _type_of(x); }", "integer?")
        chkEx("{ var x = _nullable(123); when { 1>0-> x = 456; else-> x = _nullable(789); } return _type_of(x); }", "integer?")
    }

    @Test fun testStmtUpdate() {
        tstCtx.useSql = true
        def("entity user { name; mutable score: integer; }")

        chkOp("{ val x = _nullable(123); print(_type_of(x)); }")
        chkOut("integer?")

        chkOp("{ val x = _nullable(123); update user @* { x!! } ( score = 0 ); print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); update user @* { .score * 5 > x!! - 10 } ( score = 0 ); print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); update user @* {} ( score = x!! ); print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); update user @* {} ( score += x!! ); print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); val u = user @? {}; update u ( score = x!! ); print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); update (user @? { x!! }) ( score = 0 ); print(_type_of(x)); }")
        chkOut("integer")
    }

    @Test fun testStmtDelete() {
        tstCtx.useSql = true
        def("entity user { name; mutable score: integer; }")

        chkOp("{ val x = _nullable(123); print(_type_of(x)); }")
        chkOut("integer?")

        chkOp("{ val x = _nullable(123); delete user @* { x!! }; print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); delete user @* { .score * 5 > x!! - 10 }; print(_type_of(x)); }")
        chkOut("integer")

        chkOp("{ val x = _nullable(123); delete (user @? { x!! }); print(_type_of(x)); }")
        chkOut("integer")
    }
}
