/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import org.junit.Test

class IdeSymbolExprTest: BaseIdeSymbolTest() {
    @Test fun testTypeSysMembers() {
        file("module.rell", """
            entity data { name; }
            struct rec { x: integer = 123; }
            function g() = gtv.from_json('');
        """)

        val data = arrayOf("data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        val rec = arrayOf("rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
        val g = arrayOf("g=DEF_FUNCTION|-|module.rell/function[g]", "?head=FUNCTION|:g")
        val fnKind = "DEF_FUNCTION_SYSTEM"

        chkSymsExpr("data.from_gtv(g())", *data, "from_gtv=$fnKind|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv", *g)
        chkSymsExpr("rec.from_gtv(g())", *rec, "from_gtv=$fnKind|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv", *g)
        chkSymsExpr("(data@{}).to_gtv()", *data, "to_gtv=$fnKind|-|-", "?head=FUNCTION|rell:rell.gtv_ext.to_gtv")
        chkSymsExpr("rec().to_gtv()", *rec, "to_gtv=$fnKind|-|-", "?head=FUNCTION|rell:rell.gtv_ext.to_gtv")

        chkSymsExpr("gtv.from_json('')",
            "gtv=DEF_TYPE|-|-", "?head=TYPE|rell:gtv",
            "from_json=$fnKind|-|-", "?head=FUNCTION|rell:gtv.from_json",
        )
        chkSymsExpr("g().to_bytes()", *g, "to_bytes=$fnKind|-|-", "?head=FUNCTION|rell:gtv.to_bytes")

        chkSymsExpr("(123).to_gtv()", "to_gtv=$fnKind|-|-", "?head=FUNCTION|rell:rell.gtv_ext.to_gtv")
        chkSymsExpr("(123).to_hex()", "to_hex=$fnKind|-|-", "?head=FUNCTION|rell:integer.to_hex")
    }

    @Test fun testGlobalDef() {
        file("module.rell", """
            entity data { name; }
            struct rec { x: integer; }
            enum colors { red, green, blue }
            object state { mutable x: integer = 0; }
            val MAGIC = 123;
            function f() {}
            operation op() {}
            query qu() = 123;
        """)

        chkSymsStmt("var v: data;", "data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        chkSymsStmt("var v: rec;", "rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
        chkSymsStmt("var v: colors;", "colors=DEF_ENUM|-|module.rell/enum[colors]", "?head=ENUM|:colors")
        chkSymsStmt("var v: struct<state>;", "state=DEF_OBJECT|-|module.rell/object[state]", "?head=OBJECT|:state|state")
        chkSymsStmt("var v: struct<op>;", "op=DEF_OPERATION|-|module.rell/operation[op]", "?head=OPERATION|:op|op")
        chkSymsStmt("return MAGIC;", "MAGIC=DEF_CONSTANT|-|module.rell/constant[MAGIC]", "?head=CONSTANT|:MAGIC")
        chkSymsStmt("return qu();", "qu=DEF_QUERY|-|module.rell/query[qu]", "?head=QUERY|:qu|qu")
        chkSymsStmt("f();", "f=DEF_FUNCTION|-|module.rell/function[f]", "?head=FUNCTION|:f")
    }

    @Test fun testCall() {
        tst.testLib = true
        file("module.rell", """
            function f(x: integer, y: text) = 0;
            function p(): (integer) -> integer = f(y = '', *);
            operation o(x: integer) {}
        """)

        val f = arrayOf("f=DEF_FUNCTION|-|module.rell/function[f]", "?head=FUNCTION|:f")
        val argBase = "EXPR_CALL_ARG|-|module.rell/function[f].param"
        val x = arrayOf("x=$argBase[x]", "?doc=PARAMETER|x|x: [integer]")
        val y = arrayOf("y=$argBase[y]", "?doc=PARAMETER|y|y: [text]")

        chkSymsExpr("f(x = 123, y = 'Hello')", *f, *x, *y)
        chkSymsExpr("f(y = 'Hello', x = 123)", *f, *y, *x)

        chkSymsExpr("f(x = 123, y = 'Hello', foo = 0)",
            *f, *x, *y, "foo=UNKNOWN|-|-", "?head=-",
            err = "expr:call:unknown_named_arg:[f]:foo",
        )

        chkSymsExpr("f()", *f, err = "expr:call:missing_args:[f]:[0:x,1:y]")
        chkSymsExpr("f(123)", *f, err = "expr:call:missing_args:[f]:[1:y]")
        chkSymsExpr("f('hello', 123)", *f, err = "[expr_call_argtype:[f]:0:x:integer:text][expr_call_argtype:[f]:1:y:text:integer]")
        chkSymsExpr("f(123, 'hello', true)", *f, err = "expr:call:too_many_args:[f]:2:3")

        chkSymsExpr("o(x = 123)",
            "o=DEF_OPERATION|-|module.rell/operation[o]", "?head=OPERATION|:o|o",
            "x=EXPR_CALL_ARG|-|module.rell/operation[o].param[x]", "?doc=PARAMETER|x|x: [integer]",
        )
    }

    @Test fun testCallError() {
        tst.testLib = true
        file("module.rell", """
            function f(x: integer, y: text) = 0;
            function p(): (integer) -> integer = f(y = '', *);
            operation o(x: integer) {}
        """)

        chkSymsExpr("o(x = 123, foo = 456)",
            "o=DEF_OPERATION|-|module.rell/operation[o]", "?head=OPERATION|:o|o",
            "x=EXPR_CALL_ARG|-|module.rell/operation[o].param[x]", "?doc=PARAMETER|x|x: [integer]",
            "foo=UNKNOWN|-|-", "?head=-",
            err = "expr:call:unknown_named_arg:[o]:foo",
        )

        chkSymsExpr("p()(x = 123)",
            "p=DEF_FUNCTION|-|module.rell/function[p]", "?head=FUNCTION|:p",
            "x=UNKNOWN|-|-", "?head=-",
            err = "expr:call:unknown_named_arg:[?]:x",
        )

        chkSymsExpr("integer.from_text(x = '123')",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "from_text=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:integer.from_text",
            "x=UNKNOWN|-|-", "?head=-",
            err = "[expr:call:missing_args:[integer.from_text]:[0:value]][expr:call:unknown_named_arg:[integer.from_text]:x]",
        )

        chkSymsExpr("'Hello'.size(x = 123)",
            "size=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:text.size",
            "x=UNKNOWN|-|-", "?head=-",
            err = "expr:call:unknown_named_arg:[text.size]:x",
        )

        chkSymsExpr("'Hello'.char_at(x = 123)",
            "char_at=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:text.char_at",
            "x=UNKNOWN|-|-", "?head=-",
            err = "[expr:call:missing_args:[text.char_at]:[0:index]][expr:call:unknown_named_arg:[text.char_at]:x]",
        )

        chkSymsExpr("33(x = 1)", "x=UNKNOWN|-|-", "?head=-", err = "expr_call_nofn:integer")
    }

    @Test fun testCreate() {
        file("module.rell", """
            entity data {
                n1: integer = 0; mutable n2: integer = 0;
                key k1: integer = 0; key mutable k2: integer = 0;
                index i1: integer = 0; index mutable i2: integer = 0;
            }
        """)

        val data = arrayOf("data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        val attrBase = "module.rell/entity[data].attr"
        val attrHead = "?head=ENTITY_ATTR|"

        chkSymsExpr("create data()", *data)
        chkSymsExpr("create data(n1 = 1)", *data, "n1=MEM_ENTITY_ATTR_NORMAL|-|$attrBase[n1]", "$attrHead:data.n1")
        chkSymsExpr("create data(n2 = 1)", *data, "n2=MEM_ENTITY_ATTR_NORMAL_VAR|-|$attrBase[n2]", "$attrHead:data.n2")
        chkSymsExpr("create data(k1 = 1)", *data, "k1=MEM_ENTITY_ATTR_KEY|-|$attrBase[k1]", "$attrHead:data.k1")
        chkSymsExpr("create data(k2 = 1)", *data, "k2=MEM_ENTITY_ATTR_KEY_VAR|-|$attrBase[k2]", "$attrHead:data.k2")
        chkSymsExpr("create data(i1 = 1)", *data, "i1=MEM_ENTITY_ATTR_INDEX|-|$attrBase[i1]", "$attrHead:data.i1")
        chkSymsExpr("create data(i2 = 1)", *data, "i2=MEM_ENTITY_ATTR_INDEX_VAR|-|$attrBase[i2]", "$attrHead:data.i2")
        chkSymsExpr("create data(foo = 123)", *data, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
    }

    @Test fun testStructEntityPath() {
        file("lib.rell", """
            struct s1 { s: s2; }
            struct s2 { u: user; }
            entity user { c: company; }
            entity company { name; }
        """)
        file("mid.rell", """
            function f(): s1 = g()!!;
            function g(): s1? = null;
        """)

        chkSymsFile("lib.rell",
            "s1=DEF_STRUCT|struct[s1]|-", "?head=STRUCT|:s1",
            "s=MEM_STRUCT_ATTR|struct[s1].attr[s]|-", "?head=STRUCT_ATTR|:s1.s",
            "s2=DEF_STRUCT|-|lib.rell/struct[s2]", "?head=STRUCT|:s2",
            "s2=DEF_STRUCT|struct[s2]|-", "?head=STRUCT|:s2",
            "u=MEM_STRUCT_ATTR|struct[s2].attr[u]|-", "?head=STRUCT_ATTR|:s2.u",
            "user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user",
            "user=DEF_ENTITY|entity[user]|-", "?head=ENTITY|:user|user",
            "c=MEM_ENTITY_ATTR_NORMAL|entity[user].attr[c]|-", "?head=ENTITY_ATTR|:user.c",
            "company=DEF_ENTITY|-|lib.rell/entity[company]", "?head=ENTITY|:company|company",
            "company=DEF_ENTITY|entity[company]|-", "?head=ENTITY|:company|company",
            "name=DEF_TYPE|entity[company].attr[name]|-", "?head=ALIAS|rell:name",
        )

        chkSymsExpr("f().s.u.c.name",
            "f=DEF_FUNCTION|-|mid.rell/function[f]", "?head=FUNCTION|:f",
            "s=MEM_STRUCT_ATTR|-|lib.rell/struct[s1].attr[s]", "?head=STRUCT_ATTR|:s1.s",
            "u=MEM_STRUCT_ATTR|-|lib.rell/struct[s2].attr[u]", "?head=STRUCT_ATTR|:s2.u",
            "c=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/entity[user].attr[c]", "?head=ENTITY_ATTR|:user.c",
            "name=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/entity[company].attr[name]", "?head=ENTITY_ATTR|:company.name",
        )
    }

    @Test fun testMirrorStructCreateEntity() {
        initMirrorStruct()

        val data = arrayOf("data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        val attrBase = "module.rell/entity[data].attr"
        val head = "?head=ENTITY_ATTR|:data"

        chkSymsExpr("struct<data>(n1 = 1)", *data, "n1=MEM_STRUCT_ATTR|-|$attrBase[n1]", "$head.n1")
        chkSymsExpr("struct<data>(n2 = 1)", *data, "n2=MEM_STRUCT_ATTR|-|$attrBase[n2]", "$head.n2")
        chkSymsExpr("struct<data>(k1 = 1)", *data, "k1=MEM_STRUCT_ATTR|-|$attrBase[k1]", "$head.k1")
        chkSymsExpr("struct<data>(k2 = 1)", *data, "k2=MEM_STRUCT_ATTR|-|$attrBase[k2]", "$head.k2")
        chkSymsExpr("struct<data>(i1 = 1)", *data, "i1=MEM_STRUCT_ATTR|-|$attrBase[i1]", "$head.i1")
        chkSymsExpr("struct<data>(i2 = 1)", *data, "i2=MEM_STRUCT_ATTR|-|$attrBase[i2]", "$head.i2")
        chkSymsExpr("struct<data>(foo = 1)", *data, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")

        chkSymsExpr("struct<mutable data>(n1 = 1)", *data, "n1=MEM_STRUCT_ATTR_VAR|-|$attrBase[n1]", "$head.n1")
        chkSymsExpr("struct<mutable data>(n2 = 1)", *data, "n2=MEM_STRUCT_ATTR_VAR|-|$attrBase[n2]", "$head.n2")
        chkSymsExpr("struct<mutable data>(k1 = 1)", *data, "k1=MEM_STRUCT_ATTR_VAR|-|$attrBase[k1]", "$head.k1")
        chkSymsExpr("struct<mutable data>(k2 = 1)", *data, "k2=MEM_STRUCT_ATTR_VAR|-|$attrBase[k2]", "$head.k2")
        chkSymsExpr("struct<mutable data>(i1 = 1)", *data, "i1=MEM_STRUCT_ATTR_VAR|-|$attrBase[i1]", "$head.i1")
        chkSymsExpr("struct<mutable data>(i2 = 1)", *data, "i2=MEM_STRUCT_ATTR_VAR|-|$attrBase[i2]", "$head.i2")
        chkSymsExpr("struct<mutable data>(foo = 1)", *data, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
    }

    @Test fun testMirrorStructCreateObject() {
        initMirrorStruct()

        val state = arrayOf("state=DEF_OBJECT|-|module.rell/object[state]", "?head=OBJECT|:state|state")
        val attrBase = "module.rell/object[state].attr"
        val head = "?head=OBJECT_ATTR|:state"

        chkSymsExpr("struct<state>(x = 1)", *state, "x=MEM_STRUCT_ATTR|-|$attrBase[x]", "$head.x")
        chkSymsExpr("struct<state>(y = 1)", *state, "y=MEM_STRUCT_ATTR|-|$attrBase[y]", "$head.y")
        chkSymsExpr("struct<state>(foo = 1)", *state, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
        chkSymsExpr("struct<mutable state>(x = 1)", *state, "x=MEM_STRUCT_ATTR_VAR|-|$attrBase[x]", "$head.x")
        chkSymsExpr("struct<mutable state>(y = 1)", *state, "y=MEM_STRUCT_ATTR_VAR|-|$attrBase[y]", "$head.y")
        chkSymsExpr("struct<mutable state>(foo = 1)", *state, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
    }

    @Test fun testMirrorStructCreateOperation() {
        initMirrorStruct()

        val op = arrayOf("op=DEF_OPERATION|-|module.rell/operation[op]", "?head=OPERATION|:op|op")
        val xLink = "module.rell/operation[op].param[x]"

        chkSymsExpr("struct<op>(x = 1)", *op, "x=MEM_STRUCT_ATTR|-|$xLink", "?head=PARAMETER|x")
        chkSymsExpr("struct<op>(foo = 1)", *op, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
        chkSymsExpr("struct<mutable op>(x = 1)", *op, "x=MEM_STRUCT_ATTR_VAR|-|$xLink", "?head=PARAMETER|x")
        chkSymsExpr("struct<mutable op>(foo = 1)", *op, "foo=UNKNOWN|-|-", "?head=-", err = "attr_unknown_name:foo")
    }

    @Test fun testMirrorStructAttrsEntity() {
        initMirrorStruct()

        val data = arrayOf("data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        val attrLink = "module.rell/entity[data].attr"
        val attrHead = "?head=ENTITY_ATTR|:data"

        chkSymsExpr("struct<data>().n1", *data, "n1=MEM_STRUCT_ATTR|-|$attrLink[n1]", "$attrHead.n1")
        chkSymsExpr("struct<data>().n2", *data, "n2=MEM_STRUCT_ATTR|-|$attrLink[n2]", "$attrHead.n2")
        chkSymsExpr("struct<data>().k1", *data, "k1=MEM_STRUCT_ATTR|-|$attrLink[k1]", "$attrHead.k1")
        chkSymsExpr("struct<data>().k2", *data, "k2=MEM_STRUCT_ATTR|-|$attrLink[k2]", "$attrHead.k2")
        chkSymsExpr("struct<data>().i1", *data, "i1=MEM_STRUCT_ATTR|-|$attrLink[i1]", "$attrHead.i1")
        chkSymsExpr("struct<data>().i2", *data, "i2=MEM_STRUCT_ATTR|-|$attrLink[i2]", "$attrHead.i2")
        chkSymsExpr("struct<data>().foo", *data, "foo=UNKNOWN|-|-", "?head=-", err = "unknown_member:[struct<data>]:foo")

        chkSymsExpr("struct<mutable data>().n1", *data, "n1=MEM_STRUCT_ATTR_VAR|-|$attrLink[n1]", "$attrHead.n1")
        chkSymsExpr("struct<mutable data>().n2", *data, "n2=MEM_STRUCT_ATTR_VAR|-|$attrLink[n2]", "$attrHead.n2")
        chkSymsExpr("struct<mutable data>().k1", *data, "k1=MEM_STRUCT_ATTR_VAR|-|$attrLink[k1]", "$attrHead.k1")
        chkSymsExpr("struct<mutable data>().k2", *data, "k2=MEM_STRUCT_ATTR_VAR|-|$attrLink[k2]", "$attrHead.k2")
        chkSymsExpr("struct<mutable data>().i1", *data, "i1=MEM_STRUCT_ATTR_VAR|-|$attrLink[i1]", "$attrHead.i1")
        chkSymsExpr("struct<mutable data>().i2", *data, "i2=MEM_STRUCT_ATTR_VAR|-|$attrLink[i2]", "$attrHead.i2")
        chkSymsExpr("struct<mutable data>().foo", *data, "foo=UNKNOWN|-|-", "?head=-",
            err = "unknown_member:[struct<mutable data>]:foo")
    }

    @Test fun testMirrorStructAttrsObject() {
        initMirrorStruct()

        val state = arrayOf("state=DEF_OBJECT|-|module.rell/object[state]", "?head=OBJECT|:state|state")
        val attrLink = "module.rell/object[state].attr"
        val attrHead = "?head=OBJECT_ATTR|:state"

        chkSymsExpr("struct<state>().x", *state, "x=MEM_STRUCT_ATTR|-|$attrLink[x]", "$attrHead.x")
        chkSymsExpr("struct<state>().y", *state, "y=MEM_STRUCT_ATTR|-|$attrLink[y]", "$attrHead.y")
        chkSymsExpr("struct<state>().foo", *state, "foo=UNKNOWN|-|-", "?head=-",
            err = "unknown_member:[struct<state>]:foo")
        chkSymsExpr("struct<mutable state>().x", *state, "x=MEM_STRUCT_ATTR_VAR|-|$attrLink[x]", "$attrHead.x")
        chkSymsExpr("struct<mutable state>().y", *state, "y=MEM_STRUCT_ATTR_VAR|-|$attrLink[y]", "$attrHead.y")
        chkSymsExpr("struct<mutable state>().foo", *state, "foo=UNKNOWN|-|-", "?head=-",
            err = "unknown_member:[struct<mutable state>]:foo")
    }

    @Test fun testMirrorStructAttrsOperation() {
        initMirrorStruct()

        val op = arrayOf("op=DEF_OPERATION|-|module.rell/operation[op]", "?head=OPERATION|:op|op")
        val xLink = "module.rell/operation[op].param[x]"

        chkSymsExpr("struct<op>().x", *op, "x=MEM_STRUCT_ATTR|-|$xLink", "?head=PARAMETER|x")
        chkSymsExpr("struct<op>().foo", *op, "foo=UNKNOWN|-|-", "?head=-", err = "unknown_member:[struct<op>]:foo")
        chkSymsExpr("struct<mutable op>().x", *op, "x=MEM_STRUCT_ATTR_VAR|-|$xLink", "?head=PARAMETER|x")
        chkSymsExpr("struct<mutable op>().foo", *op, "foo=UNKNOWN|-|-", "?head=-",
            err = "unknown_member:[struct<mutable op>]:foo")
    }

    private fun initMirrorStruct() {
        file("module.rell", """
            entity data {
                n1: integer = 0; mutable n2: integer = 0;
                key k1: integer = 0; key mutable k2: integer = 0;
                index i1: integer = 0; index mutable i2: integer = 0;
            }
            object state {
                x: integer = 0;
                mutable y: integer = 0;
            }
            operation op(x: integer = 0) {}
        """)
    }

    @Test fun testLocalVar() {
        chkSymsStmt("val x = 123; return x;",
            "x=LOC_VAL|-|-", "?doc=VAR|x|<val> x: [integer]",
            "x=LOC_VAL|-|local[x:0]", "?doc=VAR|x|<val> x: [integer]",
        )
        chkSymsStmt("var x = 123; return x;",
            "x=LOC_VAR|-|-", "?doc=VAR|x|<var> x: [integer]",
            "x=LOC_VAR|-|local[x:0]", "?doc=VAR|x|<var> x: [integer]",
        )
        chkSymsStmt("val x: integer; x = 123;",
            "x=LOC_VAL|-|-", "?doc=VAR|x|<val> x: [integer]",
            "x=LOC_VAL|-|local[x:0]", "?doc=VAR|x|<val> x: [integer]",
        )
        chkSymsStmt("var x: integer; x = 123;",
            "x=LOC_VAR|-|-", "?doc=VAR|x|<var> x: [integer]",
            "x=LOC_VAR|-|local[x:0]", "?doc=VAR|x|<var> x: [integer]",
        )
    }

    @Test fun testMember() {
        file("module.rell", "struct p { y: integer; } struct s { x: integer; p = p(456); }")

        val s = arrayOf("s=DEF_STRUCT|-|module.rell/struct[s]", "?head=STRUCT|:s")
        chkSymsExpr("s(123)", *s)
        chkSymsExpr("s(123).x", *s, "x=MEM_STRUCT_ATTR|-|module.rell/struct[s].attr[x]", "?head=STRUCT_ATTR|:s.x")
        chkSymsExpr("s(123).p.y", *s,
            "p=MEM_STRUCT_ATTR|-|module.rell/struct[s].attr[p]", "?head=STRUCT_ATTR|:s.p",
            "y=MEM_STRUCT_ATTR|-|module.rell/struct[p].attr[y]", "?head=STRUCT_ATTR|:p.y",
        )
        chkSymsExpr("s()", *s, err = "attr_missing:[s]:x")
        chkSymsExpr("s().x", *s,
            "x=MEM_STRUCT_ATTR|-|module.rell/struct[s].attr[x]", "?head=STRUCT_ATTR|:s.x",
            err = "attr_missing:[s]:x",
        )
        chkSymsExpr("s().p.x", *s,
            "p=MEM_STRUCT_ATTR|-|module.rell/struct[s].attr[p]", "?head=STRUCT_ATTR|:s.p",
            "x=UNKNOWN|-|-", "?head=-",
            err = "[attr_missing:[s]:x][unknown_member:[p]:x]",
        )

        val abs = arrayOf("abs=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:abs")
        val err = "expr_call_badargs:[abs]:[]"
        chkSymsExpr("abs().a", *abs, "a=UNKNOWN|-|-", "?head=-", err = err)
        chkSymsExpr("abs().f()", *abs, "f=UNKNOWN|-|-", "?head=-", err = err)
        chkSymsExpr("abs().a.b", *abs, "a=UNKNOWN|-|-", "?head=-", "b=UNKNOWN|-|-", "?head=-", err = err)
        chkSymsExpr("abs().a.b()", *abs, "a=UNKNOWN|-|-", "?head=-", "b=UNKNOWN|-|-", "?head=-", err = err)
    }

    @Test fun testEntityAttrNamed() {
        file("lib.rell", "entity data { mutable x: integer; }")
        file("utils.rell", "function d(): data = data @ {};")

        chkSymsFile("lib.rell", "x=MEM_ENTITY_ATTR_NORMAL_VAR|entity[data].attr[x]|-", "?head=ENTITY_ATTR|:data.x")

        val attrRef = arrayOf("x=MEM_ENTITY_ATTR_NORMAL_VAR|*|lib.rell/entity[data].attr[x]", "?head=ENTITY_ATTR|:data.x")
        chkSymsExpr("d().x", *attrRef)
        chkSymsStmt("d().x = 0;", *attrRef)

        chkSymsExpr("data @* { .x == 0 }", *attrRef)
        chkSymsExpr("data @* {} (.x)", *attrRef)
        chkSymsExpr("data @* {} .x", *attrRef)
        chkSymsExpr("data @* {} ($.x)", *attrRef)
        chkSymsExpr("data @* {} (data.x)", *attrRef)

        chkSymsStmt("create data(x = 0);", *attrRef)
        chkSymsStmt("update data @* {.x == 0} ( 123 );", *attrRef)
        chkSymsStmt("update data @* {} ( .x = 0 );", *attrRef)
        chkSymsStmt("update data @* {} ( x = 0 );", *attrRef)
        chkSymsStmt("delete data @* {.x == 0};", *attrRef)
    }

    @Test fun testEntityAttrAnon() {
        file("a.rell", "enum color { red }")
        file("b.rell", "entity data { mutable color; }")

        val attrRef = arrayOf(
            "color=MEM_ENTITY_ATTR_NORMAL_VAR|*|b.rell/entity[data].attr[color]",
            "?doc=ENTITY_ATTR|:data.color|<mutable> color: [color]",
        )

        chkSymsFile("b.rell", "color=DEF_ENUM|entity[data].attr[color]|a.rell/enum[color]", "?head=ENUM|:color")
        chkSyms("function f(d: data) = d.color;", *attrRef)
        chkSymsExpr("data @* {} (.color)", *attrRef)
    }

    @Test fun testEntityAttrRowid() {
        file("lib.rell", "entity data { x: integer; }")
        val rowid = "?doc=ENTITY_ATTR|:data.rowid|<key> rowid: [rowid]"
        chkSyms("function f(d: data) = d.rowid;", "rowid=MEM_ENTITY_ATTR_ROWID|-|-", rowid)
        chkSymsExpr("data @* {} (.rowid)", "rowid=MEM_ENTITY_ATTR_ROWID|*|-", rowid)
        chkSymsExpr("data @* {}.rowid", "rowid=MEM_ENTITY_ATTR_ROWID|*|-", rowid)
    }

    @Test fun testEntityAttrTransaction() {
        file("lib.rell", "@log entity data { x: integer; }")
        val tx = "?doc=ENTITY_ATTR|:data.transaction|transaction: [transaction]"
        chkSyms("function f(d: data) = d.transaction;", "transaction=MEM_ENTITY_ATTR_NORMAL|-|-", tx)
        chkSymsExpr("data @* {} (.transaction)", "transaction=MEM_ENTITY_ATTR_NORMAL|*|-", tx)
        chkSymsExpr("data @* {}.transaction", "transaction=MEM_ENTITY_ATTR_NORMAL|*|-", tx)
    }

    @Test fun testEntityAttrKeyIndex() {
        val head = "?head=ENTITY_ATTR|:data.x"

        chkEntityAttrKeyIndex("entity data { x: integer; {KW} x; }",
            "x=MEM_ENTITY_ATTR_{KW}|entity[data].attr[x]|-", head,
            "x=MEM_ENTITY_ATTR_{KW}|-|main.rell/entity[data].attr[x]", head,
        )

        chkEntityAttrKeyIndex("entity data { {KW} x; x: integer; }",
            "x=MEM_ENTITY_ATTR_{KW}|-|main.rell/entity[data].attr[x]", head,
            "x=MEM_ENTITY_ATTR_{KW}|entity[data].attr[x]|-", head,
        )

        chkEntityAttrKeyIndex("entity data { {KW} x: integer; }",
            "x=MEM_ENTITY_ATTR_{KW}|entity[data].attr[x]|-", head,
        )

        chkSyms("entity data { x: integer; key x; index x; }",
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", head,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", head,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", head,
        )

        chkSyms("entity data { key x; index x; x: integer; }",
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", head,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", head,
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", head,
        )
    }

    private fun chkEntityAttrKeyIndex(code: String, vararg expected: String) {
        chkSyms(code.replace("{KW}", "key"), *expected.map { it.replace("{KW}", "KEY") }.toTypedArray())
        chkSyms(code.replace("{KW}", "index"), *expected.map { it.replace("{KW}", "INDEX") }.toTypedArray())
    }

    @Test fun testEntityAttrKeyIndexMulti() {
        chkSyms("entity data { key part: integer, id: text; value: integer; index id, value; }",
            "data=DEF_ENTITY|entity[data]|-", "?head=ENTITY|:data|data",
            "part=MEM_ENTITY_ATTR_KEY|entity[data].attr[part]|-", "?head=ENTITY_ATTR|:data.part",
            "id=MEM_ENTITY_ATTR_KEY|entity[data].attr[id]|-", "?head=ENTITY_ATTR|:data.id",
            "value=MEM_ENTITY_ATTR_INDEX|entity[data].attr[value]|-", "?head=ENTITY_ATTR|:data.value",
            "id=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[id]", "?head=ENTITY_ATTR|:data.id",
            "value=MEM_ENTITY_ATTR_INDEX|-|main.rell/entity[data].attr[value]", "?head=ENTITY_ATTR|:data.value",
        )
    }

    @Test fun testObjectAttr() {
        file("module.rell", "object state { x: integer = 123; mutable y: integer = 456; }")

        val state = arrayOf("state=DEF_OBJECT|-|module.rell/object[state]", "?head=OBJECT|:state|state")
        val x = arrayOf("x=MEM_ENTITY_ATTR_NORMAL|-|module.rell/object[state].attr[x]", "?head=OBJECT_ATTR|:state.x")
        val y = arrayOf("y=MEM_ENTITY_ATTR_NORMAL_VAR|-|module.rell/object[state].attr[y]", "?head=OBJECT_ATTR|:state.y")

        chkSymsExpr("state.x", *state, *x)
        chkSymsExpr("state.y", *state, *y)
        chkSymsStmt("state.y = 789;", *state, *y)
    }

    @Test fun testModule() {
        file("lib.rell", "module; namespace ns { function f() = 123; }")
        file("module.rell", "import lib; import dup: lib;")

        val ns = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")
        val f = arrayOf("f=DEF_FUNCTION|-|lib.rell/function[ns.f]", "?head=FUNCTION|lib:ns.f")

        chkSymsExpr("lib.ns.f()",
            "lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib",
            *ns, *f,
        )
        chkSymsExpr("dup.ns.f()",
            "dup=EXPR_IMPORT_ALIAS|-|module.rell/import[dup]", "?head=IMPORT|:dup",
            *ns, *f,
        )
    }
}
