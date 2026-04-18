/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import kotlin.test.Test

abstract class AtExprColAggrTest: AtExprBaseTest() {
    protected val fromData = impFrom("data")

    protected fun initData() {
        tst.strictToString = false
        impDefType("data", "k:integer", "v:text")
        impCreateObjs("data",
            "k = 1, v = 'A'",
            "k = 2, v = 'B'",
            "k = 1, v = 'C'",
            "k = 3, v = 'D'",
            "k = 1, v = 'E'",
            "k = 2, v = 'F'",
        )
    }

    @Test fun testList() {
        initData()

        chkAt("$fromData @{ .k < 0 } ( @list .v )", "list<text>", "[]")
        chkAt("$fromData @{} ( @list .k + 100 )", "list<integer>", "[101, 102, 101, 103, 101, 102]")

        chkAt("$fromData @*{} ( @group _=.k, @list .v )",
            "list<(integer,list<text>)>",
            "[(1,[A, C, E]), (2,[B, F]), (3,[D])]",
        )
    }

    @Test fun testSet() {
        initData()

        chkAt("$fromData @{ .k < 0 } ( @set .v )", "set<text>", "[]")
        chkAt("$fromData @{} ( @set .k + 100 )", "set<integer>", "[101, 102, 103]")

        chkAt("$fromData @*{} ( @group _=.k, @set .v )",
            "list<(integer,set<text>)>",
            "[(1,[A, C, E]), (2,[B, F]), (3,[D])]",
        )

        chkAt("$fromData @*{} ( @group _=.k, @set .k )", "list<(integer,set<integer>)>", "[(1,[1]), (2,[2]), (3,[3])]")
    }

    @Test fun testSetError() {
        initData()
        chk("$fromData @{} ( @set [.k] )", "ct_err:at:what:aggr:bad_type:SET:list<integer>")
        chk("$fromData @*{} ( @group .k, @set [.v] )", "ct_err:at:what:aggr:bad_type:SET:list<text>")
    }

    @Test fun testMap() {
        initData()

        chkAt("$fromData @{ .k < 0 } ( @map (.k, .v) )", "map<integer,text>", "{}")

        chkAt("$fromData @{} ( @map (.v, .k + 100) )",
            "map<text,integer>",
            "{A=101, B=102, C=101, D=103, E=101, F=102}",
        )

        chkAt("$fromData @{} ( @map (a = .v, b = .k) )", "map<text,integer>", "{A=1, B=2, C=1, D=3, E=1, F=2}")
        chkAt("$fromData @{} ( @map (a = .v, .k) )", "map<text,integer>", "{A=1, B=2, C=1, D=3, E=1, F=2}")
        chkAt("$fromData @{} ( @map (.v, b = .k) )", "map<text,integer>", "{A=1, B=2, C=1, D=3, E=1, F=2}")

        chkAt("$fromData @*{} ( @group _=.k, @map (.v, .k) )",
            "list<(integer,map<text,integer>)>",
            "[(1,{A=1, C=1, E=1}), (2,{B=2, F=2}), (3,{D=3})]",
        )
    }

    @Test fun testMapChain() {
        initData()

        chkAt("$fromData @*{} ( @group .k, @list .v ) @{} ( @map $ )",
            "map<integer,list<text>>",
            "{1=[A, C, E], 2=[B, F], 3=[D]}",
        )
    }

    @Test fun testMapError() {
        initData()
        chk("$fromData @{} ( @map .k )", "ct_err:at:what:aggr:bad_type:MAP:integer")
        chk("$fromData @{} ( @map ([.k], .v) )", "ct_err:at:what:aggr:bad_type:MAP:(list<integer>,text)")
        chk("$fromData @{} ( @map (.k, .v) )", "rt_err:aggregate:map:dupkey:int[1]")
        chk("$fromData @{} ( @map (.k, 123) )", "rt_err:aggregate:map:dupkey:int[1]")
    }

    @Test fun testLimitOffset() {
        initData()

        chk("$fromData @*{} ( @group _=.k, @list .v )", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")

        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 0", "[]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 1", "[(1,[A, C, E])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 2", "[(1,[A, C, E]), (2,[B, F])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 3", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")

        chk("$fromData @*{} ( @group _=.k, @list .v ) offset 0", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) offset 1", "[(2,[B, F]), (3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) offset 2", "[(3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) offset 3", "[]")

        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 1 offset 0", "[(1,[A, C, E])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 1 offset 1", "[(2,[B, F])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 1 offset 2", "[(3,[D])]")

        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 2 offset 0", "[(1,[A, C, E]), (2,[B, F])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 2 offset 1", "[(2,[B, F]), (3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @list .v ) limit 2 offset 2", "[(3,[D])]")
    }

    @Test fun testSort() {
        initData()

        chk("$fromData @*{} ( @sort @group _=.k, @list .v )", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")
        chk("$fromData @*{} ( @sort_desc @group _=.k, @list .v )", "[(3,[D]), (2,[B, F]), (1,[A, C, E])]")
        chk("$fromData @*{} ( @sort_desc @group _=.k, @list .v ) limit 1", "[(3,[D])]")
        chk("$fromData @*{} ( @sort_desc @group _=.k, @list .v ) offset 2", "[(1,[A, C, E])]")

        chk("$fromData @*{} ( @group _=.k, @sort @list .v )", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @sort_desc @list .v )", "[(3,[D]), (2,[B, F]), (1,[A, C, E])]")
        chk("$fromData @*{} ( @group _=.k, @sort_desc @list .v ) limit 1", "[(3,[D])]")
        chk("$fromData @*{} ( @group _=.k, @sort_desc @list .v ) offset 2", "[(1,[A, C, E])]")
    }

    @Test fun testWhere() {
        initData()
        chk("$fromData @*{} ( @group _=.k, @list .v )", "[(1,[A, C, E]), (2,[B, F]), (3,[D])]")
        chk("$fromData @*{ .v <= 'C' } ( @group _=.k, @list .v )", "[(1,[A, C]), (2,[B])]")
        chk("$fromData @*{ .v >= 'C' } ( @group _=.k, @list .v )", "[(1,[C, E]), (3,[D]), (2,[F])]")
        chk("$fromData @*{ .v >= 'C' } ( @sort @group _=.k, @list .v )", "[(1,[C, E]), (2,[F]), (3,[D])]")
        chk("$fromData @*{ .v >= 'C' } ( @sort_desc @group _=.k, @list .v )", "[(3,[D]), (2,[F]), (1,[C, E])]")
    }

    @Test fun testOmit() {
        initData()
        chkAt("$fromData @*{} ( @omit @group _=.k, @list .v )", "list<list<text>>", "[[A, C, E], [B, F], [D]]")
        chkAt("$fromData @*{} ( @group _=.k, @omit @list .v )", "list<integer>", "[1, 2, 3]")
        chkAt("$fromData @*{} ( @omit @sort @group _=.k, @list .v )", "list<list<text>>", "[[A, C, E], [B, F], [D]]")
        chkAt("$fromData @*{} ( @omit @sort_desc @group _=.k, @list .v )", "list<list<text>>",
            "[[D], [B, F], [A, C, E]]")
    }

    @Test fun testVersionControl() {
        initData()
        chkVerCtExpr("$fromData @*{} ( @group _=.k, @list .v )", "0.13.9", "VER:lib:ANNOTATION:[@list]")
        chkVerCtExpr("$fromData @*{} ( @group _=.k, @set .v )", "0.13.9", "VER:lib:ANNOTATION:[@set]")
        chkVerCtExpr("$fromData @*{} ( @group _=.k, @map (.k, .v) )", "0.13.9", "VER:lib:ANNOTATION:[@map]")
    }

    private fun chkAt(expr: String, expType: String, expValue: String) {
        chk("_type_of($expr)", expType)
        chk(expr, expValue)
    }

    class AtExprColAggrColTest: AtExprColAggrTest() {
        override fun impKind() = AtExprTestKind_Col_Struct()

        @Test fun testNestedAt() {
            initData()
            chk("$fromData @*{exists( $fromData @*{} ( .k ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
            chk("$fromData @*{exists( $fromData @*{} ( @list .k ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
            chk("$fromData @*{exists( $fromData @*{} ( @group .k, @list .v ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
            chk("$fromData @*{exists( $fromData @*{} ( @set .k ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
            chk("$fromData @*{exists( $fromData @*{} ( @map (.v, .k) ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
        }
    }

    class AtExprColAggrDbTest: AtExprColAggrTest() {
        override fun impKind() = AtExprTestKind_Db()

        @Test fun testNestedAt() {
            initData()
            val err = "ct_err:at:what:aggr:collection_db"
            chk("$fromData @*{exists( $fromData @*{} ( .k ) )} ( .k )", "[1, 2, 1, 3, 1, 2]")
            chk("$fromData @*{exists( $fromData @*{} ( @list .k ) )} ( .k )", "$err:LIST:integer")
            chk("$fromData @*{exists( $fromData @*{} ( @group .k, @list .v ) )} ( .k )", "$err:LIST:text")
            chk("$fromData @*{exists( $fromData @*{} ( @set .k ) )} ( .k )", "$err:SET:integer")
            chk("$fromData @*{exists( $fromData @*{} ( @map (.k, .v) ) )} ( .k )", "ct_err:expr_sqlnotallowed") //tuple
        }
    }
}
