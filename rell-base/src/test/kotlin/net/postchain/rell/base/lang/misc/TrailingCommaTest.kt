/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class TrailingCommaTest: BaseRellTest() {
    @Test fun testAnnotation() {
        chkCompile("@extend function f() {}", "ct_err:ann:extend:arg_count:0")
        chkCompile("@extend() function f() {}", "ct_err:ann:extend:arg_count:0")
        chkCompile("@extend(,) function f() {}", "ct_err:syntax")
        chkCompile("@extend(g) function f() {}", "ct_err:unknown_name:g")
        chkCompile("@extend(g,) function f() {}", "ct_err:unknown_name:g")
        chkCompile("@extend(g,h) function f() {}", "ct_err:ann:extend:arg_count:2")
        chkCompile("@extend(g,h,) function f() {}", "ct_err:ann:extend:arg_count:2")
    }

    @Test fun testAnnotationLegacy() {
        chkCompile("entity data () {}", "ct_err:syntax")
        chkCompile("entity data (,) {}", "ct_err:syntax")
        chkCompile("entity data (log) {}", "OK")
        chkCompile("entity data (log,) {}", "OK")
        chkCompile("entity data (log,foo) {}", "ct_err:entity_ann_bad:foo")
        chkCompile("entity data (log,foo,) {}", "ct_err:entity_ann_bad:foo")
    }

    @Test fun testKeyIndex() {
        chkCompile("entity data { x: integer; key; }", "ct_err:syntax")
        chkCompile("entity data { x: integer; key ,; }", "ct_err:syntax")

        chkKeyIndex("a")
        chkKeyIndex("a,")
        chkKeyIndex("a,b,c")
        chkKeyIndex("a,b,c,")

        chkKeyIndex("x: integer")
        chkKeyIndex("x: integer,")
        chkKeyIndex("x: integer, y: integer, z: integer")
        chkKeyIndex("x: integer, y: integer, z: integer,")
    }

    private fun chkKeyIndex(code: String) {
        chkCompile("entity data { a: integer; b: integer; c: integer; key $code; }", "OK")
        chkCompile("entity data { a: integer; b: integer; c: integer; index $code; }", "OK")
    }

    @Test fun testImportExact() {
        file("lib.rell", "module; val X = 123; val Y = 456;")

        chkCompile("import lib.{};", "ct_err:syntax")
        chkCompile("import lib.{,};", "ct_err:syntax")

        chkFull("import lib.{X}; query q() = X;", "int[123]")
        chkFull("import lib.{X,}; query q() = X;", "int[123]")
        chkFull("import lib.{X,Y}; query q() = (X,Y);", "(int[123],int[456])")
        chkFull("import lib.{X,Y,}; query q() = (X,Y);", "(int[123],int[456])")
    }

    @Test fun testEnum() {
        chkCompile("enum foo {}", "OK")
        chkCompile("enum foo {,}", "ct_err:syntax")
        chkCompile("enum foo {A}", "OK")
        chkCompile("enum foo {A,}", "OK")
        chkCompile("enum foo {A,B,C}", "OK")
        chkCompile("enum foo {A,B,C,}", "OK")
    }

    @Test fun testFormalParameters() {
        chkCompile("function f(,) {}", "ct_err:syntax")
        chkCompile("function f(x: integer) {}", "OK")
        chkCompile("function f(x: integer,) {}", "OK")
        chkCompile("function f(x: integer, y: text) {}", "OK")
        chkCompile("function f(x: integer, y: text,) {}", "OK")
    }

    @Test fun testFunctionType() {
        chkCompile("function f(g: () -> integer) {}", "OK")
        chkCompile("function f(g: (,) -> integer) {}", "ct_err:syntax")
        chkCompile("function f(g: (integer) -> integer) {}", "OK")
        chkCompile("function f(g: (integer,) -> integer) {}", "OK")
        chkCompile("function f(g: (integer,text) -> integer) {}", "OK")
        chkCompile("function f(g: (integer,text,) -> integer) {}", "OK")
    }

    @Test fun testGenericType() {
        chkCompile("function f(x: list<>) {}", "ct_err:syntax")
        chkCompile("function f(x: list<,>) {}", "ct_err:syntax")
        chkCompile("function f(x: list<integer>) {}", "OK")
        chkCompile("function f(x: list<integer,>) {}", "OK")
        chkCompile("function f(x: map<integer,text>) {}", "OK")
        chkCompile("function f(x: map<integer,text,>) {}", "OK")
    }

    @Test fun testTuple() {
        chkCompile("function f(x: ()) {}", "ct_err:syntax")
        chkCompile("function f(x: (,)) {}", "ct_err:syntax")
        chkCompile("function f(x: (integer,)) {}", "OK")
        chkCompile("function f(x: (integer,text)) {}", "OK")
        chkCompile("function f(x: (integer,text,)) {}", "OK")

        chk("()", "ct_err:syntax")
        chk("(,)", "ct_err:syntax")
        chk("(123,)", "(int[123])")
        chk("(123,456)", "(int[123],int[456])")
        chk("(123,456,)", "(int[123],int[456])")
    }

    @Test fun testTupleVar() {
        chkEx("{ val (); }", "ct_err:syntax")
        chkEx("{ val (,); }", "ct_err:syntax")
        chkEx("{ val (x) = (123,); return x; }", "int[123]")
        chkEx("{ val (x,) = (123,); return x; }", "int[123]")
        chkEx("{ val (x,y) = (123,'A'); return x + y; }", "text[123A]")
        chkEx("{ val (x,y,) = (123,'A'); return x + y; }", "text[123A]")
    }

    @Test fun testUpdate() {
        def("entity data { mutable x: integer; mutable y: text; }")

        chkCompile("operation op() { update () @* {} ( .x = 123 ); }", "ct_err:syntax")
        chkCompile("operation op() { update (,) @* {} ( .x = 123 ); }", "ct_err:syntax")
        chkCompile("operation op() { update (data) @* {} ( .x = 123 ); }", "OK")
        chkCompile("operation op() { update (data,) @* {} ( .x = 123 ); }", "OK")
        chkCompile("operation op() { update (a:data,b:data) @* {} ( .x = 123 ); }", "OK")
        chkCompile("operation op() { update (a:data,b:data,) @* {} ( .x = 123 ); }", "OK")

        chkCompile("operation op() { update data @* {} (); }", "ct_err:syntax")
        chkCompile("operation op() { update data @* {} (,); }", "ct_err:syntax")
        chkCompile("operation op() { update data @* {} ( .x = 123 ); }", "OK")
        chkCompile("operation op() { update data @* {} ( .x = 123, ); }", "OK")
        chkCompile("operation op() { update data @* {} ( .x = 123, .y = 'A' ); }", "OK")
        chkCompile("operation op() { update data @* {} ( .x = 123, .y = 'A', ); }", "OK")
    }

    @Test fun testWhen() {
        chkWhenExpr("when (x) { 1 -> 'A'; else -> 'B' }", 1 to "A", 2 to "B")
        chkWhenExpr("when (x) { 1, -> 'A'; else -> 'B' }", 1 to "A", 2 to "B")
        chkWhenExpr("when (x) { 1,2,3 -> 'A'; else -> 'B' }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")
        chkWhenExpr("when (x) { 1,2,3, -> 'A'; else -> 'B' }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")

        chkWhenExpr("when { x == 1 -> 'A'; else -> 'B' }", 1 to "A", 2 to "B")
        chkWhenExpr("when { x == 1, -> 'A'; else -> 'B' }", 1 to "A", 2 to "B")
        chkWhenExpr("when { x == 1, x == 2, x == 3 -> 'A'; else -> 'B' }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")
        chkWhenExpr("when { x == 1, x == 2, x == 3, -> 'A'; else -> 'B' }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")

        chkWhenStmt("when (x) { 1 -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "B")
        chkWhenStmt("when (x) { 1, -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "B")
        chkWhenStmt("when (x) { 1,2,3 -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")
        chkWhenStmt("when (x) { 1,2,3, -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "A", 3 to "A", 4 to "B")

        chkWhenStmt("when { x == 1 -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "B")
        chkWhenStmt("when { x == 1, -> return 'A'; else -> return 'B'; }", 1 to "A", 2 to "B")
        chkWhenStmt("when { x == 1, x == 2, x == 3 -> return 'A'; else -> return 'B'; }",
            1 to "A", 2 to "A", 3 to "A", 4 to "B")
        chkWhenStmt("when { x == 1, x == 2, x == 3, -> return 'A'; else -> return 'B'; }",
            1 to "A", 2 to "A", 3 to "A", 4 to "B")

        chkCompile("query q(x: integer) = when (x) { , -> 'A'; else -> 'B' }", "ct_err:syntax")
        chkCompile("query q(x: integer) { when (x) { , -> return 'A'; else -> return 'B'; } }", "ct_err:syntax")
    }

    private fun chkWhenExpr(expr: String, vararg args: Pair<Int, String>) {
        chkWhenStmt("return $expr;", *args)
    }

    private fun chkWhenStmt(stmt: String, vararg args: Pair<Int, String>) {
        for ((arg, exp) in args) {
            chkFull("query q(x: integer) { $stmt }", arg.toLong(), "text[$exp]")
        }
    }

    @Test fun testFunctionCallExpr() {
        tst.testLib = true
        def("function f(x: integer, y: text = 'A') = x + y;")
        def("operation op(x: integer, y: text = 'A') {}")

        chk("f(,)", "ct_err:syntax")
        chk("f(123)", "text[123A]")
        chk("f(123,)", "text[123A]")
        chk("f(123,'B')", "text[123B]")
        chk("f(123,'B',)", "text[123B]")

        chk("op(,)", "ct_err:syntax")
        chk("op(123)", "op[op(123,\"A\")]")
        chk("op(123,)", "op[op(123,\"A\")]")
        chk("op(123,'B')", "op[op(123,\"B\")]")
        chk("op(123,'B',)", "op[op(123,\"B\")]")
    }

    @Test fun testAtExpr() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity data { x: integer; }")
        insert("c0.data", "x", "1,123")

        chk("(,) @? {}", "ct_err:syntax")
        chk("(data) @? {}", "data[1]")
        chk("(data,) @? {}", "data[1]")
        chk("(a:data, b:data) @? {}", "(a=data[1],b=data[1])")
        chk("(a:data, b:data,) @? {}", "(a=data[1],b=data[1])")

        chk("data @? {,}", "ct_err:syntax")
        chk("data @? { .x == 123 }", "data[1]")
        chk("data @? { .x == 123, }", "data[1]")
        chk("data @? { .x != 123, .x != 456 }", "null")
        chk("data @? { .x != 123, .x != 456, }", "null")
        chk("data @? { .x != 456, .x != 789 }", "data[1]")
        chk("data @? { .x != 456, .x != 789, }", "data[1]")

        chk("data @? {} (,)", "ct_err:syntax")
        chk("data @? {} (.x)", "123")
        chk("data @? {} (.x,)", "123")
        chk("data @? {} (_=.x, .x*.x)", "(123,15129)")
        chk("data @? {} (_=.x, .x*.x,)", "(123,15129)")
    }

    @Test fun testCreateExpr() {
        def("entity data { x: integer; y: text = 'A'; }")
        chkCompile("operation op() { create data(,); }", "ct_err:syntax")
        chkCompile("operation op() { create data(x = 123); }", "OK")
        chkCompile("operation op() { create data(x = 123,); }", "OK")
        chkCompile("operation op() { create data(x = 123, y = 'B'); }", "OK")
        chkCompile("operation op() { create data(x = 123, y = 'B',); }", "OK")
    }

    @Test fun testCollectionLiteralExpr() {
        tst.strictToString = false

        chk("[]", "ct_err:expr_list_no_type")
        chk("[,]", "ct_err:syntax")
        chk("[123]", "[123]")
        chk("[123,]", "[123]")
        chk("[123,456]", "[123, 456]")
        chk("[123,456,]", "[123, 456]")

        chk("[:]", "ct_err:expr_map_no_type")
        chk("[:,]", "ct_err:syntax")
        chk("[123:'A']", "{123=A}")
        chk("[123:'A',]", "{123=A}")
        chk("[123:'A',456:'B']", "{123=A, 456=B}")
        chk("[123:'A',456:'B',]", "{123=A, 456=B}")
    }
}
