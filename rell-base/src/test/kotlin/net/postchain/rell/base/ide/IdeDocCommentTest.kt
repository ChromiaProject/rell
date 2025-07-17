/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import kotlin.test.Test

class IdeDocCommentTest: BaseIdeSymbolTest() {
    @Test fun testVariableSimple() {
        init()
        val c = "/** MyComment */"
        chkSymsStmt("val x = 123;", "x=LOC_VAL|-|-", "?doc=VAR|x|-")
        chkSymsStmt("$c val x = 123;", "x=LOC_VAL|-|-", "?doc=VAR|x|MyComment")
        chkSymsStmt("val $c x = 123;", "x=LOC_VAL|-|-", "?doc=VAR|x|-")
        chkSymsStmt("var x = 123;", "x=LOC_VAR|-|-", "?doc=VAR|x|-")
        chkSymsStmt("$c var x = 123;", "x=LOC_VAR|-|-", "?doc=VAR|x|MyComment")
        chkSymsStmt("var $c x = 123;", "x=LOC_VAR|-|-", "?doc=VAR|x|-")
    }

    @Test fun testVariableComplex() {
        init()

        chkSymsStmt("val (x, (y, z)) = (1, (2, 3));",
            "x=LOC_VAL|-|-", "?doc=VAR|x|-",
            "y=LOC_VAL|-|-", "?doc=VAR|y|-",
            "z=LOC_VAL|-|-", "?doc=VAR|z|-",
        )
        chkSymsStmt("/** V */ val (x, (y, z)) = (1, (2, 3));",
            "x=LOC_VAL|-|-", "?doc=VAR|x|V",
            "y=LOC_VAL|-|-", "?doc=VAR|y|V",
            "z=LOC_VAL|-|-", "?doc=VAR|z|V",
        )
        chkSymsStmt("/** V */ val (/** X */ x, (/** Y */ y, /** Z */ z)) = (1, (2, 3));",
            "x=LOC_VAL|-|-", "?doc=VAR|x|V",
            "y=LOC_VAL|-|-", "?doc=VAR|y|V",
            "z=LOC_VAL|-|-", "?doc=VAR|z|V",
        )
    }

    @Test fun testVariableImplicitName() {
        init()
        file("module.rell", "namespace ns { /** MyData */ struct data {} } val v = ns.data();")

        val c = "/** MyComment */"
        val nameType = arrayOf("name=DEF_TYPE|-|-", "?doc=ALIAS|rell:name||since:0.6.0")
        chkSymsStmt("var name; name = '';", *nameType, "name=LOC_VAR|*", "?doc=VAR|name|-")
        chkSymsStmt("val name; name = '';", *nameType, "name=LOC_VAL|*", "?doc=VAR|name|-")
        chkSymsStmt("$c var name; name = '';", *nameType, "name=LOC_VAR|*", "?doc=VAR|name|MyComment")
        chkSymsStmt("$c val name; name = '';", *nameType, "name=LOC_VAL|*", "?doc=VAR|name|MyComment")

        val dataType = arrayOf("data=DEF_STRUCT|*", "?doc=STRUCT|:ns.data|MyData")
        chkSymsStmt("var ns.data; data = v;", *dataType, "data=LOC_VAR|*", "?doc=VAR|data|-")
        chkSymsStmt("val ns.data; data = v;", *dataType, "data=LOC_VAL|*", "?doc=VAR|data|-")
        chkSymsStmt("$c var ns.data; data = v;", *dataType, "data=LOC_VAR|*", "?doc=VAR|data|MyComment")
        chkSymsStmt("$c val ns.data; data = v;", *dataType, "data=LOC_VAL|*", "?doc=VAR|data|MyComment")
    }

    @Test fun testVariableFor() {
        init()
        val c = "/** MyComment */"
        chkSymsStmt("for (x in [1]) {}", "x=LOC_VAL|*", "?doc=VAR|x|-")
        chkSymsStmt("$c for (x in [1]) {}", "x=LOC_VAL|*", "?doc=VAR|x|-")
        chkSymsStmt("for ($c x in [1]) {}", "x=LOC_VAL|*", "?doc=VAR|x|-")
    }

    @Test fun testImplicitNameAttribute() {
        init()
        file("module.rell", "namespace ns { enum color {} }")
        file("lib.rell", """
            /** DataCom */
            struct data {
                /** NameCom */ name = '';
                /** ColorCom */ ns.color? = null;
            }
        """)

        chkSymsFile("lib.rell",
            "name=DEF_TYPE|*", "?doc=ALIAS|rell:name||since:0.6.0",
            "color=DEF_ENUM|*", "?doc=ENUM|:ns.color|-",
        )
        chkSymsExpr("data().name", "name=MEM_STRUCT_ATTR|*", "?doc=STRUCT_ATTR|:data.name|NameCom")
        chkSymsExpr("data().color", "color=MEM_STRUCT_ATTR|*", "?doc=STRUCT_ATTR|:data.color|ColorCom")
    }

    @Test fun testImplicitNameParameter() {
        init()
        file("module.rell", "namespace ns { enum color {} }")

        val c = "/** MyComment */"
        val nameType = arrayOf("name=DEF_TYPE|*", "?doc=ALIAS|rell:name||since:0.6.0")
        chkSyms("function f(name) = name;", *nameType, "name=LOC_PARAMETER|*", "?doc=PARAMETER|name|-")
        chkSyms("$c function f(name) = name;", *nameType, "name=LOC_PARAMETER|*", "?doc=PARAMETER|name|-")
        chkSyms("function f($c name) = name;", *nameType, "name=LOC_PARAMETER|*", "?doc=PARAMETER|name|MyComment")
        chkSyms("/** @param name OtherComment */ function f(name) = name;", *nameType,
            "name=LOC_PARAMETER|*", "?doc=PARAMETER|name|OtherComment",
        )

        val colorType = arrayOf("color=DEF_ENUM|*", "?doc=ENUM|:ns.color|-")
        chkSyms("function f(ns.color) = color;", *colorType, "color=LOC_PARAMETER|*", "?doc=PARAMETER|color|-")
        chkSyms("$c function f(ns.color) = color;", *colorType, "color=LOC_PARAMETER|*", "?doc=PARAMETER|color|-")
        chkSyms("function f($c ns.color) = color;", *colorType,
            "color=LOC_PARAMETER|*", "?doc=PARAMETER|color|MyComment",
        )
        chkSyms("/** @param color OtherComment */ function f(ns.color) = color;", *colorType,
            "color=LOC_PARAMETER|*", "?doc=PARAMETER|color|OtherComment",
        )
    }

    @Test fun testTupleAttributes() {
        init()

        val (c1, c2) = listOf("/** ComA */", "/** ComB */")
        file("f.rell", "function f() = ($c1 a = 1, $c2 b = 2);")
        file("g.rell", "function g(): ($c1 a: integer, $c2 b: text)? = null;")

        val (a, b) = listOf("a=MEM_TUPLE_ATTR|*", "b=MEM_TUPLE_ATTR|*")
        chkSymsFile("f.rell", a, "?doc=TUPLE_ATTR|a|ComA", b, "?doc=TUPLE_ATTR|b|ComB")
        chkSymsFile("g.rell", a, "?doc=TUPLE_ATTR|a|ComA", b, "?doc=TUPLE_ATTR|b|ComB")

        chkSymsExpr("f().a", a, "?doc=TUPLE_ATTR|a|ComA")
        chkSymsExpr("f().b", b, "?doc=TUPLE_ATTR|b|ComB")
        chkSymsExpr("g()?.a", a, "?doc=TUPLE_ATTR|a|ComA")
        chkSymsExpr("g()?.b", b, "?doc=TUPLE_ATTR|b|ComB")

        chkSymsExpr("(a = 1, b = 2)", a, "?doc=TUPLE_ATTR|a|-", b, "?doc=TUPLE_ATTR|b|-")
        chkSymsExpr("($c1 a = 1, $c2 b = 2)", a, "?doc=TUPLE_ATTR|a|ComA", b, "?doc=TUPLE_ATTR|b|ComB")
    }

    @Test fun testAtExprWhatAttribute() {
        init()
        file("lib.rell", "struct data { /** Data.A */ a: integer; /** Data.B */ b: text; }")
        file("f.rell", "function f() = [1] @{} ( /** F.A */ a = 1, /** F.B */ b = 'A' );")
        file("g.rell", "function g(l: list<data> = []) = l @{} ( .a, .b );")
        file("h.rell", "function h(l: list<data> = []) = l @{} ( /** H.A */ .a, /** H.B */ .b );")

        val (ta, tb) = listOf("a=MEM_TUPLE_ATTR|*", "b=MEM_TUPLE_ATTR|*")
        val (sa, sb) = listOf("a=MEM_STRUCT_ATTR|*", "b=MEM_STRUCT_ATTR|*")
        chkSymsFile("f.rell", ta, "?doc=TUPLE_ATTR|a|F.A", tb, "?doc=TUPLE_ATTR|b|F.B")
        chkSymsFile("g.rell", sa, "?doc=STRUCT_ATTR|:data.a|Data.A", sb, "?doc=STRUCT_ATTR|:data.b|Data.B")
        chkSymsFile("h.rell", sa, "?doc=STRUCT_ATTR|:data.a|Data.A", sb, "?doc=STRUCT_ATTR|:data.b|Data.B")

        chkSymsExpr("(f().a, f().b)", ta, "?doc=TUPLE_ATTR|a|F.A", tb, "?doc=TUPLE_ATTR|b|F.B")
        chkSymsExpr("(g().a, g().b)", ta, "?doc=TUPLE_ATTR|a|-", tb, "?doc=TUPLE_ATTR|b|-")
        chkSymsExpr("(h().a, h().b)", ta, "?doc=TUPLE_ATTR|a|-", tb, "?doc=TUPLE_ATTR|b|-")
    }

    @Test fun testAtExprFromAlias() {
        init()
        file("module.rell", """
            /** Data */ entity data { name; }
            /** Rec */ struct rec {}
            /** F */ function f() = list<rec>();
        """)

        val c = "/** MyComment */"
        chkSymsExpr("$c data @* {} (data)",
            "data=DEF_ENTITY|*", "?doc=ENTITY|:data|data|Data",
            "data=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|data|-",
        )
        chkSymsExpr("(x: data, $c @outer d: data) @* {} (d)",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|d|MyComment",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|d|MyComment",
        )
        chkSymsExpr("(x: data, @outer $c d: data) @* {} (d)",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|d|-",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|d|-",
        )

        chkSymsExpr("($c f()) @* {} ($)", "$=LOC_AT_ALIAS|*", "?doc=AT_VAR_COL|$|-")
        chkSymsExpr("($c d: f()) @* {} (d)",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_COL|d|MyComment",
            "d=LOC_AT_ALIAS|*", "?doc=AT_VAR_COL|d|MyComment",
        )
    }

    @Test fun testUpdateDeleteFromAlias() {
        init()
        file("module.rell", "/** Data */ entity data { mutable name; }")

        val c = "/** MyComment */"

        val data = arrayOf(
            "data=DEF_ENTITY|*", "?doc=ENTITY|:data|data|Data",
            "data=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|data|-",
        )
        val dollar = arrayOf("$=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|$|-")
        val d = arrayOf("d=LOC_AT_ALIAS|*", "?doc=AT_VAR_DB|d|MyComment")

        chkSymsStmt("update $c data @* { data.name == $.name } ( .name = '' );", *data, *dollar)
        chkSymsStmt("update ($c data) @* { data.name == $.name } ( .name = '' );", *data, *dollar)
        chkSymsStmt("update ($c d: data) @* { d.name == '' } ( .name = 'A' );", *d, *d)

        chkSymsStmt("delete $c data @* { data.name == $.name };", *data, *dollar)
        chkSymsStmt("delete ($c data) @* { data.name == $.name };", *data, *dollar)
        chkSymsStmt("delete ($c d: data) @* { d.name == '' };", *d, *d)
    }

    private fun init() {
        docDeclarationsEnabled = false
        docCommentsEnabled = true
    }
}
