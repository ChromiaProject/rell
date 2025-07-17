/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class LibRellTestAssertTest: BaseRellTest() {
    init {
        tst.testLib = true
    }

    @Test fun testAssertEqualsBasic() {
        chkAssertEquals("0", "1", "int[0]", "int[1]")
        chkAssertEquals("'Hello'", "'World'", "text[Hello]", "text[World]")
        chkAssertEquals("true", "false", "boolean[true]", "boolean[false]")
        chkAssertEquals("12.34", "56.78", "dec[12.34]", "dec[56.78]")
        chkAssertEquals("x'beef'", "x'feed'", "byte_array[beef]", "byte_array[feed]")
        chkAssertEquals("json('[1,2,3]')", "json('[4,5,6]')", "json[[1,2,3]]", "json[[4,5,6]]")
        chkAssertEquals("range(5)", "range(6)", "range[0,5,1]", "range[0,6,1]")
        chkAssertEquals("\"ABCDEF\".to_gtv()", "x\"ABCDEF\".to_gtv()", "gtv[\"ABCDEF\"]", "gtv[x\"ABCDEF\"]")
    }

    @Test fun testAssertEqualsComplex() {
        def("struct rec { x: integer; }")

        chkAssertEquals("(1,'a')", "(1,'b')", "(int[1],text[a])", "(int[1],text[b])")
        chkAssertEquals("(1,'a')", "(2,'a')", "(int[1],text[a])", "(int[2],text[a])")
        chkAssertEquals("rec(123)", "rec(456)", "rec[x=int[123]]", "rec[x=int[456]]")

        chkAssertEqualsBad("(1,'a')", "('a',1)", "(integer,text)", "(text,integer)")
        chkAssertEqualsBad("(1,'a')", "(1,2)", "(integer,text)", "(integer,integer)")
        chkAssertEqualsBad("(1,'a')", "(1,'a',2)", "(integer,text)", "(integer,text,integer)")
        chkAssertEqualsBad("(x=1,y='a')", "(x=1,z='a')", "(x:integer,y:text)", "(x:integer,z:text)")

        chkAssertEquals("(x\"AB\".to_gtv(),)", "(x\"CE\".to_gtv(),)", "(gtv[x\"AB\"])", "(gtv[x\"CE\"])")
        chkAssertEquals("[x\"AB\".to_gtv(), x\"CD\".to_gtv()]", "[\"AB\".to_gtv(), \"CD\".to_gtv()]", "list<gtv>[gtv[x\"AB\"],gtv[x\"CD\"]]", "list<gtv>[gtv[\"AB\"],gtv[\"CD\"]]")
    }

    @Test fun testAssertEqualsCollections() {
        chkAssertEquals("[1,2,3]", "[4,5,6]", "list<integer>[int[1],int[2],int[3]]", "list<integer>[int[4],int[5],int[6]]")
        chkAssertEqualsBad("[1,2,3]", "['a','b','c']", "list<integer>", "list<text>")

        chkAssertEquals("set([1,2,3])", "set([4,5,6])", "set<integer>[int[1],int[2],int[3]]", "set<integer>[int[4],int[5],int[6]]")
        chkAssertEqualsBad("set([1,2,3])", "set(['a','b','c'])", "set<integer>", "set<text>")
        chkAssertEqualsBad("set([1,2,3])", "[1,2,3]", "set<integer>", "list<integer>")

        chkAssertEquals("[1:'a']", "[1:'b']", "map<integer,text>[int[1]=text[a]]", "map<integer,text>[int[1]=text[b]]")
        chkAssertEqualsBad("[1:'a']", "['a':1]", "map<integer,text>", "map<text,integer>")
    }

    @Test fun testAssertEqualsSpecial() {
        def("struct rec { x: integer; }")

        chkAssertEquals("_nullable_int(123)", "456", "int[123]", "int[456]")
        chkAssertEquals("_nullable_int(123)", "_nullable_int(456)", "int[123]", "int[456]")
        chkAssertEquals("_nullable_int(123)", "null", "int[123]", "null")
        chkAssertEquals("123", "_nullable_int(456)", "int[123]", "int[456]")
        chkAssertEquals("null", "_nullable_int(123)", "null", "int[123]")

        chkAssertEquals("_nullable(rec(123))", "rec(456)", "rec[x=int[123]]", "rec[x=int[456]]")
        chkAssertEquals("rec(123)", "_nullable(rec(456))", "rec[x=int[123]]", "rec[x=int[456]]")
        chkAssertEquals("_nullable(rec(123))", "null", "rec[x=int[123]]", "null")
        chkAssertEquals("null", "_nullable(rec(123))", "null", "rec[x=int[123]]")
    }

    @Test fun testAssertEqualsNumericPromotions() {
        chkAssert("assert_equals(123.0, 456)", "asrt_err:assert_equals:dec[123]:dec[456]")
        chkAssert("assert_equals(123, 456.0)", "asrt_err:assert_equals:dec[123]:dec[456]")
        chkAssert("assert_equals(123.0, 123)", "int[0]")
        chkAssert("assert_equals(123, 123.0)", "int[0]")

        chkAssert("assert_not_equals(123.0, 456)", "int[0]")
        chkAssert("assert_not_equals(123, 456.0)", "int[0]")
        chkAssert("assert_not_equals(123.0, 123)", "asrt_err:assert_not_equals:dec[123]")
        chkAssert("assert_not_equals(123, 123.0)", "asrt_err:assert_not_equals:dec[123]")

        chkAssert("assert_equals(_nullable_int(123), 123.0)", "int[0]")
        chkAssert("assert_equals(123.0, _nullable_int(123))", "int[0]")
        chkAssert("assert_equals(_nullable_int(123), 456.0)", "asrt_err:assert_equals:dec[123]:dec[456]")
        chkAssert("assert_equals(123.0, _nullable_int(456))", "asrt_err:assert_equals:dec[123]:dec[456]")

        chkAssert("assert_equals(_nullable(123.0), 123)", "int[0]")
        chkAssert("assert_equals(123, _nullable(123.0))", "int[0]")
        chkAssert("assert_equals(_nullable(123.0), 456)", "asrt_err:assert_equals:dec[123]:dec[456]")
        chkAssert("assert_equals(123, _nullable(456.0))", "asrt_err:assert_equals:dec[123]:dec[456]")
    }

    @Test fun testAssertEqualsBadArgs() {
        chkAssertEqualsBadArgs("assert_equals", "expected")
        chkAssertEqualsBadArgs("assert_not_equals", "illegal")
    }

    private fun chkAssertEqualsBadArgs(fn: String, arg2: String) {
        chkAssert("$fn()", "ct_err:expr:call:missing_args:[FN]:[0:actual,1:$arg2]")
        chkAssert("$fn(0)", "ct_err:expr:call:missing_args:[FN]:[1:$arg2]")
        chkAssert("$fn(0, 1, 'Hello')", "ct_err:expr:call:too_many_args:[FN]:2:3")
        chkAssert("$fn(0, 1, 2)", "ct_err:expr:call:too_many_args:[FN]:2:3")
        chkAssert("$fn(123, 'Hello')", "ct_err:expr_call_badargs:[FN]:[integer,text]")
    }

    private fun chkAssertEquals(e1: String, e2: String, v1: String, v2: String) {
        chkAssert("assert_equals($e1, $e2)", "asrt_err:assert_equals:$v1:$v2")
        chkAssert("assert_equals($e1, $e1)", "int[0]")
        chkAssert("assert_equals($e2, $e2)", "int[0]")

        chkAssert("assert_not_equals($e1, $e2)", "int[0]")
        chkAssert("assert_not_equals($e1, $e1)", "asrt_err:assert_not_equals:$v1")
        chkAssert("assert_not_equals($e2, $e2)", "asrt_err:assert_not_equals:$v2")
    }

    private fun chkAssertEqualsBad(e1: String, e2: String, t1: String, t2: String) {
        chkAssert("assert_equals($e1, $e2)", "ct_err:expr_call_badargs:[FN]:[$t1,$t2]")
        chkAssert("assert_not_equals($e1, $e2)", "ct_err:expr_call_badargs:[FN]:[$t1,$t2]")
    }

    @Test fun testAssertTrueFalse() {
        chkAssert("assert_true(true)", "int[0]")
        chkAssert("assert_true(false)", "asrt_err:assert_boolean:true")
        chkAssert("assert_false(true)", "asrt_err:assert_boolean:false")
        chkAssert("assert_false(false)", "int[0]")

        chkAssert("assert_true(123)", "ct_err:expr_call_badargs:[FN]:[integer]")
        chkAssert("assert_false(123)", "ct_err:expr_call_badargs:[FN]:[integer]")
        chkAssert("assert_true(_nullable(true))", "ct_err:expr_call_badargs:[FN]:[boolean?]")
        chkAssert("assert_false(_nullable(true))", "ct_err:expr_call_badargs:[FN]:[boolean?]")
    }

    @Test fun testAssertNull() {
        chkAssert("assert_null(_nullable_int(123))", "asrt_err:assert_null:int[123]")
        chkAssert("assert_null(_nullable_int(null))", "int[0]")
        chkAssert("assert_null(123)", "ct_err:expr_call_badargs:[FN]:[integer]")
        chkAssert("assert_null(null)", "ct_err:expr_call_badargs:[FN]:[null]")

        chkAssert("assert_not_null(_nullable_int(123))", "int[0]")
        chkAssert("assert_not_null(_nullable_int(null))", "asrt_err:assert_not_null")
        chkAssert("assert_not_null(123)", "ct_err:expr_call_badargs:[FN]:[integer]")
        chkAssert("assert_not_null(null)", "ct_err:expr_call_badargs:[FN]:[null]")
    }

    @Test fun testAssertNullNullabilityAnalysis() {
        chkEx("{ val x = _nullable_int(123); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = _nullable_int(null); return _type_of(x); }", "text[integer?]")
        chkEx("{ val x = _nullable_int(123); assert_not_null(x); return _type_of(x); }", "text[integer]")
        chkEx("{ val x = _nullable_int(null); assert_null(x); return _type_of(x); }", "text[integer?]")
    }

    @Test fun testAssertCompare() {
        chkAssertCompare("1 2 3", "int[1] int[2] int[3]")
        chkAssertCompare("1.2 3.4 5.6", "dec[1.2] dec[3.4] dec[5.6]")
        chkAssertCompare("'a' 'b' 'c'", "text[a] text[b] text[c]")
        chkAssertCompare("x'a1' x'b2' x'c3'", "byte_array[a1] byte_array[b2] byte_array[c3]")
    }

    private fun chkAssertCompare(exprs: String, values: String) {
        chkAssertCompare("assert_lt", CmpOp.LT, exprs, values)
        chkAssertCompare("assert_gt", CmpOp.GT, exprs, values)
        chkAssertCompare("assert_le", CmpOp.LE, exprs, values)
        chkAssertCompare("assert_ge", CmpOp.GE, exprs, values)
    }

    private fun chkAssertCompare(fn: String, op: CmpOp, exprsStr: String, valuesStr: String) {
        val exprs = exprsStr.split(" ")
        val values = valuesStr.split(" ")
        chkAssertCompare(fn, op, exprs, values, 0, 1)
        chkAssertCompare(fn, op, exprs, values, 1, 1)
        chkAssertCompare(fn, op, exprs, values, 2, 1)
        chkAssertCompare(fn, op, exprs, values, 1, 0)
        chkAssertCompare(fn, op, exprs, values, 1, 1)
        chkAssertCompare(fn, op, exprs, values, 1, 2)
    }

    private fun chkAssertCompare(fn: String, op: CmpOp, exprs: List<String>, values: List<String>, i: Int, j: Int) {
        val t = op.fn(i.compareTo(j))
        val exp = if (t) "int[0]" else "asrt_err:assert_compare:${op.op}:${values[i]}:${values[j]}"
        chkAssert("$fn(${exprs[i]}, ${exprs[j]})", exp)
    }

    @Test fun testAssertCompareNumericPromotion() {
        chkAssertCompareNP("assert_lt", CmpOp.LT)
        chkAssertCompareNP("assert_gt", CmpOp.GT)
        chkAssertCompareNP("assert_le", CmpOp.LE)
        chkAssertCompareNP("assert_ge", CmpOp.GE)
    }

    private fun chkAssertCompareNP(fn: String, op: CmpOp) {
        chkAssertCompareNP(fn, op, "1", "2.0", -1)
        chkAssertCompareNP(fn, op, "2", "2.0", 0)
        chkAssertCompareNP(fn, op, "3", "2.0", 1)
        chkAssertCompareNP(fn, op, "1.0", "2", -1)
        chkAssertCompareNP(fn, op, "2.0", "2", 0)
        chkAssertCompareNP(fn, op, "3.0", "2", 1)

        chkAssertCompareNP(fn, op, "2.0", "1", 1)
        chkAssertCompareNP(fn, op, "2.0", "2", 0)
        chkAssertCompareNP(fn, op, "2.0", "3", -1)
        chkAssertCompareNP(fn, op, "2", "1.0", 1)
        chkAssertCompareNP(fn, op, "2", "2.0", 0)
        chkAssertCompareNP(fn, op, "2", "3.0", -1)
    }

    private fun chkAssertCompareNP(fn: String, op: CmpOp, a: String, b: String, cmp: Int) {
        val av = "dec[${a.toDouble().toInt()}]"
        val bv = "dec[${b.toDouble().toInt()}]"
        val exp = if (op.fn(cmp)) "int[0]" else "asrt_err:assert_compare:${op.op}:$av:$bv"
        chkAssert("$fn($a, $b)", exp)
    }

    @Test fun testAssertCompareBoolean() {
        chkAssertCmpPair("false,false", "boolean[false]:boolean[false]", 0)
        chkAssertCmpPair("false,true", "boolean[false]:boolean[true]", -1)
        chkAssertCmpPair("true,false", "boolean[true]:boolean[false]", 1)
        chkAssertCmpPair("true,true", "boolean[true]:boolean[true]", 0)
    }

    @Test fun testAssertCompareEnum() {
        def("enum color { red, green, blue }")
        chkAssertCmpPair("color.red,color.red", "color[red]:color[red]", 0)
        chkAssertCmpPair("color.red,color.green", "color[red]:color[green]", -1)
        chkAssertCmpPair("color.red,color.blue", "color[red]:color[blue]", -1)
        chkAssertCmpPair("color.green,color.red", "color[green]:color[red]", 1)
        chkAssertCmpPair("color.green,color.green", "color[green]:color[green]", 0)
        chkAssertCmpPair("color.green,color.blue", "color[green]:color[blue]", -1)
        chkAssertCmpPair("color.blue,color.red", "color[blue]:color[red]", 1)
        chkAssertCmpPair("color.blue,color.green", "color[blue]:color[green]", 1)
        chkAssertCmpPair("color.blue,color.blue", "color[blue]:color[blue]", 0)
    }

    @Test fun testAssertCompareTuple() {
        for (a in listOf(1, 2, 3)) {
            for (b in "ABC") {
                for (c in listOf(1, 2, 3)) {
                    for (d in "ABC") {
                        val cmp = "$a$b".compareTo("$c$d")
                        chkAssertCmpPair("($a,'$b'),($c,'$d')", "(int[$a],text[$b]):(int[$c],text[$d])", cmp)
                    }
                }
            }
        }
    }

    private fun chkAssertCmpPair(args: String, values: String, cmp: Int) {
        chkFn("{ assert_le($args); }", if (cmp <= 0) "unit" else "asrt_err:assert_compare:<=:$values")
        chkFn("{ assert_lt($args); }", if (cmp < 0)  "unit" else "asrt_err:assert_compare:<:$values")
        chkFn("{ assert_gt($args); }", if (cmp > 0)  "unit" else "asrt_err:assert_compare:>:$values")
        chkFn("{ assert_ge($args); }", if (cmp >= 0) "unit" else "asrt_err:assert_compare:>=:$values")
    }

    @Test fun testAssertCompareWrongArgs() {
        chkAssertCmpWrongArgs("123,'Hello'", "integer,text")
        chkAssertCmpWrongArgs("false,123", "boolean,integer")
        chkAssertCmpWrongArgs("gtv.from_json('{}'),gtv.from_json('{}')", "gtv,gtv")
        chkAssertCmpWrongArgs("(1,'A'),0.0", "(integer,text),decimal")
        chkAssertCmpWrongArgs("(123,456.0),(123.0,456)", "(integer,decimal),(decimal,integer)")
    }

    private fun chkAssertCmpWrongArgs(args: String, types: String) {
        chk("assert_lt($args)", "ct_err:expr_call_badargs:[assert_lt]:[$types]")
        chk("assert_gt($args)", "ct_err:expr_call_badargs:[assert_gt]:[$types]")
        chk("assert_le($args)", "ct_err:expr_call_badargs:[assert_le]:[$types]")
        chk("assert_ge($args)", "ct_err:expr_call_badargs:[assert_ge]:[$types]")
    }

    @Test fun testAssertRange() {
        def("enum ord { zero, one, two, three, four }")
        chkAssertRange("assert_gt_lt", CmpOp.GT, CmpOp.LT)
        chkAssertRange("assert_gt_le", CmpOp.GT, CmpOp.LE)
        chkAssertRange("assert_ge_lt", CmpOp.GE, CmpOp.LT)
        chkAssertRange("assert_ge_le", CmpOp.GE, CmpOp.LE)
    }

    private fun chkAssertRange(fn: String, op1: CmpOp, op2: CmpOp) {
        chkAssertRange(fn, op1, op2, "1 2 3 4 5", "int[1] int[2] int[3] int[4] int[5]")
        chkAssertRange(fn, op1, op2, "1.0 2.0 3.0 4.0 5.0", "dec[1] dec[2] dec[3] dec[4] dec[5]")
        chkAssertRange(fn, op1, op2, "'a' 'b' 'c' 'd' 'e'", "text[a] text[b] text[c] text[d] text[e]")
        chkAssertRange(fn, op1, op2, "x'a1' x'b2' x'c3' x'd4' x'e5'",
                "byte_array[a1] byte_array[b2] byte_array[c3] byte_array[d4] byte_array[e5]")
        chkAssertRange(fn, op1, op2, "ord.zero ord.one ord.two ord.three ord.four",
            "ord[zero] ord[one] ord[two] ord[three] ord[four]")
        chkAssertRange(fn, op1, op2, "(1,'a') (1,'b') (2,'a') (3,'c') (3,'d')",
            "(int[1],text[a]) (int[1],text[b]) (int[2],text[a]) (int[3],text[c]) (int[3],text[d])")
    }

    private fun chkAssertRange(fn: String, op1: CmpOp, op2: CmpOp, exprsStr: String, valuesStr: String) {
        val exprs = exprsStr.split(" ")
        val values = valuesStr.split(" ")
        chkAssertRange(fn, op1, op2, exprs, values, 0)
        chkAssertRange(fn, op1, op2, exprs, values, 1)
        chkAssertRange(fn, op1, op2, exprs, values, 2)
        chkAssertRange(fn, op1, op2, exprs, values, 3)
        chkAssertRange(fn, op1, op2, exprs, values, 4)
    }

    private fun chkAssertRange(fn: String, op1: CmpOp, op2: CmpOp, exprs: List<String>, values: List<String>, i: Int) {
        val exp = if (!op1.fn(i.compareTo(1))) {
            "asrt_err:assert_compare:${op1.op}:${values[i]}:${values[1]}"
        } else if (!op2.fn(i.compareTo(3))) {
            "asrt_err:assert_compare:${op2.op}:${values[i]}:${values[3]}"
        } else {
            "int[0]"
        }
        chkAssert("$fn(${exprs[i]}, ${exprs[1]}, ${exprs[3]})", exp)
    }

    @Test fun testAssertRangeNumericPromotion() {
        chkAssertRangeNP("assert_gt_lt", CmpOp.GT, CmpOp.LT)
        chkAssertRangeNP("assert_gt_le", CmpOp.GT, CmpOp.LE)
        chkAssertRangeNP("assert_ge_lt", CmpOp.GE, CmpOp.LT)
        chkAssertRangeNP("assert_ge_le", CmpOp.GE, CmpOp.LE)
    }

    private fun chkAssertRangeNP(fn: String, op1: CmpOp, op2: CmpOp) {
        chkAssertRangeNP(fn, op1, op2, "1.0, 2, 4", 0)
        chkAssertRangeNP(fn, op1, op2, "2.0, 2, 4", 1)
        chkAssertRangeNP(fn, op1, op2, "3.0, 2, 4", 2)
        chkAssertRangeNP(fn, op1, op2, "4.0, 2, 4", 3)
        chkAssertRangeNP(fn, op1, op2, "5.0, 2, 4", 4)

        chkAssertRangeNP(fn, op1, op2, "1, 2.0, 4", 0)
        chkAssertRangeNP(fn, op1, op2, "2, 2.0, 4", 1)
        chkAssertRangeNP(fn, op1, op2, "3, 2.0, 4", 2)
        chkAssertRangeNP(fn, op1, op2, "4, 2.0, 4", 3)
        chkAssertRangeNP(fn, op1, op2, "5, 2.0, 4", 4)

        chkAssertRangeNP(fn, op1, op2, "1, 2, 4.0", 0)
        chkAssertRangeNP(fn, op1, op2, "2, 2, 4.0", 1)
        chkAssertRangeNP(fn, op1, op2, "3, 2, 4.0", 2)
        chkAssertRangeNP(fn, op1, op2, "4, 2, 4.0", 3)
        chkAssertRangeNP(fn, op1, op2, "5, 2, 4.0", 4)
    }

    private fun chkAssertRangeNP(fn: String, op1: CmpOp, op2: CmpOp, argsStr: String, i: Int) {
        val args = argsStr.split(",")
        val vals = args.map { "dec[${it.toDouble().toInt()}]" }
        val exp = if (!op1.fn(i.compareTo(1))) {
            "asrt_err:assert_compare:${op1.op}:${vals[0]}:${vals[1]}"
        } else if (!op2.fn(i.compareTo(3))) {
            "asrt_err:assert_compare:${op2.op}:${vals[0]}:${vals[2]}"
        } else {
            "int[0]"
        }
        chkAssert("$fn($argsStr)", exp)
    }

    @Test fun testAssertFails() {
        chkAssert("assert_fails(integer.from_hex(*))", "ct_err:expr_call_badargs:[FN]:[(text)->integer]")
        chkAssert("assert_fails(123)", "ct_err:expr_call_badargs:[FN]:[integer]")

        chkAssert("assert_fails(integer.from_hex('xyz', *))", "int[0]")
        chkAssert("assert_fails(integer.from_hex('123', *))",
            "asrt_err:assert_fails:no_fail:fn[integer.from_hex(text[123])]")
        chkAssert("assert_fails('foo', integer.from_hex('qwer', *))",
            "asrt_err:assert_fails:mismatch:[foo]:[Invalid hex number: 'qwer']")
        chkAssert("assert_fails(\"Invalid hex number: 'qwer'\", integer.from_hex('qwer', *))", "int[0]")

        chkAssert("assert_fails(json('', *))", "int[0]")
        chkAssert("assert_fails(json('{}', *))", "asrt_err:assert_fails:no_fail:fn[json(text[{}])]")
        chkAssert("assert_fails('Bad JSON: ', json('', *))", "int[0]")

        chkAssert("assert_fails('error', json('', *))", "asrt_err:assert_fails:mismatch:[error]:[Bad JSON: ]")
        chkAssert("assert_fails('Bad ', json('', *))", "int[0]")
        chkAssert("assert_fails('JSON', json('', *))", "int[0]")
        chkAssert("assert_fails('json', json('', *))", "asrt_err:assert_fails:mismatch:[json]:[Bad JSON: ]")
    }

    @Test fun testAssertFailsRequire() {
        def("function req(v: integer) { require(v >= 0, 'negative: %d'.format(v)); }")
        chkAssert("assert_fails(req(123, *))", "asrt_err:assert_fails:no_fail:fn[req(int[123])]")
        chkAssert("assert_fails(req(-123, *))", "int[0]")
        chkAssert("assert_fails('aaa', req(-123, *))", "asrt_err:assert_fails:mismatch:[aaa]:[negative: -123]")
        chkAssert("assert_fails('negative: -123', req(-123, *))", "int[0]")
    }

    @Test fun testAssertFailsAssert() {
        def("function asrt_true(v: integer) { assert_true(v >= 0); }")
        def("function asrt_eq(act: integer, exp: integer) { assert_equals(act, exp); }")

        chkAssert("assert_fails(asrt_true(123, *))", "asrt_err:assert_fails:no_fail:fn[asrt_true(int[123])]")
        chkAssert("assert_fails(asrt_true(-123, *))", "asrt_err:assert_boolean:true")
        chkAssert("assert_fails('aaa', asrt_true(-123, *))", "asrt_err:assert_boolean:true")
        chkAssert("assert_fails('negative: -123', asrt_true(-123, *))", "asrt_err:assert_boolean:true")

        chkAssert("assert_fails(asrt_eq(123, 123, *))", "asrt_err:assert_fails:no_fail:fn[asrt_eq(int[123],int[123])]")
        chkAssert("assert_fails(asrt_eq(123, 456, *))", "asrt_err:assert_equals:int[123]:int[456]")
        chkAssert("assert_fails('aaa', asrt_eq(123, 456, *))", "asrt_err:assert_equals:int[123]:int[456]")
        chkAssert("assert_fails('negative: -123', asrt_eq(123, 456, *))", "asrt_err:assert_equals:int[123]:int[456]")
    }

    @Test fun testAssertFailsResult() {
        val expr = "integer.from_hex('xy', *)"
        chk("_type_of(assert_fails($expr))", "text[rell.test.failure]")
        chk("assert_fails($expr)", "rell.test.failure[Invalid hex number: 'xy']")
        chk("assert_fails($expr).message", "text[Invalid hex number: 'xy']")
        chkEx("{ val f: rell.test.failure; f = assert_fails($expr); return f; }", "rell.test.failure[Invalid hex number: 'xy']")
        chkEx("{ val f: rell.test.failure; f = assert_fails($expr); return f.message; }", "text[Invalid hex number: 'xy']")
    }

    private fun chkAssert(expr: String, expected: String) {
        val fn = expr.substringBefore('(')
        chkEx("{ $expr; return 0; }", expected.replace("FN", fn))
        chkEx("{ rell.test.$expr; return 0; }", expected.replace("FN", "rell.test.$fn"))
    }

    private enum class CmpOp(val op: String, val fn: (Int) -> Boolean) {
        LT("<", { it < 0}),
        GT(">", { it > 0}),
        LE("<=", { it <= 0}),
        GE(">=", { it >= 0}),
    }
}
