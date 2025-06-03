/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import org.junit.Test

class IdeSymbolTupleTest: BaseIdeSymbolTest() {
    @Test fun testAtWhatExplicitCol() {
        file("rec.rell", "struct rec { x: integer; y: integer; } function data() = list<rec>();")
        file("def.rell", "function f() { val r = data() @{} (a = .x, b = .y, @omit c = .x); return r; }")

        chkSymsFile("def.rell",
            "data=DEF_FUNCTION|-|rec.rell/function[data]",
            "a=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[a]|-",
            "x=MEM_STRUCT_ATTR|-|rec.rell/struct[rec].attr[x]",
            "b=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[b]|-",
            "y=MEM_STRUCT_ATTR|-|rec.rell/struct[rec].attr[y]",
            "omit=MOD_ANNOTATION|-|-",
            "c=MEM_TUPLE_ATTR|-|-",
            "x=MEM_STRUCT_ATTR|-|rec.rell/struct[rec].attr[x]",
        )

        val link = "def.rell/function[f].tuple[_0].attr"
        chkSymsExpr("f().a", "a=MEM_TUPLE_ATTR|-|$link[a]", "?doc=TUPLE_ATTR|a|a: [integer]")
        chkSymsExpr("f().b", "b=MEM_TUPLE_ATTR|-|$link[b]", "?doc=TUPLE_ATTR|b|b: [integer]")
    }

    @Test fun testAtWhatExplicitDb() {
        file("rec.rell", "entity data { x: integer; y: integer; }")
        file("def.rell", "function f() { val r = data @{} (a = .x, b = .y, @omit c = .x); return r; }")

        chkSymsFile("def.rell",
            "data=DEF_ENTITY|-|rec.rell/entity[data]",
            "a=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[a]|-",
            "x=MEM_ENTITY_ATTR_NORMAL|-|rec.rell/entity[data].attr[x]",
            "b=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[b]|-",
            "y=MEM_ENTITY_ATTR_NORMAL|-|rec.rell/entity[data].attr[y]",
            "omit=MOD_ANNOTATION|-|-",
            "c=MEM_TUPLE_ATTR|-|-",
            "x=MEM_ENTITY_ATTR_NORMAL|-|rec.rell/entity[data].attr[x]",
        )

        val link = "def.rell/function[f].tuple"
        chkSymsExpr("f().a", "a=MEM_TUPLE_ATTR|-|$link[_0].attr[a]", "?doc=TUPLE_ATTR|a|a: [integer]")
        chkSymsExpr("f().b", "b=MEM_TUPLE_ATTR|-|$link[_0].attr[b]", "?doc=TUPLE_ATTR|b|b: [integer]")
    }

    @Test fun testAtWhatImplicitCol() {
        file("rec.rell", "struct rec { x: integer; y: integer; } function data() = list<rec>();")
        file("def.rell", "function f() { val r = data() @{} (.x, $.y, @omit .x); return r; }")

        chkSymsFile("def.rell",
            "data=DEF_FUNCTION|-|rec.rell/function[data]",
            "x=MEM_STRUCT_ATTR|function[f].tuple[_0].attr[x]|rec.rell/struct[rec].attr[x]",
            "$=LOC_AT_ALIAS|-|local[data:0]",
            "y=MEM_STRUCT_ATTR|function[f].tuple[_0].attr[y]|rec.rell/struct[rec].attr[y]",
            "omit=MOD_ANNOTATION|-|-",
            "x=MEM_STRUCT_ATTR|-|rec.rell/struct[rec].attr[x]",
        )

        val link = "def.rell/function[f].tuple[_0]"
        chkSymsExpr("f().x", "x=MEM_TUPLE_ATTR|-|$link.attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("f().y", "y=MEM_TUPLE_ATTR|-|$link.attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
    }

    @Test fun testAtWhatImplicitDb() {
        file("rec.rell", "entity data { x: integer; y: integer; }")
        file("def.rell", "function f() { val r = data @{} (.x, $.y, @omit .x); return r; }")

        chkSymsFile("def.rell",
            "data=DEF_ENTITY|-|rec.rell/entity[data]",
            "x=MEM_ENTITY_ATTR_NORMAL|function[f].tuple[_0].attr[x]|rec.rell/entity[data].attr[x]",
            "$=LOC_AT_ALIAS|-|local[data:0]",
            "y=MEM_ENTITY_ATTR_NORMAL|function[f].tuple[_0].attr[y]|rec.rell/entity[data].attr[y]",
            "omit=MOD_ANNOTATION|-|-",
            "x=MEM_ENTITY_ATTR_NORMAL|-|rec.rell/entity[data].attr[x]",
        )

        val link = "def.rell/function[f].tuple[_0]"
        chkSymsExpr("f().x", "x=MEM_TUPLE_ATTR|-|$link.attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("f().y", "y=MEM_TUPLE_ATTR|-|$link.attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
    }

    @Test fun testExpr() {
        val attrs = arrayOf(
            "x=MEM_TUPLE_ATTR|function[__main].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[__main].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[__main].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )

        val expr = "(x=123, (y=456, z=789))"
        val fn = "main.rell/function[__main]"
        chkSymsExpr("$expr.x", *attrs, "x=MEM_TUPLE_ATTR|-|$fn.tuple[_0].attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("$expr[1].y", *attrs, "y=MEM_TUPLE_ATTR|-|$fn.tuple[_1].attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
        chkSymsExpr("$expr[1].z", *attrs, "z=MEM_TUPLE_ATTR|-|$fn.tuple[_1].attr[z]", "?doc=TUPLE_ATTR|z|z: [integer]")
    }

    @Test fun testFunctionResultValueShort() {
        file("def.rell", "function f() = (x = 123, (y = 456, z = 789));")
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    @Test fun testFunctionResultValueFull() {
        file("def.rell", "function f() { return (x = 123, (y = 456, z = 789)); }")
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    @Test fun testFunctionResultType() {
        file("def.rell", "function f(): (x: integer, (y: integer, z: integer)) { return (x = 123, (y = 456, z = 789)); }")
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_2].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_3].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_3].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    @Test fun testFunctionVal() {
        file("def.rell", "function f() { val r = (x = 123, (y = 456, z = 789)); return r; }")
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    @Test fun testFunctionValAssign() {
        file("def.rell", """
            function f() {
                val r: (x: integer, (y: integer, z: integer));
                r = (x = 123, (y = 456, z = 789));
                return r;
            }
        """)
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_2].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_3].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_3].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    @Test fun testFunctionParam() {
        file("def.rell", "function f(a: (x: integer, (y: integer, z: integer))? = null) = a!!;")
        chkSymsFile("def.rell",
            "x=MEM_TUPLE_ATTR|function[f].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[f].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )
        chkTupleAttrFn()
    }

    private fun chkTupleAttrFn() {
        val link = "def.rell/function[f].tuple"
        chkSymsExpr("f().x", "x=MEM_TUPLE_ATTR|-|$link[_0].attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("f()[1].y", "y=MEM_TUPLE_ATTR|-|$link[_1].attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
        chkSymsExpr("f()[1].z", "z=MEM_TUPLE_ATTR|-|$link[_1].attr[z]", "?doc=TUPLE_ATTR|z|z: [integer]")
    }

    @Test fun testStructAttr() {
        file("def.rell", "struct s { a: (x: integer, (y: integer, z: integer))? = null; }")

        chkSymsFile("def.rell",
            "s=DEF_STRUCT|struct[s]|-",
            "a=MEM_STRUCT_ATTR|struct[s].attr[a]|-",
            "x=MEM_TUPLE_ATTR|struct[s].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|struct[s].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|struct[s].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )

        val aRef = arrayOf("a=MEM_STRUCT_ATTR|-|def.rell/struct[s].attr[a]", "?head=STRUCT_ATTR|:s.a")
        val link = "def.rell/struct[s].tuple"
        chkSymsExpr("s().a!!.x", *aRef, "x=MEM_TUPLE_ATTR|-|$link[_0].attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("s().a!![1].y", *aRef, "y=MEM_TUPLE_ATTR|-|$link[_1].attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
        chkSymsExpr("s().a!![1].z", *aRef, "z=MEM_TUPLE_ATTR|-|$link[_1].attr[z]", "?doc=TUPLE_ATTR|z|z: [integer]")
    }

    @Test fun testStructAttrValue() {
        file("def.rell", "struct s { a: (x: integer, (y: integer, z: integer)) = (x = 123, (y = 456, z = 789)); }")

        chkSymsFile("def.rell",
            "s=DEF_STRUCT|struct[s]|-",
            "a=MEM_STRUCT_ATTR|struct[s].attr[a]|-",
            "x=MEM_TUPLE_ATTR|struct[s].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|struct[s].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|struct[s].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
            "x=MEM_TUPLE_ATTR|struct[s].tuple[_2].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|struct[s].tuple[_3].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|struct[s].tuple[_3].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )

        val aRef = arrayOf("a=MEM_STRUCT_ATTR|-|def.rell/struct[s].attr[a]", "?head=STRUCT_ATTR|:s.a")
        val link = "def.rell/struct[s].tuple"
        chkSymsExpr("s().a.x", *aRef, "x=MEM_TUPLE_ATTR|-|$link[_0].attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("s().a[1].y", *aRef, "y=MEM_TUPLE_ATTR|-|$link[_1].attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
        chkSymsExpr("s().a[1].z", *aRef, "z=MEM_TUPLE_ATTR|-|$link[_1].attr[z]", "?doc=TUPLE_ATTR|z|z: [integer]")
    }

    @Test fun testVal() {
        val attrs = arrayOf(
            "x=MEM_TUPLE_ATTR|function[__main].tuple[_0].attr[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "y=MEM_TUPLE_ATTR|function[__main].tuple[_1].attr[y]|-", "?doc=TUPLE_ATTR|y|y: [integer]",
            "z=MEM_TUPLE_ATTR|function[__main].tuple[_1].attr[z]|-", "?doc=TUPLE_ATTR|z|z: [integer]",
        )

        val expr = "(x=123, (y=456, z=789))"
        val link = "main.rell/function[__main].tuple"
        chkSymsExpr("$expr.x", *attrs, "x=MEM_TUPLE_ATTR|-|$link[_0].attr[x]", "?doc=TUPLE_ATTR|x|x: [integer]")
        chkSymsExpr("$expr[1].y", *attrs, "y=MEM_TUPLE_ATTR|-|$link[_1].attr[y]", "?doc=TUPLE_ATTR|y|y: [integer]")
        chkSymsExpr("$expr[1].z", *attrs, "z=MEM_TUPLE_ATTR|-|$link[_1].attr[z]", "?doc=TUPLE_ATTR|z|z: [integer]")
    }
}
