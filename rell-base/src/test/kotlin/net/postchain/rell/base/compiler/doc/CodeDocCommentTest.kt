/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import org.junit.Test
import kotlin.test.assertEquals

class CodeDocCommentTest: BaseCodeDocTest() {
    @Test fun testBasic() {
        chkComment("/** Hello! */ entity data {}", ":data", "Hello!")
    }

    @Test fun testSyntax() {
        chkSyntax("/** MyComment */", "MyComment")
        chkSyntax("/**MyComment*/", "MyComment")
        chkSyntax("/* MyComment */", "n/a")
        chkSyntax("/**/", "n/a")
        chkSyntax("/***/", "")
        chkSyntax("/** */", "")
        chkSyntax("/****/", "")
        chkSyntax("/***123*/", "*123")
        chkSyntax("/*** */", "")
        chkSyntax("/*** 123 */", "123")
    }

    @Test fun testIndent() {
        chkSyntax("/***/", "")
        chkSyntax("/**     */", "")
        chkSyntax("/**hello*/", "hello")
        chkSyntax("/**    hello    */", "hello")

        chkSyntax("/*** hello*/", "hello")
        chkSyntax("/**    * hello*/", "hello")
        chkSyntax("/***hello*/", "*hello")
        chkSyntax("/**    *hello*/", "*hello")

        chkSyntax("/**\n* hello\n*/", "hello")
        chkSyntax("/**\n    * hello\n*/", "hello")
        chkSyntax("/**\n*hello\n*/", "*hello")
        chkSyntax("/**\n    *hello\n*/", "*hello")

        chkSyntax("/**\n    * hello\n    * world*/", "hello\nworld")
        chkSyntax("/**\n    * hello\n      * world*/", "hello\nworld")
        chkSyntax("/**\n    * hello\n    *   world*/", "hello\n  world")
        chkSyntax("/**\n    * line1\n    * line2\n    * line3*/", "line1\nline2\nline3")
        chkSyntax("/**\n    * line1\n    * line2\n    * line3\n*/", "line1\nline2\nline3")
        chkSyntax("/**\n    * line1\n      * line2\n        * line3*/", "line1\nline2\nline3")
        chkSyntax("/**\n    * line1\n    *   line2\n    *     line3*/", "line1\n  line2\n    line3")

        chkSyntax("/**    line1\n* line2\n    *    line3   \n*/", "line1\nline2\n   line3")
        chkSyntax("/***    line1\n* line2\n    *    line3   \n*/", "line1\nline2\n   line3")
        chkSyntax("/**\n* line1\n   *line2\n   line3   \n   *    line4\n*/", "line1\n*line2\nline3\n   line4")
        chkSyntax("/**\n* line1\n*\n*   line2\n   *   \n* line3\n\n*   line4\n*/",
            "line1\n\n  line2\n\nline3\n\n  line4")

        chkSyntax("/**\n  ** foo\n*/", "** foo")
        chkSyntax("/**\n  *    * foo\n*/", "* foo")

        chkSyntax("/**   line1\n   line2\n   line3\n*/", "line1\nline2\nline3")

        chkSyntax("/**\n* line1     \n*    line2     \n*       line3    \n\n\n*/", "line1\n   line2\n      line3")
        chkSyntax("/**\n\n\n* line\n\n\n*/", "line")
        chkSyntax("/**\n*\n*\n* line\n*\n*\n*/", "line")
    }

    private fun chkSyntax(comment: String, exp: String) {
        chkComment("$comment struct data {}", ":data", exp)
    }

    @Test fun testTags() {
        val c = """/**
            * Comment text.
            * @param x xValue
            * @param y yValue
            * @returns ReturnValue
            * @since 123
            * @see SomethingElse
        */"""
        chkComment("$c function f(x: integer, y: text) {}", ":f",
            "Comment text.|param:x=xValue;y=yValue|returns:ReturnValue|since:123|see:SomethingElse")
    }

    @Test fun testCommentErrors() {
        // Make sure compilation errors work.
        chkComment("/** Foo */ val X: integer = Y;", ":X", "ct_err:unknown_name:Y")

        chkComment("/**\n * 123\n * @since 456\n * @since 789\n*/ struct data {}", ":data", "123|since:456")
        chkWarn("comment:tag:duplicate:since")

        chkComment("/**\n * 123\n * @foo 456\n*/ struct data {}", ":data", "123|foo:456")
        chkWarn("comment:tag:unknown:foo")

        chkComment("/**\n * 123\n * @param 456 789\n*/ function f() {}", ":f", "123")
        chkWarn("comment:param:invalid_name:[f]:456")

        chkComment("/**\n * 123\n * @param y 456\n*/ function f(x: text) {}", ":f", "123")
        chkWarn("comment:param:unknown:[f]:y")

        chkComment("/**\n * 123\n * @param x 456\n * @param x 789\n*/ function f(x: text) {}", ":f", "123|param:x=456")
        chkWarn("comment:tag:duplicate:param[x]")

        chkComment("/**\n * 123\n * @param x 456\n * @param y 789\n*/ function f(z: text) {}", ":f", "123")
        chkWarn("comment:param:unknown:[f]:x", "comment:param:unknown:[f]:y")
    }

    @Test fun testCommentBinding() {
        val c = "/** Hello! */"

        chkComment("entity data {}", ":data", "n/a")
        chkComment("$c entity data {}", ":data", "Hello!")
        chkComment("$c /* 123 */ entity data {}", ":data", "n/a")
        chkComment("$c /* */ entity data {}", ":data", "n/a")
        chkComment("$c /**/ entity data {}", ":data", "n/a")

        chkComment("$c\n//\nentity data {}", ":data", "n/a")
        chkComment("$c\n// 123\nentity data {}", ":data", "n/a")
        chkComment("$c\n/* */\nentity data {}", ":data", "n/a")
        chkComment("$c\n/* 123 */\nentity data {}", ":data", "n/a")
        chkComment("$c\n\n\nentity data {}", ":data", "Hello!")

        chkComment("entity $c data {}", ":data", "n/a")
        chkComment("entity data $c {}", ":data", "n/a")
        chkComment("entity data { $c }", ":data", "n/a")

        chkComment("$c @log entity data {}", ":data", "Hello!")
        chkComment("@log $c entity data {}", ":data", "n/a")
        chkComment("@log entity $c data {}", ":data", "n/a")
        chkComment("@log entity data $c {}", ":data", "n/a")
        chkComment("$c @ log entity data {}", ":data", "Hello!")
        chkComment("@ $c log entity data {}", ":data", "n/a")
        chkComment("@${c}log entity data {}", ":data", "n/a")

        chkComment("$c @mount('foo') @log entity data {}", ":data", "Hello!")
        chkComment("@mount('foo') $c @log entity data {}", ":data", "n/a")
        chkComment("@mount('foo') @log $c entity data {}", ":data", "n/a")
        chkComment("@ $c mount('foo') @log entity data {}", ":data", "n/a")
        chkComment("@mount $c ('foo') @log entity data {}", ":data", "n/a")
        chkComment("@mount($c 'foo') @log entity data {}", ":data", "n/a")
        chkComment("@mount('foo' $c) @log entity data {}", ":data", "n/a")
        chkComment("@${c}mount('foo') @log entity data {}", ":data", "n/a")

        chkComment("entity other {} $c entity data {}", ":data", "Hello!")
        chkComment("$c entity other {} entity data {}", ":data", "n/a")
        chkComment("$c entity other {} entity data {}", ":other", "Hello!")
        chkComment("entity other { $c } entity data {}", ":data", "n/a")
        chkComment("entity other { $c } entity data {}", ":other", "n/a")
        chkComment("entity $c other {} entity data {}", ":data", "n/a")
        chkComment("entity $c other {} entity data {}", ":other", "n/a")

        chkComment("/** Bye! */ $c entity data {}", ":data", "Hello!")
        chkComment("$c /** Bye! */ entity data {}", ":data", "Bye!")
    }

    @Test fun testDefs() {
        chkCommentEx("^ entity data {}", ":data")
        chkCommentEx("^ object state {}", ":state")
        chkCommentEx("^ struct data {}", ":data")
        chkCommentEx("^ enum color { red }", ":color")
        chkCommentEx("^ operation op() {}", ":op")
        chkCommentEx("^ query q() = 123;", ":q")
        chkCommentEx("^ function f() {}", ":f")
        chkCommentEx("^ val C = 123;", ":C")
        chkCommentEx("^ namespace ns {}", ":ns")
    }

    @Test fun testNamespace() {
        val c = "/** Hello! */"
        chkComment("$c namespace ns {}", ":ns", "Hello!")

        chkComment("$c namespace a.b.c {}", ":a.b.c", "Hello!")
        chkComment("$c namespace a.b.c {}", ":a.b", "n/a")
        chkComment("$c namespace a.b.c {}", ":a", "n/a")

        chkComment("$c namespace { namespace ns {} }", ":ns", "n/a")
        chkComment("namespace { $c namespace ns {} }", ":ns", "Hello!")
        chkComment("$c namespace ns { namespace {} }", ":ns", "Hello!")
        chkComment("namespace ns { $c namespace {} }", ":ns", "n/a")
    }

    @Test fun testNamespaceDisjoint() {
        val (c1, c2) = listOf("/** C1 */", "/** C2 */")

        chkComment("namespace ns {} namespace ns {} namespace ns {}", ":ns", "n/a")
        chkComment("$c1 namespace ns {} namespace ns {} namespace ns {}", ":ns", "C1")
        chkComment("namespace ns {} $c1 namespace ns {} namespace ns {}", ":ns", "C1")
        chkComment("namespace ns {} namespace ns {} $c1 namespace ns {}", ":ns", "C1")

        chkComment("$c1 namespace ns {} $c2 namespace ns {} namespace ns {}", ":ns", "C1")
        chkComment("$c1 namespace ns {} namespace ns {} $c2 namespace ns {}", ":ns", "C1")
        chkComment("namespace ns {} $c1 namespace ns {} $c2 namespace ns {}", ":ns", "C1")
        chkComment("$c2 namespace ns {} $c1 namespace ns {} namespace ns {}", ":ns", "C2")
        chkComment("$c2 namespace ns {} namespace ns {} $c1 namespace ns {}", ":ns", "C2")
        chkComment("namespace ns {} $c2 namespace ns {} $c1 namespace ns {}", ":ns", "C2")

        val code = "/** ABC */ namespace a.b.c {} /** AB */ namespace a.b {} /** A */ namespace a {}"
        chkComment(code, ":a.b.c", "ABC")
        chkComment(code, ":a.b", "AB")
        chkComment(code, ":a", "A")
    }

    @Test fun testFunctionModifiers() {
        file("lib.rell", "abstract module; abstract function a() {}")
        def("import lib;")
        def("@extendable function p() {}")

        val c = "/** MyComment */"
        chkComment("$c function f() {}", ":f", "MyComment")

        chkComment("$c @extendable function f() {}", ":f", "MyComment")
        chkComment("@extendable $c function f() {}", ":f", "n/a")
        chkComment("@extendable function $c f() {}", ":f", "n/a")
        chkComment("@${c}extendable function f() {}", ":f", "n/a")

        chkComment("$c @extend(p) function f() {}", ":f", "MyComment")
        chkComment("@extend(p) $c function f() {}", ":f", "n/a")
        chkComment("@extend(p) function $c f() {}", ":f", "n/a")
        chkComment("@${c}extend(p) function f() {}", ":f", "n/a")
        chkComment("@extend($c p) function f() {}", ":f", "n/a")
    }

    @Test fun testFunctionAbstract() {
        file("module.rell", "abstract module;")

        val c = "/** MyComment */"
        chkComment("abstract function f() {}", ":f", "n/a")
        chkComment("$c abstract function f() {}", ":f", "MyComment")
        chkComment("abstract $c function f() {}", ":f", "n/a")
        chkComment("abstract function $c f() {}", ":f", "n/a")
    }

    @Test fun testEnum() {
        chkCommentEx("^ enum color { red, green, blue }", ":color", ":color.red", ":color.green", ":color.blue")
        chkCommentEx("enum color { ^ red, green, blue }", ":color.red", ":color", ":color.green", ":color.blue")
        chkCommentEx("enum color { red, ^ green, blue }", ":color.green", ":color", ":color.red", ":color.blue")
        chkCommentEx("enum color { red, green, ^ blue }", ":color.blue", ":color", ":color.red", ":color.green")
    }

    @Test fun testAttribute() {
        chkAttribute("entity")
        chkAttribute("object")
        chkAttribute("struct")
    }

    private fun chkAttribute(kw: String) {
        val c = "/** MyComment */"
        chkCommentEx("$c $kw data { ^ x: integer = 1; y: text = 'A'; }", ":data.x", ":data.y")
        chkCommentEx("$c $kw data { x: integer = 1; ^ y: text = 'A'; }", ":data.y", ":data.x")
        chkCommentEx("$c $kw data { ^ mutable x: integer = 1; y: text = 'A'; }", ":data.x", ":data.y")
        chkCommentEx("$c $kw data { x: integer = 1; ^ mutable y: text = 'A'; }", ":data.y", ":data.x")

        chkComment("$kw data { $c x: integer = 1; }", ":data.x", "MyComment")
        chkComment("$kw data { $c mutable x: integer = 1; }", ":data.x", "MyComment")
        chkComment("$kw data { mutable $c x: integer = 1; }", ":data.x", "n/a")
        chkComment("$kw data { x $c: integer = 1; }", ":data.x", "n/a")
    }

    @Test fun testEntityAttribute() {
        val c = "/** MyComment */"
        chkEntityAttr("entity data { x: integer; key x; }", "x" to "n/a")
        chkEntityAttr("entity data { $c x: integer; key x; }", "x" to "MyComment")
        chkEntityAttr("entity data { x: integer; $c key x; }", "x" to "MyComment")
        chkEntityAttr("entity data { $c key x; x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { key x; $c x: integer; }", "x" to "MyComment")

        chkEntityAttr("entity data { key x: integer; }", "x" to "n/a")
        chkEntityAttr("entity data { $c key x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { key $c x: integer; }", "x" to "n/a")

        chkEntityAttr("entity data { key mutable x: integer; }", "x" to "n/a")
        chkEntityAttr("entity data { $c key mutable x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { key $c mutable x: integer; }", "x" to "n/a")
        chkEntityAttr("entity data { key mutable $c x: integer; }", "x" to "n/a")
    }

    @Test fun testEntityAttribute2() {
        def("namespace ns { enum color {} }")

        val c = "/** MyComment */"
        chkEntityAttr("entity data { $c key name; }", "name" to "MyComment")
        chkEntityAttr("entity data { $c name; key name; }", "name" to "MyComment")
        chkEntityAttr("entity data { name; $c key name; }", "name" to "MyComment")
        chkEntityAttr("entity data { $c key name; name; }", "name" to "MyComment")
        chkEntityAttr("entity data { key name; $c name; }", "name" to "MyComment")

        chkEntityAttr("entity data { $c key ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { ns.color; $c key ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { $c ns.color; key ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { $c key ns.color; ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { key ns.color; $c ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { ns.color; $c key color; }", "color" to "MyComment")
        chkEntityAttr("entity data { $c ns.color; key color; }", "color" to "MyComment")
        chkEntityAttr("entity data { $c key color; ns.color; }", "color" to "MyComment")
        chkEntityAttr("entity data { key color; $c ns.color; }", "color" to "MyComment")

        chkEntityAttr("entity data { $c key x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { x: integer; $c key x; }", "x" to "MyComment")
        chkEntityAttr("entity data { $c x: integer; key x; }", "x" to "MyComment")
        chkEntityAttr("entity data { x: integer; $c key x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { $c x: integer; key x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { $c key x; x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { key x; $c x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { $c key x: integer; x: integer; }", "x" to "MyComment")
        chkEntityAttr("entity data { key x: integer; $c x: integer; }", "x" to "MyComment")
    }

    @Test fun testEntityAttribute3() {
        val (c1, c2, c3) = listOf("/** AAA */", "/** BBB */", "/** CCC */")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; }", "x" to "CCC", "y" to "CCC")
        chkEntityAttr("entity data { key $c1 x: integer, $c2 y: text; }", "x" to "n/a", "y" to "n/a")
        chkEntityAttr("entity data { $c3 key $c1 x: integer, $c2 y: text; }", "x" to "CCC", "y" to "CCC")

        chkEntityAttr("entity data { $c1 x: integer; $c2 y: text; $c3 key x, y; }", "x" to "AAA", "y" to "BBB")
        chkEntityAttr("entity data { $c3 key x, y; $c1 x: integer; $c2 y: text; }", "x" to "AAA", "y" to "BBB")
        chkEntityAttr("entity data { $c1 x: integer; $c2 y: text; $c3 key x: integer, y: text; }",
            "x" to "AAA", "y" to "BBB")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; $c1 x: integer; $c2 y: text; }",
            "x" to "AAA", "y" to "BBB")

        chkEntityAttr("entity data { $c1 x: integer; y: text; $c3 key x, y; }", "x" to "AAA", "y" to "CCC")
        chkEntityAttr("entity data { $c3 key x, y; $c1 x: integer; y: text; }", "x" to "AAA", "y" to "CCC")
        chkEntityAttr("entity data { x: integer; $c2 y: text; $c3 key x, y; }", "x" to "CCC", "y" to "BBB")
        chkEntityAttr("entity data { $c3 key x, y; x: integer; $c2 y: text; }", "x" to "CCC", "y" to "BBB")

        chkEntityAttr("entity data { $c1 x: integer; $c3 key x: integer, y: text; }", "x" to "AAA", "y" to "CCC")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; $c1 x: integer; }", "x" to "AAA", "y" to "CCC")
        chkEntityAttr("entity data { $c2 y: text; $c3 key x: integer, y: text; }", "x" to "CCC", "y" to "BBB")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; $c2 y: text; }", "x" to "CCC", "y" to "BBB")
        chkEntityAttr("entity data { x: integer; $c3 key x: integer, y: text; }", "x" to "CCC", "y" to "CCC")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; x: integer; }", "x" to "CCC", "y" to "CCC")
        chkEntityAttr("entity data { y: text; $c3 key x: integer, y: text; }", "x" to "CCC", "y" to "CCC")
        chkEntityAttr("entity data { $c3 key x: integer, y: text; y: text; }", "x" to "CCC", "y" to "CCC")
    }

    @Test fun testEntityAttribute4() {
        val (c1, c2, c3, c4) = listOf("/** AAA */", "/** BBB */", "/** CCC */", "/** DDD */")
        chkEntityAttr("entity data { $c1 x: integer; $c2 y: text; $c3 key x; $c4 key y, x; }", "x" to "AAA", "y" to "BBB")
        chkEntityAttr("entity data { $c3 key x; $c4 key y, x; $c1 x: integer; $c2 y: text; }", "x" to "AAA", "y" to "BBB")
        chkEntityAttr("entity data { x: integer; $c2 y: text; $c3 key x; $c4 key y, x; }", "x" to "CCC", "y" to "BBB")
        chkEntityAttr("entity data { x: integer; $c2 y: text; $c4 key y, x; $c3 key x; }", "x" to "DDD", "y" to "BBB")
        chkEntityAttr("entity data { $c1 x: integer; y: text; $c3 key x; $c4 key y, x; }", "x" to "AAA", "y" to "DDD")
        chkEntityAttr("entity data { x: integer; y: text; $c3 key x; $c4 key y, x; }", "x" to "CCC", "y" to "DDD")
        chkEntityAttr("entity data { x: integer; y: text; $c4 key y, x; $c3 key x; }", "x" to "DDD", "y" to "DDD")
    }

    private fun chkEntityAttr(code: String, vararg exps: Pair<String, String>) {
        for ((name, exp) in exps) {
            chkComment(code, ":data.$name", exp)
            chkComment(code.replace("key", "index"), ":data.$name", exp)
        }
    }

    @Test fun testEntityHeader() {
        chkCommentEx("@external('foo') namespace ns { ^ entity block; }", ":ns.block")
        chkCommentEx("@external('foo') namespace ns { ^ entity transaction; }", ":ns.transaction")
    }

    @Test fun testParameter() {
        var code = "/** FFF */ function f(/** XXX */ x: integer, /** YYY */ y: text) {}"
        chkComment(code, ":f.x", "XXX")
        chkComment(code, ":f.y", "YYY")

        code = "/** OOO */ operation op(/** XXX */ x: integer, /** YYY */ y: text) {}"
        chkComment(code, ":op.x", "XXX")
        chkComment(code, ":op.y", "YYY")

        code = "/** QQQ */ query q(/** XXX */ x: integer, /** YYY */ y: text) = 123;"
        chkComment(code, ":q.x", "XXX")
        chkComment(code, ":q.y", "YYY")
    }

    @Test fun testParameterTagFunction() {
        chkParameterTag("function", "{}")
    }

    @Test fun testParameterTagOperation() {
        chkParameterTag("operation", "{}")
    }

    @Test fun testParameterTagQuery() {
        chkParameterTag("query", "= 0;")
    }

    private fun chkParameterTag(kw: String, body: String) {
        val c = """/**
            * SomeFunction
            * @param x FirstParam
            * @param y SecondParam
        */"""
        val code = "$c $kw f(x: integer, /** ParamNo2 */ y: text, /** ParamNo3 */ z: boolean) $body"
        chkComment(code, ":f", "SomeFunction|param:x=FirstParam;y=SecondParam;z=ParamNo3")
        chkComment(code, ":f.x", "FirstParam")
        chkComment(code, ":f.y", "SecondParam")
        chkComment(code, ":f.z", "ParamNo3")
    }

    @Test fun testAttributeParameterImplicitName() {
        def("namespace ns { struct foo {} }")
        chkCommentEx("struct data { ^ name; }", ":data.name")
        chkCommentEx("struct data { ^ mutable name; }", ":data.name")
        chkCommentEx("struct data { ^ ns.foo; }", ":data.foo")
        chkCommentEx("struct data { ^ mutable ns.foo; }", ":data.foo")
        chkCommentEx("function f(^ name) {}", ":f.name")
        chkCommentEx("function f(^ ns.foo) {}", ":f.foo")
    }

    @Test fun testImportSimple() {
        file("lib.rell", "module; val X = 123; function f() = 'Hello';")

        val c = "/** MyComment */"
        chkComment("$c import lib;", ":lib", "n/a")
        chkComment("$c import bil: lib;", ":bil", "MyComment")
        chkComment("import $c bil: lib;", ":bil", "n/a")
        chkComment("import bil: $c lib;", ":bil", "n/a")
    }

    @Test fun testImportWildcard() {
        file("lib.rell", "module; val X = 123; function f() = 'Hello';")

        val c = "/** MyComment */"
        chkComment("$c import lib.*;", ":X", "n/a")
        chkComment("$c import lib.*;", ":f", "n/a")
        chkComment("$c import ns: lib.*;", ":ns", "MyComment")
        chkComment("import $c ns: lib.*;", ":ns", "n/a")
        chkComment("import ns: $c lib.*;", ":ns", "n/a")
        chkComment("$c import ns: lib.*;", ":ns.X", "n/a")
    }

    @Test fun testImportExact() {
        file("lib.rell", "module; val X = 123; function f() = 'Hello'; namespace sn { val K = 456; }")

        val c = "/** MyComment */"
        chkComment("import lib.{X};", ":X", "n/a")
        chkComment("$c import lib.{X};", ":X", "n/a")
        chkComment("import lib.{$c X};", ":X", "n/a")
        chkComment("import lib.{$c Y:X};", ":Y", "MyComment")
        chkComment("$c import lib.{Y:X};", ":Y", "n/a")
        chkComment("import $c lib.{Y:X};", ":Y", "n/a")
        chkComment("import lib.{Y: $c X};", ":Y", "n/a")

        chkComment("import ns: lib.{X};", ":ns", "n/a")
        chkComment("$c import ns: lib.{X};", ":ns", "MyComment")
        chkComment("$c import ns: lib.{X};", ":ns.X", "n/a")
        chkComment("import $c ns: lib.{X};", ":ns", "n/a")

        chkComment("import ns: lib.{Y:X};", ":ns", "n/a")
        chkComment("$c import ns: lib.{Y:X};", ":ns", "MyComment")
        chkComment("$c import ns: lib.{Y:X};", ":ns.Y", "n/a")
        chkComment("import ns: lib.{$c Y:X};", ":ns", "n/a")
        chkComment("import ns: lib.{$c Y:X};", ":ns.Y", "MyComment")
        chkComment("$c import ns: lib.{/** Foo */ Y:X};", ":ns", "MyComment")
        chkComment("$c import ns: lib.{/** Foo */ Y:X};", ":ns.Y", "Foo")

        chkComment("import lib.{sn.*};", ":K", "n/a")
        chkComment("$c import lib.{sn.*};", ":K", "n/a")
        chkComment("import lib.{$c sn.*};", ":K", "n/a")
        chkComment("import lib.{ns: sn.*};", ":ns", "n/a")
        chkComment("import lib.{$c ns: sn.*};", ":ns", "MyComment")
        chkComment("/** Foo */ import ns1: lib.{$c ns2: sn.*};", ":ns1", "Foo")
        chkComment("/** Foo */ import ns1: lib.{$c ns2: sn.*};", ":ns1.ns2", "MyComment")
        chkComment("/** Foo */ import ns1: lib.{ns2: sn.*};", ":ns1.ns2", "n/a")
    }

    @Test fun testModule() {
        tst.mainFile = "lib.rell"
        mainModule("lib")
        file("module.rell", "")

        val c = "/** MyMod */"
        chkComment("$c module; /** MyData */ struct data {}", "lib", "MyMod")
        chkComment("$c abstract module; /** MyData */ struct data {}", "lib", "MyMod")
        chkComment("abstract $c module; /** MyData */ struct data {}", "lib", "n/a")
        chkComment("$c @mount('foo') module; /** MyData */ struct data {}", "lib", "MyMod")
        chkComment("@mount('foo') $c module; /** MyData */ struct data {}", "lib", "n/a")
        chkComment("@external $c module; /** MyData */ @log entity data {}", "lib", "n/a")
    }

    private fun chkCommentEx(code: String, name: String, vararg otherNames: String) {
        val moreCode = "/** pre-comment */ val pre = 123; $code /** post-comment */ val post = 456;"
        chkComment(moreCode.replace("^", ""), name, "n/a")

        val code2 = moreCode.replace("^", "/** Hello! */")
        chkComment(code2, name, "Hello!")

        for (otherName in otherNames) {
            chkComment(code2, otherName, "n/a")
        }
    }

    private fun chkComment(code: String, name: String, exp: String) {
        val act = processDocDef(code, name) { def ->
            def.docSymbol.comment?.strCode() ?: "n/a"
        }
        assertEquals(exp, act)
    }
}
