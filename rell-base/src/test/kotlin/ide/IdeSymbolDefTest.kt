/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import kotlin.test.Test

class IdeSymbolDefTest: BaseIdeSymbolTest() {
    @Test fun testNamespace() {
        file("module.rell", "namespace ns { val k = 123; }")

        chkSymsFile("module.rell",
            "ns=DEF_NAMESPACE|namespace[ns]|-",
            "?doc=NAMESPACE|:ns|<namespace> ns",
            "k=DEF_CONSTANT|constant[ns.k]|-",
        )

        val kRef = "k=DEF_CONSTANT|-|module.rell/constant[ns.k]"
        chkSymsExpr("ns.k", "ns=DEF_NAMESPACE|-|module.rell/namespace[ns]", "?head=NAMESPACE|:ns", kRef)
    }

    @Test fun testNamespaceQualified() {
        file("module.rell", "namespace a.b.c { val k = 123; }")
        chkSymsFile("module.rell",
            "a=DEF_NAMESPACE|namespace[a]|-",
            "?head=NAMESPACE|:a",
            "b=DEF_NAMESPACE|namespace[a.b]|-",
            "?head=NAMESPACE|:a.b",
            "c=DEF_NAMESPACE|namespace[a.b.c]|-",
            "?head=NAMESPACE|:a.b.c",
            "k=DEF_CONSTANT|constant[a.b.c.k]|-",
        )
        chkSymsExpr("a.b.c.k",
            "a=DEF_NAMESPACE|-|module.rell/namespace[a]",
            "?head=NAMESPACE|:a",
            "b=DEF_NAMESPACE|-|module.rell/namespace[a.b]",
            "?head=NAMESPACE|:a.b",
            "c=DEF_NAMESPACE|-|module.rell/namespace[a.b.c]",
            "?head=NAMESPACE|:a.b.c",
            "k=DEF_CONSTANT|-|module.rell/constant[a.b.c.k]",
        )
    }

    @Test fun testNamespaceNested() {
        file("module.rell", "namespace a { namespace b { namespace c { val k = 123; } } }")
        chkSymsFile("module.rell",
            "a=DEF_NAMESPACE|namespace[a]|-",
            "?head=NAMESPACE|:a",
            "b=DEF_NAMESPACE|namespace[a.b]|-",
            "?head=NAMESPACE|:a.b",
            "c=DEF_NAMESPACE|namespace[a.b.c]|-",
            "?head=NAMESPACE|:a.b.c",
            "k=DEF_CONSTANT|constant[a.b.c.k]|-",
        )
        chkSymsExpr("a.b.c.k",
            "a=DEF_NAMESPACE|-|module.rell/namespace[a]",
            "?head=NAMESPACE|:a",
            "b=DEF_NAMESPACE|-|module.rell/namespace[a.b]",
            "?head=NAMESPACE|:a.b",
            "c=DEF_NAMESPACE|-|module.rell/namespace[a.b.c]",
            "?head=NAMESPACE|:a.b.c",
            "k=DEF_CONSTANT|-|module.rell/constant[a.b.c.k]",
        )
    }

    @Test fun testNamespaceMultiOneFile() {
        file("module.rell", "namespace a { val x = 123; } namespace a { val y = 456; }")

        chkSymsFile("module.rell",
            "a=DEF_NAMESPACE|namespace[a]|module.rell/namespace[a:1]",
            "?head=NAMESPACE|:a",
            "x=DEF_CONSTANT|constant[a.x]|-",
            "a=DEF_NAMESPACE|namespace[a:1]|module.rell/namespace[a]",
            "?head=NAMESPACE|:a",
            "y=DEF_CONSTANT|constant[a.y]|-",
        )

        val aRef = arrayOf("a=DEF_NAMESPACE|-|module.rell/namespace[a]", "?head=NAMESPACE|:a")
        chkSymsExpr("a.x", *aRef, "x=DEF_CONSTANT|-|module.rell/constant[a.x]")
        chkSymsExpr("a.y", *aRef, "y=DEF_CONSTANT|-|module.rell/constant[a.y]")
    }

    @Test fun testNamespaceMultiManyFiles() {
        file("f1.rell", "namespace a { val x = 123; }")
        file("f2.rell", "namespace a { val y = 456; }")

        chkSymsFile("f1.rell",
            "a=DEF_NAMESPACE|namespace[a]|f2.rell/namespace[a]",
            "?head=NAMESPACE|:a",
            "x=DEF_CONSTANT|constant[a.x]|-",
        )
        chkSymsFile("f2.rell",
            "a=DEF_NAMESPACE|namespace[a]|f1.rell/namespace[a]",
            "?head=NAMESPACE|:a",
            "y=DEF_CONSTANT|constant[a.y]|-",
        )

        val aRef = arrayOf("a=DEF_NAMESPACE|-|f1.rell/namespace[a]", "?head=NAMESPACE|:a")
        chkSymsExpr("a.x", *aRef, "x=DEF_CONSTANT|-|f1.rell/constant[a.x]")
        chkSymsExpr("a.y", *aRef, "y=DEF_CONSTANT|-|f2.rell/constant[a.y]")
    }

    @Test fun testNamespaceMultiLocalLink() {
        file("lib/f1.rell", "namespace a { val x = 123; } val p = a.y;")
        file("lib/f2.rell", "namespace a { val y = 456; } val q = a.x;")

        chkSymsFile("lib/f1.rell",
            "a=DEF_NAMESPACE|namespace[a]|lib/f2.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "x=DEF_CONSTANT|constant[a.x]|-",
            "p=DEF_CONSTANT|constant[p]|-",
            "a=DEF_NAMESPACE|-|lib/f1.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "y=DEF_CONSTANT|-|lib/f2.rell/constant[a.y]",
        )

        chkSymsFile("lib/f2.rell",
            "a=DEF_NAMESPACE|namespace[a]|lib/f1.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "y=DEF_CONSTANT|constant[a.y]|-",
            "a=DEF_NAMESPACE|-|lib/f1.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "x=DEF_CONSTANT|-|lib/f1.rell/constant[a.x]",
        )
    }

    @Test fun testNamespaceMultiNextLinkOneFile() {
        chkSyms("namespace a {}", "a=DEF_NAMESPACE|namespace[a]|-")
        chkSyms("namespace a {} namespace a {}",
            "a=DEF_NAMESPACE|namespace[a]|main.rell/namespace[a:1]", "?head=NAMESPACE|:a",
            "a=DEF_NAMESPACE|namespace[a:1]|main.rell/namespace[a]", "?head=NAMESPACE|:a",
        )
        chkSyms("namespace a {} namespace a {} namespace a {}",
            "a=DEF_NAMESPACE|namespace[a]|main.rell/namespace[a:1]", "?head=NAMESPACE|:a",
            "a=DEF_NAMESPACE|namespace[a:1]|main.rell/namespace[a:2]", "?head=NAMESPACE|:a",
            "a=DEF_NAMESPACE|namespace[a:2]|main.rell/namespace[a]", "?head=NAMESPACE|:a",
        )
    }

    @Test fun testNamespaceMultiNextLinkManyFiles() {
        file("lib/f1.rell", "namespace a {}")
        file("lib/f2.rell", "namespace a {}")
        file("lib/f3.rell", "namespace a {}")
        chkSymsFile("lib/f1.rell", "a=DEF_NAMESPACE|namespace[a]|lib/f2.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        chkSymsFile("lib/f2.rell", "a=DEF_NAMESPACE|namespace[a]|lib/f3.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        chkSymsFile("lib/f3.rell", "a=DEF_NAMESPACE|namespace[a]|lib/f1.rell/namespace[a]", "?head=NAMESPACE|lib:a")
    }

    @Test fun testNamespaceMultiNextLinkManyFilesQualified() {
        file("lib/f1.rell", "namespace a {}")
        file("lib/f2.rell", "namespace a { namespace b {} }")
        file("lib/f3.rell", "namespace a.b {}")
        file("lib/f4.rell", "namespace a {}")
        file("lib/f5.rell", "namespace a { namespace b {} }")

        val aId = "namespace[a]"
        chkSymsFile("lib/f1.rell", "a=DEF_NAMESPACE|$aId|lib/f2.rell/$aId", "?head=NAMESPACE|lib:a")
        chkSymsFile("lib/f2.rell",
            "a=DEF_NAMESPACE|$aId|lib/f3.rell/$aId", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|lib/f3.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b",
        )
        chkSymsFile("lib/f3.rell",
            "a=DEF_NAMESPACE|$aId|lib/f4.rell/$aId", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|lib/f5.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b",
        )
        chkSymsFile("lib/f4.rell", "a=DEF_NAMESPACE|$aId|lib/f5.rell/$aId", "?head=NAMESPACE|lib:a")
        chkSymsFile("lib/f5.rell",
            "a=DEF_NAMESPACE|$aId|lib/f1.rell/$aId", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|lib/f2.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b",
        )
    }

    @Test fun testConstant() {
        file("module.rell", "val A = 123;")

        chkSymsFile("module.rell",
            "A=DEF_CONSTANT|constant[A]|-",
            "?doc=CONSTANT|:A|<val> A: [integer] = 123",
        )

        chkSymsExpr("A", "A=DEF_CONSTANT|-|module.rell/constant[A]", "?head=CONSTANT|:A")
    }

    @Test fun testEntity() {
        file("lib.rell", "entity user { mutable name; }")

        chkSymsFile("lib.rell", "user=DEF_ENTITY|entity[user]|-", "?head=ENTITY|:user|user")

        val entityRef = arrayOf("user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user")
        chkSymsType("user", *entityRef)
        chkSymsType("user?", *entityRef)
        chkSymsType("struct<user>", *entityRef)
        chkSymsExpr("user @* {}", *entityRef)
        chkSymsStmt("create user('Bob');", *entityRef)
        chkSymsStmt("delete user @* {};", *entityRef)
        chkSymsStmt("update user @* {} ( name = 'Bob' );", *entityRef)
    }

    @Test fun testEntityBlockTransaction() {
        file("lib.rell", "@external module; namespace ns { entity block; entity transaction; }")
        file("module.rell", "import lib.*;")

        val blockDoc = "?head=ENTITY|lib:ns.block|blocks"
        val txDoc = "?head=ENTITY|lib:ns.transaction|transactions"

        chkSymsFile("lib.rell",
            "block=DEF_ENTITY|entity[ns.block]|-", blockDoc,
            "transaction=DEF_ENTITY|entity[ns.transaction]|-", txDoc,
        )

        chkSymsType("ns.block", "block=DEF_ENTITY|-|lib.rell/entity[ns.block]", blockDoc)
        chkSymsType("ns.transaction", "transaction=DEF_ENTITY|-|lib.rell/entity[ns.transaction]", txDoc)
    }

    @Test fun testObject() {
        file("lib.rell", "object state { mutable x: integer = 123; }")
        chkSymsFile("lib.rell", "state=DEF_OBJECT|object[state]|-", "?head=OBJECT|:state|state")

        val stateRef = arrayOf("state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|:state|state")
        chkSymsExpr("state.x", *stateRef)
        chkSymsType("struct<state>", *stateRef)
    }

    @Test fun testEnum() {
        file("module.rell", "enum colors { red, green, blue }")
        file("utils.rell", "function c() = colors.red;")

        chkSymsFile("module.rell",
            "colors=DEF_ENUM|enum[colors]|-",
            "?doc=ENUM|:colors|<enum> colors",
            "red=MEM_ENUM_VALUE|enum[colors].value[red]|-",
            "?doc=ENUM_VALUE|:colors.red|red",
            "green=MEM_ENUM_VALUE|enum[colors].value[green]|-",
            "?doc=ENUM_VALUE|:colors.green|green",
            "blue=MEM_ENUM_VALUE|enum[colors].value[blue]|-",
            "?doc=ENUM_VALUE|:colors.blue|blue",
        )

        val colors = arrayOf("colors=DEF_ENUM|-|module.rell/enum[colors]", "?head=ENUM|:colors")
        val valueRef = "MEM_ENUM_VALUE|-|module.rell/enum[colors].value"
        val valueHead = "ENUM_VALUE|:colors"
        val prop = "MEM_SYS_PROPERTY_PURE|-|-"

        chkSymsType("colors", *colors)

        chkSymsExpr("colors.red", *colors, "red=$valueRef[red]", "?head=$valueHead.red")
        chkSymsExpr("colors.green", *colors, "green=$valueRef[green]", "?head=$valueHead.green")
        chkSymsExpr("colors.blue", *colors, "blue=$valueRef[blue]", "?head=$valueHead.blue")

        chkSymsExpr("colors.red.name", *colors, "red=$valueRef[red]", "?head=$valueHead.red", "name=$prop")
        chkSymsExpr("colors.red.value", *colors, "red=$valueRef[red]", "?head=$valueHead.red", "value=$prop")

        chkSymsExpr("colors.values()", *colors, "values=DEF_FUNCTION_SYSTEM|-|-")
        chkSymsExpr("colors.value('red')", *colors, "value=DEF_FUNCTION_SYSTEM|-|-")

        chkSymsExpr("when (c()) { red -> 1; green -> 2; else -> 0 }",
            "red=$valueRef[red]", "?head=$valueHead.red",
            "green=$valueRef[green]", "?head=$valueHead.green",
        )
    }

    @Test fun testFunction() {
        chkSyms("function f() {}", "f=DEF_FUNCTION|function[f]|-", "?head=FUNCTION|:f")

        chkSyms("function f(x: integer) = x * x;",
            "f=DEF_FUNCTION|function[f]|-", "?head=FUNCTION|:f",
            "x=LOC_PARAMETER|function[f].param[x]|-", "?head=PARAMETER|x",
            "x=LOC_PARAMETER|-|main.rell/function[f].param[x]", "?head=PARAMETER|x",
            "x=LOC_PARAMETER|-|main.rell/function[f].param[x]", "?head=PARAMETER|x",
        )
    }

    @Test fun testFunctionAbstract() {
        file("module.rell", "abstract module;")
        chkSyms("abstract function f();", "f=DEF_FUNCTION_ABSTRACT|function[f]|-", "?head=FUNCTION|:f", ide = true)
    }

    @Test fun testFunctionOverride() {
        file("lib.rell", "abstract module; abstract function g();")

        chkSymsFile("lib.rell", "g=DEF_FUNCTION_ABSTRACT|function[g]|-", "?head=FUNCTION|lib:g")

        chkSyms("import lib; override function lib.g() {}",
            "g=DEF_FUNCTION_ABSTRACT|-|lib.rell/function[g]",
            "?head=FUNCTION|lib:g",
        )
    }

    @Test fun testFunctionExtendable() {
        file("lib.rell", "module; namespace ns { @extendable function f(){} }")
        chkSymsFile("lib.rell", "f=DEF_FUNCTION_EXTENDABLE|function[ns.f]|-")

        val fnRef = arrayOf("f=DEF_FUNCTION_EXTENDABLE|-|lib.rell/function[ns.f]", "?head=FUNCTION|lib:ns.f")
        val nsRef = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")

        chkSyms("import lib.{ns.*}; function g() { f(); }", *fnRef)
        chkSyms("import lib.{ns.*}; @extend(f) function g() {}", *fnRef)
        chkSyms("import lib; @extend(lib.ns.f) function g() {}", *fnRef)
        chkSyms("import lib.{ns.*}; @extend(f) function() {}", *fnRef)
        chkSyms("import lib; @extend(lib.ns.f) function() {}", *fnRef)
        chkSyms("import lib.*; @extend(ns.f) function() {}", *fnRef)
        chkSyms("import lib.{ns}; @extend(ns.f) function() {}", "ns=*|-|*", *nsRef, *fnRef)

        chkSyms("import lib; @extend(lib.ns.f) function g() {}",
            "lib=*|*|*",
            "extend=MOD_ANNOTATION|-|-",
            "lib=DEF_IMPORT_MODULE|-|*",
            *nsRef,
            *fnRef,
            "g=DEF_FUNCTION_EXTEND|function[g]|-",
        )

        chkSyms("import lib; @extend(lib.ns.foo) function g() {}",
            "lib=*|*|*",
            "extend=MOD_ANNOTATION|-|-",
            "lib=DEF_IMPORT_MODULE|-|*",
            *nsRef,
            "foo=UNKNOWN|-|-",
            "g=DEF_FUNCTION_EXTEND|function[g]|-",
            err = "unknown_name:lib.ns.foo",
        )

        chkSyms("@extend(foo.bar) function g() {}",
            "extend=MOD_ANNOTATION|-|-",
            "foo=UNKNOWN|-|-",
            "bar=UNKNOWN|-|-",
            "g=DEF_FUNCTION_EXTEND|function[g]|-",
            err = "unknown_name:foo",
        )
    }

    @Test fun testFunctionMisc() {
        chkSyms("function a.b.c() {}",
            "a=UNKNOWN|-|-", "b=UNKNOWN|-|-", "c=UNKNOWN|-|-",
            err = "fn:qname_no_override:a.b.c",
        )

        chkSyms("@extendable function a.b.c() {}",
            "extendable=MOD_ANNOTATION|-|-",
            "a=UNKNOWN|-|-", "b=UNKNOWN|-|-", "c=UNKNOWN|-|-",
            err = "fn:qname_no_override:a.b.c",
        )

        chkSyms("abstract function a.b.c();",
            "a=UNKNOWN|-|-", "b=UNKNOWN|-|-", "c=UNKNOWN|-|-",
            err = "[fn:abstract:non_abstract_module::a.b.c][fn:qname_no_override:a.b.c]",
            ide = true
        )
    }

    @Test fun testFunctionRef() {
        tst.testLib = true
        file("lib.rell", "abstract module; abstract function p();")
        file("module.rell", """
            import lib;
            function f() {}
            @extendable function g() {}
            @extend(g) function h() {}
            override function lib.p() {}
            operation op() {}
            query q() = 123;
            struct rec { x: integer = 123; }
        """)

        chkSymsExpr("f()", "f=DEF_FUNCTION|-|module.rell/function[f]", "?head=FUNCTION|:f")
        chkSymsExpr("g()", "g=DEF_FUNCTION_EXTENDABLE|-|module.rell/function[g]", "?head=FUNCTION|:g")
        chkSymsExpr("h()", "h=DEF_FUNCTION_EXTEND|-|module.rell/function[h]", "?head=FUNCTION|:h")
        chkSymsExpr("lib.p()", "lib=DEF_IMPORT_MODULE|-|*", "p=DEF_FUNCTION_ABSTRACT|-|lib.rell/function[p]", "?head=FUNCTION|lib:p")
        chkSymsExpr("op()", "op=DEF_OPERATION|-|module.rell/operation[op]", "?head=OPERATION|:op|op")
        chkSymsExpr("q()", "q=DEF_QUERY|-|module.rell/query[q]", "?head=QUERY|:q|q")
        chkSymsExpr("rec()", "rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
    }

    @Test fun testFunctionRefSys() {
        chkSymsExpr("print()", "print=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:print")
        chkSymsExpr("log()", "log=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:log")
        chkSymsExpr("'hello'.size()", "size=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:text.size")
        chkSymsExpr("'hello'.index_of('world')", "index_of=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:text.index_of")

        chkSymsExpr("crypto.sha256(x'1234')",
            "crypto=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell:crypto",
            "sha256=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:crypto.sha256",
        )
    }

    @Test fun testFunctionParamNamed() {
        file("module.rell", "function f(x: integer) { return x*2; }")
        val xId = "function[f].param[x]"
        chkSymsFile("module.rell", "x=LOC_PARAMETER|$xId|-", "x=LOC_PARAMETER|-|module.rell/$xId", "?head=PARAMETER|x")
        chkSymsExpr("f(x = 123)", "x=EXPR_CALL_ARG|-|module.rell/$xId", "?head=PARAMETER|x")
    }

    @Test fun testFunctionParamAnonSimple() {
        file("a.rell", "enum color { red }")
        file("b.rell", "function f(color) { return color; }")
        chkSymsFile("b.rell",
            "color=DEF_ENUM|function[f].param[color]|a.rell/enum[color]", "?head=ENUM|:color",
            "color=LOC_PARAMETER|-|b.rell/function[f].param[color]", "?doc=PARAMETER|color|color: [color]",
        )
        chkSymsExpr("f(color = color.red)",
            "color=EXPR_CALL_ARG|-|b.rell/function[f].param[color]", "?doc=PARAMETER|color|color: [color]",
            "color=DEF_ENUM|-|a.rell/enum[color]", "?head=ENUM|:color",
        )
    }

    @Test fun testFunctionParamAnonComplex() {
        file("a.rell", "namespace ns { enum color { red } }")
        file("b.rell", "function f(ns.color) { return color; }")
        chkSymsFile("b.rell",
            "color=DEF_ENUM|function[f].param[color]|a.rell/enum[ns.color]", "?head=ENUM|:ns.color",
            "color=LOC_PARAMETER|-|b.rell/function[f].param[color]", "?doc=PARAMETER|color|color: [ns.color]",
        )
        chkSymsExpr("f(color = ns.color.red)",
            "color=EXPR_CALL_ARG|-|b.rell/function[f].param[color]", "?doc=PARAMETER|color|color: [ns.color]",
            "color=DEF_ENUM|-|a.rell/enum[ns.color]", "?head=ENUM|:ns.color",
        )
    }

    @Test fun testOperation() {
        tst.testLib = true
        file("lib.rell", "operation op(x: integer) {}")

        chkSymsFile("lib.rell",
            "op=DEF_OPERATION|operation[op]|-",
            "?doc=OPERATION|:op|op|<operation> op(\n\tx: [integer]\n)",
        )

        chkSymsExpr("op(123)", "op=DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|:op|op")
        chkSymsType("struct<op>", "op=DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|:op|op")
    }

    @Test fun testOperationParams() {
        tst.testLib = true
        file("def.rell", "operation op(x: integer, y: decimal = 123, z: text) { if (x>0 and y>0 and z!='') {} }")

        chkSymsFile("def.rell",
            "op=DEF_OPERATION|operation[op]|-",
            // Type-adapted default values (integer 123 -> decimal) cannot be resolved as compile-time constants.
            "?doc=OPERATION|:op|op|<operation> op(\n\tx: [integer],\n\ty: [decimal] = <...>,\n\tz: [text]\n)",
            "x=LOC_PARAMETER|operation[op].param[x]|-",
            "?doc=PARAMETER|x|x: [integer]",
            "y=LOC_PARAMETER|operation[op].param[y]|-",
            "?doc=PARAMETER|y|y: [decimal] = <...>",
            "z=LOC_PARAMETER|operation[op].param[z]|-",
            "?doc=PARAMETER|z|z: [text]",
            "x=LOC_PARAMETER|-|def.rell/operation[op].param[x]",
            "y=LOC_PARAMETER|-|def.rell/operation[op].param[y]",
            "z=LOC_PARAMETER|-|def.rell/operation[op].param[z]",
        )

        chkSymsExpr("op(x = 123, y = 456, z = 'Hello')",
            "op=DEF_OPERATION|-|def.rell/operation[op]", "?head=OPERATION|:op|op",
            "x=EXPR_CALL_ARG|-|def.rell/operation[op].param[x]", "?head=PARAMETER|x",
            "y=EXPR_CALL_ARG|-|def.rell/operation[op].param[y]", "?head=PARAMETER|y",
            "z=EXPR_CALL_ARG|-|def.rell/operation[op].param[z]", "?head=PARAMETER|z",
        )
    }

    @Test fun testQuery() {
        file("lib.rell", "query ask() = 0;")
        chkSymsFile("lib.rell", "ask=DEF_QUERY|query[ask]|-", "?head=QUERY|:ask|ask")
        chkSymsExpr("ask()", "ask=DEF_QUERY|-|lib.rell/query[ask]", "?head=QUERY|:ask|ask")
    }

    @Test fun testQueryParams() {
        file("def.rell", "query ask(x: integer) = x + 1;")
        chkSymsFile("def.rell",
            "ask=DEF_QUERY|query[ask]|-", "?head=QUERY|:ask|ask",
            "x=LOC_PARAMETER|query[ask].param[x]|-", "?head=PARAMETER|x",
            "x=LOC_PARAMETER|-|def.rell/query[ask].param[x]", "?head=PARAMETER|x",
        )
        chkSymsExpr("ask(x = 123)",
            "ask=DEF_QUERY|-|def.rell/query[ask]", "?head=QUERY|:ask|ask",
            "x=EXPR_CALL_ARG|-|def.rell/query[ask].param[x]", "?head=PARAMETER|x",
        )
    }

    @Test fun testStruct() {
        file("module.rell", "struct rec {}")
        chkSymsFile("module.rell", "rec=DEF_STRUCT|struct[rec]|-", "?doc=STRUCT|:rec|<struct> rec")

        val recRef = arrayOf("rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
        chkSymsExpr("rec()", *recRef)
        chkSymsType("rec", *recRef)
    }

    @Test fun testStructAttr() {
        file("module.rell", "namespace ns { struct rec {} }")

        val ns = arrayOf("ns=DEF_NAMESPACE|-|module.rell/namespace[ns]", "?doc=NAMESPACE|:ns|<namespace> ns")

        chkSyms("struct data { x: text; }",
            "x=MEM_STRUCT_ATTR|struct[data].attr[x]|-",
            "?doc=STRUCT_ATTR|:data.x|x: [text]",
        )

        chkSyms("struct data { x: text = 'Hello'; }",
            "x=MEM_STRUCT_ATTR|struct[data].attr[x]|-",
            "?doc=STRUCT_ATTR|:data.x|x: [text] = \"Hello\"",
        )

        chkSyms("struct data { text; }",
            "text=DEF_TYPE|struct[data].attr[text]|-",
            "?doc=TYPE|rell:text|<type> text",
        )

        chkSyms("struct data { ns.rec; }", *ns,
            "rec=DEF_STRUCT|struct[data].attr[rec]|module.rell/struct[ns.rec]",
            "?doc=STRUCT|:ns.rec|<struct> rec",
        )

        chkSyms("struct data { integer = 123; }",
            "integer=DEF_TYPE|struct[data].attr[integer]|-",
            "?doc=TYPE|rell:integer|<type> integer",
        )

        chkSyms("struct data { text?; }",
            "text=DEF_TYPE|struct[data].attr[text]|-",
            "?doc=TYPE|rell:text|<type> text",
        )

        chkSyms("struct data { ns.rec?; }", *ns,
            "rec=DEF_STRUCT|struct[data].attr[rec]|module.rell/struct[ns.rec]",
            "?doc=STRUCT|:ns.rec|<struct> rec",
        )

        chkSyms("struct data { text? = 'Hello'; }",
            "text=DEF_TYPE|struct[data].attr[text]|-",
            "?doc=TYPE|rell:text|<type> text",
        )

        chkSyms("struct data { ns.rec? = null; }", *ns,
            "rec=DEF_STRUCT|struct[data].attr[rec]|module.rell/struct[ns.rec]",
            "?doc=STRUCT|:ns.rec|<struct> rec",
        )

        chkSyms("struct data { x: integer; }",
            "x=MEM_STRUCT_ATTR|struct[data].attr[x]|-",
            "?doc=STRUCT_ATTR|:data.x|x: [integer]",
        )

        chkSyms("struct data { mutable x: integer; }",
            "x=MEM_STRUCT_ATTR_VAR|struct[data].attr[x]|-",
            "?doc=STRUCT_ATTR|:data.x|<mutable> x: [integer]",
        )

        chkSyms("struct data { mutable x: integer = 123; }",
            "x=MEM_STRUCT_ATTR_VAR|struct[data].attr[x]|-",
            "?doc=STRUCT_ATTR|:data.x|<mutable> x: [integer] = 123",
        )
    }

    @Test fun testStructAttrRefNamed() {
        file("module.rell", "struct rec { mutable x: integer = 0; y: integer = 0; }")
        file("utils.rell", "function r() = rec();")

        chkSymsFile("module.rell",
            "x=MEM_STRUCT_ATTR_VAR|struct[rec].attr[x]|-",
            "?doc=STRUCT_ATTR|:rec.x|<mutable> x: [integer] = 0",
            "y=MEM_STRUCT_ATTR|struct[rec].attr[y]|-",
            "?doc=STRUCT_ATTR|:rec.y|y: [integer] = 0",
        )

        val recRef = arrayOf("rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
        val xRef = arrayOf("x=MEM_STRUCT_ATTR_VAR|-|module.rell/struct[rec].attr[x]", "?head=STRUCT_ATTR|:rec.x")

        chkSymsExpr("rec(x = 123)", *recRef, *xRef)
        chkSymsExpr("rec(y = 456)", *recRef, "y=MEM_STRUCT_ATTR|-|module.rell/struct[rec].attr[y]")
        chkSymsExpr("rec(foo = 1)", *recRef, "foo=UNKNOWN|-|-", err = "attr_unknown_name:foo")

        chkSymsExpr("r().x", *xRef)
        chkSymsStmt("r().x = 0;", *xRef)
    }

    @Test fun testStructAttrRefAnonSimple() {
        file("a.rell", "enum color { red }")
        file("b.rell", "struct rec { mutable color; }")
        file("utils.rell", "function r() = rec(color.red);")

        chkSymsFile("b.rell",
            "color=DEF_ENUM|struct[rec].attr[color]|a.rell/enum[color]", "?doc=ENUM|:color|<enum> color",
        )

        val enumRef = arrayOf("color=DEF_ENUM|-|a.rell/enum[color]", "?head=ENUM|:color")
        val attrRef = arrayOf(
            "color=MEM_STRUCT_ATTR_VAR|-|b.rell/struct[rec].attr[color]",
            "?doc=STRUCT_ATTR|:rec.color|<mutable> color: [color]",
        )

        chkSymsExpr("rec(color = color.red)", *attrRef, *enumRef)
        chkSymsExpr("r().color", *attrRef)
        chkSymsStmt("r().color = color.red;", *attrRef, *enumRef)
    }

    @Test fun testStructAttrRefAnonComplex() {
        file("a.rell", "namespace ns { enum color { red } }")
        file("b.rell", "struct rec { mutable ns.color; }")
        file("utils.rell", "function r() = rec(ns.color.red);")

        chkSymsFile("b.rell",
            "ns=DEF_NAMESPACE|-|a.rell/namespace[ns]",
            "?doc=NAMESPACE|:ns|<namespace> ns",
            "color=DEF_ENUM|struct[rec].attr[color]|a.rell/enum[ns.color]",
            "?doc=ENUM|:ns.color|<enum> color",
        )

        val attrRef = arrayOf(
            "color=MEM_STRUCT_ATTR_VAR|-|b.rell/struct[rec].attr[color]",
            "?doc=STRUCT_ATTR|:rec.color|<mutable> color: [ns.color]",
        )
        val enumRef = arrayOf("color=DEF_ENUM|-|a.rell/enum[ns.color]", "?head=ENUM|:ns.color")

        chkSymsExpr("rec(color = ns.color.red)", *attrRef, *enumRef)
        chkSymsExpr("r().color", *attrRef)
        chkSymsStmt("r().color = ns.color.red;", *attrRef, *enumRef)
    }

    @Test fun testAttrHeader() {
        file("lib.rell", "namespace x { namespace y { struct z {} } }")

        chkSyms("function f(x: text) {}",
            "x=LOC_PARAMETER|function[f].param[x]|-", "?head=PARAMETER|x",
            "text=DEF_TYPE|-|-", "?head=TYPE|rell:text",
        )
        chkSyms("function f(text) {}", "text=DEF_TYPE|function[f].param[text]|-", "?head=TYPE|rell:text")

        chkSyms("function f(a: x.y.z) {}",
            "a=LOC_PARAMETER|function[f].param[a]|-", "?head=PARAMETER|a",
            "x=DEF_NAMESPACE|-|lib.rell/namespace[x]", "?head=NAMESPACE|:x",
            "y=DEF_NAMESPACE|-|lib.rell/namespace[x.y]", "?head=NAMESPACE|:x.y",
            "z=DEF_STRUCT|-|lib.rell/struct[x.y.z]", "?head=STRUCT|:x.y.z",
        )

        chkSyms("function f(x.y.z) {}",
            "x=DEF_NAMESPACE|-|lib.rell/namespace[x]", "?head=NAMESPACE|:x",
            "y=DEF_NAMESPACE|-|lib.rell/namespace[x.y]", "?head=NAMESPACE|:x.y",
            "z=DEF_STRUCT|function[f].param[z]|lib.rell/struct[x.y.z]", "?head=STRUCT|:x.y.z",
        )
    }

    @Test fun testAttrEntity() {
        file("module.rell", "namespace ns { entity rec {} }")

        val entityDef = "data=DEF_ENTITY|entity[data]|-"
        val ns = arrayOf("ns=DEF_NAMESPACE|-|module.rell/namespace[ns]", "?head=NAMESPACE|:ns")
        val rec = arrayOf("rec=DEF_ENTITY|entity[data].attr[rec]|module.rell/entity[ns.rec]", "?head=ENTITY|:ns.rec|ns.rec")
        val x = arrayOf("x=MEM_ENTITY_ATTR_NORMAL|entity[data].attr[x]|-", "?head=ENTITY_ATTR|:data.x")
        val xVar = arrayOf("x=MEM_ENTITY_ATTR_NORMAL_VAR|entity[data].attr[x]|-", "?head=ENTITY_ATTR|:data.x")

        chkSyms("entity data { x: text; }", entityDef, *x, "text=DEF_TYPE|-|-", "?head=TYPE|rell:text")
        chkSyms("entity data { x: text = 'Hello'; }", entityDef, *x, "text=DEF_TYPE|-|-", "?head=TYPE|rell:text")
        chkSyms("entity data { text; }", entityDef, "text=DEF_TYPE|entity[data].attr[text]|-", "?head=TYPE|rell:text")
        chkSyms("entity data { ns.rec; }", entityDef, *ns, *rec)

        chkSyms("entity data { integer = 123; }",
            entityDef,
            "integer=DEF_TYPE|entity[data].attr[integer]|-",
            "?head=TYPE|rell:integer",
        )

        chkSyms("entity data { ns.rec = ns.rec @ {}; }",
            entityDef,
            *ns, *rec,
            *ns, "rec=DEF_ENTITY|-|module.rell/entity[ns.rec]", "?head=ENTITY|:ns.rec|ns.rec",
        )

        chkSyms("entity data { x: integer; }", entityDef, *x, "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer")
        chkSyms("entity data { mutable x: integer; }", entityDef, *xVar, "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer")
    }

    @Test fun testAttrEntityKeyIndex() {
        val entityDef = "data=DEF_ENTITY|entity[data]|-"

        val xHead = "?head=ENTITY_ATTR|:data.x"
        chkSyms("entity data { x: integer; }", entityDef, "x=MEM_ENTITY_ATTR_NORMAL|entity[data].attr[x]|-", xHead)
        chkSyms("entity data { key x: integer; }", entityDef, "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", xHead)
        chkSyms("entity data { index x: integer; }", entityDef, "x=MEM_ENTITY_ATTR_INDEX|entity[data].attr[x]|-", xHead)

        chkSyms("entity data { x: integer; key x; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", xHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
        )

        chkSyms("entity data { x: integer; index x; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_INDEX|entity[data].attr[x]|-", xHead,
            "x=MEM_ENTITY_ATTR_INDEX|-|main.rell/entity[data].attr[x]", xHead,
        )

        chkSyms("entity data { mutable x: integer; key x; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_KEY_VAR|entity[data].attr[x]|-", xHead,
            "x=MEM_ENTITY_ATTR_KEY_VAR|-|main.rell/entity[data].attr[x]", xHead,
        )

        chkSyms("entity data { mutable x: integer; index x; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_INDEX_VAR|entity[data].attr[x]|-", xHead,
            "x=MEM_ENTITY_ATTR_INDEX_VAR|-|main.rell/entity[data].attr[x]", xHead,
        )

        val yHead = "?head=ENTITY_ATTR|:data.y"
        chkSyms("entity data { x: integer; y: text; key x; index y; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", xHead,
            "y=MEM_ENTITY_ATTR_INDEX|entity[data].attr[y]|-", yHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
            "y=MEM_ENTITY_ATTR_INDEX|-|main.rell/entity[data].attr[y]", yHead,
        )

        chkSyms("entity data { x: integer; y: text; key x; index y; key x, y; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", xHead,
            "y=MEM_ENTITY_ATTR_KEY|entity[data].attr[y]|-", yHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
            "y=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[y]", yHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
            "y=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[y]", yHead,
        )

        chkSyms("entity data { x: integer; y: text; key x; index y; index x, y; }",
            entityDef,
            "x=MEM_ENTITY_ATTR_KEY|entity[data].attr[x]|-", xHead,
            "y=MEM_ENTITY_ATTR_INDEX|entity[data].attr[y]|-", yHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
            "y=MEM_ENTITY_ATTR_INDEX|-|main.rell/entity[data].attr[y]", yHead,
            "x=MEM_ENTITY_ATTR_KEY|-|main.rell/entity[data].attr[x]", xHead,
            "y=MEM_ENTITY_ATTR_INDEX|-|main.rell/entity[data].attr[y]", yHead,
        )
    }

    @Test fun testVarSimple() {
        file("module.rell", "namespace ns { struct rec {} }")

        val ns = arrayOf("ns=DEF_NAMESPACE|-|module.rell/namespace[ns]", "?head=NAMESPACE|:ns")
        val text = arrayOf("text=DEF_TYPE|-|-", "?head=TYPE|rell:text")

        chkSymsStmt("var x: text;", "x=LOC_VAR|-|-", "?doc=VAR|x|<var> x: [text]", *text)
        chkSymsStmt("var x: text = 'Hello';", "x=LOC_VAR|-|-", "?doc=VAR|x|<var> x: [text]", *text)

        chkSymsStmt("var text; text = ''; text.size();",
            "text=DEF_TYPE|-|-", "?head=TYPE|rell:text",
            "text=LOC_VAR|-|local[text:0]", "?doc=VAR|text|<var> text: [text]",
            "text=LOC_VAR|-|local[text:0]", "?doc=VAR|text|<var> text: [text]",
        )
        chkSymsStmt("var text = 123; text.abs();",
            "text=LOC_VAR|-|-", "?doc=VAR|text|<var> text: [integer]",
            "text=LOC_VAR|-|local[text:0]", "?doc=VAR|text|<var> text: [integer]",
        )

        chkSymsStmt("var ns.rec; rec = ns.rec(); rec.hash();",
            *ns, "rec=DEF_STRUCT|-|module.rell/struct[ns.rec]", "?head=STRUCT|:ns.rec",
            "rec=LOC_VAR|-|local[rec:0]", "?doc=VAR|rec|<var> rec: [ns.rec]",
            *ns, "rec=DEF_STRUCT|-|module.rell/struct[ns.rec]", "?head=STRUCT|:ns.rec",
            "rec=LOC_VAR|-|local[rec:0]", "?doc=VAR|rec|<var> rec: [ns.rec]",
        )
    }

    @Test fun testVarComplex() {
        val code = "val (x, (y, z)) = (123, (456, 789))"
        val xyz = arrayOf(
            "x=LOC_VAL|-|-", "?doc=VAR|x|<val> x: [integer]",
            "y=LOC_VAL|-|-", "?doc=VAR|y|<val> y: [integer]",
            "z=LOC_VAL|-|-", "?doc=VAR|z|<val> z: [integer]",
        )
        chkSymsStmt("$code; return x;", *xyz, "x=LOC_VAL|-|local[x:0]", "?doc=VAR|x|<val> x: [integer]")
        chkSymsStmt("$code; return y;", *xyz, "y=LOC_VAL|-|local[y:0]", "?doc=VAR|y|<val> y: [integer]")
        chkSymsStmt("$code; return z;", *xyz, "z=LOC_VAL|-|local[z:0]", "?doc=VAR|z|<val> z: [integer]")
    }

    @Test fun testForVarSimple() {
        val xDef = arrayOf("x=LOC_VAL|-|-", "?doc=VAR|x|<val> x: [integer]")
        val xRef = arrayOf("x=LOC_VAL|-|local[x:0]", "?doc=VAR|x|<val> x: [integer]")
        chkSymsStmt("for (x in [0]) { print(x); }", *xDef, *xRef)
        chkSymsStmt("for (x: integer in [0]) { print(x); }", *xDef, *xRef)

        chkSymsStmt("for (integer in ['']) { print(integer); }",
            "integer=LOC_VAL|-|-", "?doc=VAR|integer|<val> integer: [text]",
            "integer=LOC_VAL|-|local[integer:0]", "?doc=VAR|integer|<val> integer: [text]",
        )
    }

    @Test fun testForVarComplex() {
        val code = "for ((x, (y, z)) in [(123, (456, 789))])"
        val xyz = arrayOf(
            "x=LOC_VAL|-|-", "?doc=VAR|x|<val> x: [integer]",
            "y=LOC_VAL|-|-", "?doc=VAR|y|<val> y: [integer]",
            "z=LOC_VAL|-|-", "?doc=VAR|z|<val> z: [integer]",
        )
        chkSymsStmt("$code { print(x); }", *xyz, "x=LOC_VAL|-|local[x:0]", "?doc=VAR|x|<val> x: [integer]")
        chkSymsStmt("$code { print(y); }", *xyz, "y=LOC_VAL|-|local[y:0]", "?doc=VAR|y|<val> y: [integer]")
        chkSymsStmt("$code { print(z); }", *xyz, "z=LOC_VAL|-|local[z:0]", "?doc=VAR|z|<val> z: [integer]")
    }

    @Test fun testAnnotation() {
        //TODO support annotation docs
        chkSyms("@log entity data {}", "log=MOD_ANNOTATION|-|-", "?doc=-")

        chkSyms("@external('') entity data {}", "external=MOD_ANNOTATION|-|-", "?doc=-", err = "ann:external:invalid:")
        chkSyms("@test entity data {}", "test=UNKNOWN|-|-", "?doc=-", err = "modifier:invalid:ann:test")

        chkSyms("@mount('foo') entity data {}", "mount=MOD_ANNOTATION|-|-", "?doc=-")
        chkSyms("@mount('') entity data {}", "mount=MOD_ANNOTATION|-|-", "?doc=-", err = "ann:mount:empty:ENTITY")
        chkSyms("@mount() entity data {}", "mount=MOD_ANNOTATION|-|-", "?doc=-", err = "ann:mount:arg_count:0")
        chkSyms("@mount entity data {}", "mount=MOD_ANNOTATION|-|-", "?doc=-", err = "ann:mount:arg_count:0")
        chkSyms("@mount(123) entity data {}", "mount=MOD_ANNOTATION|-|-", "?doc=-", err = "ann:mount:arg_type:integer")

        chkSyms("entity data(log) {}", "log=MOD_ANNOTATION_LEGACY|-|-", "?doc=-", warn = "ann:legacy:log")
        chkSyms("entity data(foo) {}", "foo=UNKNOWN|-|-", "?doc=-", warn = "ann:legacy:foo", err = "entity_ann_bad:foo")
    }

    @Test fun testTypeName() {
        val defs = """
            entity data { name; }
            struct rec { x: integer; }
            enum colors { red, green, blue }
        """
        file("lib.rell", "@mount('lib') module; $defs")
        file("module.rell", "import lib; $defs")

        chkSymsType("data", "data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        chkSymsType("rec", "rec=DEF_STRUCT|-|module.rell/struct[rec]", "?head=STRUCT|:rec")
        chkSymsType("colors", "colors=DEF_ENUM|-|module.rell/enum[colors]", "?head=ENUM|:colors")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]")
        chkSymsType("lib.data", *lib, "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|lib.data")
        chkSymsType("lib.rec", *lib, "rec=DEF_STRUCT|-|lib.rell/struct[rec]", "?head=STRUCT|lib:rec")
        chkSymsType("lib.colors", *lib, "colors=DEF_ENUM|-|lib.rell/enum[colors]", "?head=ENUM|lib:colors")
    }

    @Test fun testTypeMirrorStruct() {
        val defs = """
            entity data { name; }
            object state { mutable x: integer = 0; }
            operation op() {}
        """
        file("lib.rell", "@mount('lib') module; $defs")
        file("module.rell", "import lib; $defs")

        chkSymsType("struct<data>", "data=DEF_ENTITY|-|module.rell/entity[data]", "?head=ENTITY|:data|data")
        chkSymsType("struct<state>", "state=DEF_OBJECT|-|module.rell/object[state]", "?head=OBJECT|:state|state")
        chkSymsType("struct<op>", "op=DEF_OPERATION|-|module.rell/operation[op]", "?head=OPERATION|:op|op")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib")
        chkSymsType("struct<lib.data>", *lib, "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|lib.data")
        chkSymsType("struct<lib.state>", *lib, "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|lib.state")
        chkSymsType("struct<lib.op>", *lib, "op=DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|lib:op|lib.op")
    }

    @Test fun testUnknownType() {
        file("lib.rell", "module; namespace ns { entity data { name; } }")
        file("module.rell", "import lib;")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib")
        val ns = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")

        chkSymsType("lib.ns.data",
            *lib, *ns,
            "data=DEF_ENTITY|-|lib.rell/entity[ns.data]", "?head=ENTITY|lib:ns.data|ns.data",
        )

        val hd = "?head=-"
        chkSymsType("lib.ns.c", *lib, *ns, "c=UNKNOWN|-|-", hd, err = "unknown_name:lib.ns.c")
        chkSymsType("lib.b.c", *lib, "b=UNKNOWN|-|-", hd, "c=UNKNOWN|-|-", hd, err = "unknown_name:lib.b")
        chkSymsType("a.b.c", "a=UNKNOWN|-|-", hd, "b=UNKNOWN|-|-", hd, "c=UNKNOWN|-|-", hd, err = "unknown_name:a")
    }

    @Test fun testUnknownEntity() {
        file("lib.rell", "module; namespace ns { entity data { name = 'Bob'; } }")
        file("module.rell", "import lib;")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib")
        val ns = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")

        chkSymsExpr("create lib.ns.data()",
            *lib, *ns,
            "data=DEF_ENTITY|-|lib.rell/entity[ns.data]", "?head=ENTITY|lib:ns.data|ns.data",
        )

        val hd = "?head=-"
        chkUnknownEntity("lib.ns.c", "lib.ns.c", *lib, *ns, "c=UNKNOWN|-|-", hd)
        chkUnknownEntity("lib.b.c", "lib.b", *lib, "b=UNKNOWN|-|-", hd, "c=UNKNOWN|-|-", hd)
        chkUnknownEntity("a.b.c", "a", "a=UNKNOWN|-|-", hd, "b=UNKNOWN|-|-", hd, "c=UNKNOWN|-|-", hd)
    }

    private fun chkUnknownEntity(type: String, unknown: String, vararg expected: String) {
        chkSymsExpr("create $type()", *expected, err = "unknown_name:$unknown")
    }

    @Test fun testUnknownMirrorStruct() {
        chkSymsExpr("struct<foo>()", "foo=UNKNOWN|-|-", "?head=-", err = "unknown_name:foo")
        chkSymsExpr("struct<foo.bar>()", "foo=UNKNOWN|-|-", "?head=-", "bar=UNKNOWN|-|-", "?head=-", err = "unknown_name:foo")
    }

    @Test fun testUnknownAnonAttr() {
        val f = "f=DEF_FUNCTION|function[f]|-"
        val err = "unknown_name:foo"
        val hd = "?head=-"
        chkSyms("function f(foo) {}", f, "foo=UNKNOWN|function[f].param[foo]|-", hd, err = err)
        chkSyms("function f(foo?) {}", f, "foo=UNKNOWN|function[f].param[foo]|-", hd, err = err)
        chkSyms("function f(foo.bar) {}", f, "foo=UNKNOWN|-|-", hd, "bar=UNKNOWN|function[f].param[bar]|-", hd, err = err)
    }
}
