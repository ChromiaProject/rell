/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import net.postchain.rell.base.testutils.unwrap
import java.util.Locale
import kotlin.test.Test
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
        chkSyntax("/****/", "*")
        chkSyntax("/*****/", "**")
        chkSyntax("/***123*/", "*123")
        chkSyntax("/*** */", "*")
        chkSyntax("/*** 123 */", "* 123")
    }

    @Test fun testIndent() {
        chkSyntax("/***/", "")
        chkSyntax("/**     */", "")
        chkSyntax("/**hello*/", "hello")
        chkSyntax("/**    hello    */", "hello")

        chkSyntax("/***hello*/", "*hello")
        chkSyntax("/*** hello*/", "* hello")
        chkSyntax("/**    * hello*/", "* hello")
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
        chkSyntax("/***    line1\n* line2\n    *    line3   \n*/", "*    line1\nline2\n   line3")
        chkSyntax("/**\n*    line1\n* line2\n    *    line3   \n*/", "line1\nline2\n   line3")
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
            * @author Bob
            * @author Alice
            * @param x xValue
            * @param y yValue
            * @return ReturnValue
            * @throws error-1 if foo
            * @throws error-2 if bar
            * @since 123
            * @see SomethingElse
        */"""
        chkComment("$c function f(x: integer, y: text) {}", ":f", """
            Comment text.
            |author:Bob;Alice
            |param:x=xValue;y=yValue
            |return:ReturnValue
            |throws:error-1 if foo;error-2 if bar
            |see:SomethingElse
            |since:123
        """.unwrap())
        chkWarn()
    }

    @Test fun testTagDefsAuthor() {
        tst.mainFile = "module.rell"
        val c = "/**\n* foo\n* @author bar\n*/"

        chkComment("$c module;", "", "foo|author:bar")
        chkComment("$c namespace ns {}", ":ns", "foo|author:bar")
        chkComment("$c val C = 123;", ":C", "foo|author:bar")
        chkComment("$c enum color { red }", ":color", "foo|author:bar")
        chkComment("enum color { $c red }", ":color.red", "foo", "comment:tag:not_allowed:ENUM_VALUE:author")
        chkComment("$c entity data {}", ":data", "foo|author:bar")
        chkComment("entity data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:ENTITY_ATTR:author")
        chkComment("$c object data {}", ":data", "foo|author:bar")
        chkComment("object data { $c name = ''; }", ":data.name", "foo", "comment:tag:not_allowed:OBJECT_ATTR:author")
        chkComment("$c struct data {}", ":data", "foo|author:bar")
        chkComment("struct data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:STRUCT_ATTR:author")
        chkComment("$c function f(bar: text) {}", ":f", "foo|author:bar")
        chkComment("$c operation op(bar: text) {}", ":op", "foo|author:bar")
        chkComment("$c query q(bar: text) = 123;", ":q", "foo|author:bar")
        chkComment("function f($c x: text) {}", ":f.x", "foo", "comment:tag:not_allowed:PARAMETER:author")
        chkComment("function f() { $c val x = 123; }", ":f", "n/a", "comment:tag:not_allowed:VAR:author")
    }

    @Test fun testTagDefsParam() {
        tst.mainFile = "module.rell"
        val c = "/**\n* foo\n* @param bar rab\n*/"

        chkComment("$c module;", "", "foo", "comment:tag:not_allowed:MODULE:param")
        chkComment("$c namespace ns {}", ":ns", "foo", "comment:tag:not_allowed:NAMESPACE:param")
        chkComment("$c val C = 123;", ":C", "foo", "comment:tag:not_allowed:CONSTANT:param")
        chkComment("$c enum color { red }", ":color", "foo", "comment:tag:not_allowed:ENUM:param")
        chkComment("enum color { $c red }", ":color.red", "foo", "comment:tag:not_allowed:ENUM_VALUE:param")
        chkComment("$c entity data {}", ":data", "foo", "comment:tag:not_allowed:ENTITY:param")
        chkComment("entity data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:ENTITY_ATTR:param")
        chkComment("$c object data {}", ":data", "foo", "comment:tag:not_allowed:OBJECT:param")
        chkComment("object data { $c name = ''; }", ":data.name", "foo", "comment:tag:not_allowed:OBJECT_ATTR:param")
        chkComment("$c struct data {}", ":data", "foo", "comment:tag:not_allowed:STRUCT:param")
        chkComment("struct data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:STRUCT_ATTR:param")
        chkComment("$c function f(bar: text) {}", ":f", "foo|param:bar=rab")
        chkComment("$c operation op(bar: text) {}", ":op", "foo|param:bar=rab")
        chkComment("$c query q(bar: text) = 123;", ":q", "foo|param:bar=rab")
        chkComment("function f($c x: text) {}", ":f.x", "foo", "comment:tag:not_allowed:PARAMETER:param")
        chkComment("function f() { $c val x = 123; }", ":f", "n/a", "comment:tag:not_allowed:VAR:param")
    }

    @Test fun testTagDefsReturn() {
        tst.mainFile = "module.rell"
        val c = "/**\n* foo\n* @return bar\n*/"

        chkComment("$c module;", "", "foo", "comment:tag:not_allowed:MODULE:return")
        chkComment("$c namespace ns {}", ":ns", "foo", "comment:tag:not_allowed:NAMESPACE:return")
        chkComment("$c val C = 123;", ":C", "foo", "comment:tag:not_allowed:CONSTANT:return")
        chkComment("$c enum color { red }", ":color", "foo", "comment:tag:not_allowed:ENUM:return")
        chkComment("enum color { $c red }", ":color.red", "foo", "comment:tag:not_allowed:ENUM_VALUE:return")
        chkComment("$c entity data {}", ":data", "foo", "comment:tag:not_allowed:ENTITY:return")
        chkComment("entity data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:ENTITY_ATTR:return")
        chkComment("$c object data {}", ":data", "foo", "comment:tag:not_allowed:OBJECT:return")
        chkComment("object data { $c name = ''; }", ":data.name", "foo", "comment:tag:not_allowed:OBJECT_ATTR:return")
        chkComment("$c struct data {}", ":data", "foo", "comment:tag:not_allowed:STRUCT:return")
        chkComment("struct data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:STRUCT_ATTR:return")
        chkComment("$c function f(bar: text) {}", ":f", "foo|return:bar")
        chkComment("$c operation op(bar: text) {}", ":op", "foo", "comment:tag:not_allowed:OPERATION:return")
        chkComment("$c query q(bar: text) = 123;", ":q", "foo|return:bar")
        chkComment("function f($c x: text) {}", ":f.x", "foo", "comment:tag:not_allowed:PARAMETER:return")
        chkComment("function f() { $c val x = 123; }", ":f", "n/a", "comment:tag:not_allowed:VAR:return")
    }

    @Test fun testTagDefsThrows() {
        tst.mainFile = "module.rell"
        val c = "/**\n* foo\n* @throws bar\n*/"

        chkComment("$c module;", "", "foo", "comment:tag:not_allowed:MODULE:throws")
        chkComment("$c namespace ns {}", ":ns", "foo", "comment:tag:not_allowed:NAMESPACE:throws")
        chkComment("$c val C = 123;", ":C", "foo", "comment:tag:not_allowed:CONSTANT:throws")
        chkComment("$c enum color { red }", ":color", "foo", "comment:tag:not_allowed:ENUM:throws")
        chkComment("enum color { $c red }", ":color.red", "foo", "comment:tag:not_allowed:ENUM_VALUE:throws")
        chkComment("$c entity data {}", ":data", "foo", "comment:tag:not_allowed:ENTITY:throws")
        chkComment("entity data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:ENTITY_ATTR:throws")
        chkComment("$c object data {}", ":data", "foo", "comment:tag:not_allowed:OBJECT:throws")
        chkComment("object data { $c name = ''; }", ":data.name", "foo", "comment:tag:not_allowed:OBJECT_ATTR:throws")
        chkComment("$c struct data {}", ":data", "foo", "comment:tag:not_allowed:STRUCT:throws")
        chkComment("struct data { $c name; }", ":data.name", "foo", "comment:tag:not_allowed:STRUCT_ATTR:throws")
        chkComment("$c function f(bar: text) {}", ":f", "foo|throws:bar")
        chkComment("$c operation op(bar: text) {}", ":op", "foo|throws:bar")
        chkComment("$c query q(bar: text) = 123;", ":q", "foo|throws:bar")
        chkComment("function f($c x: text) {}", ":f.x", "foo", "comment:tag:not_allowed:PARAMETER:throws")
        chkComment("function f() { $c val x = 123; }", ":f", "n/a", "comment:tag:not_allowed:VAR:throws")
    }

    @Test fun testTagReturns() {
        chkComment("/** foo\n* @return bar\n*/ function f() = 123;", ":f", "foo|return:bar")
        chkComment("/** foo\n* @returns bar\n*/ function f() = 123;", ":f", "foo|return:bar",
            "comment:tag:deprecated:returns")
    }

    @Test fun testCommentErrors() {
        // Make sure compilation errors work.
        chkComment("/** Foo */ val X: integer = Y;", ":X", "ct_err:unknown_name:Y")
        chkComment("/**\n * 123\n * @since 456\n * @since 789\n*/ struct data {}", ":data", "123|since:456",
            "comment:tag:duplicate:since")
        chkComment("/**\n * 123\n * @foo 456\n*/ struct data {}", ":data", "123|foo:456", "comment:tag:unknown:foo")
        chkComment("/**\n * 123\n * @param 456 789\n*/ function f() {}", ":f", "123",
            "comment:param:invalid_name:[f]:456")
        chkComment("/**\n * 123\n * @param y 456\n*/ function f(x: text) {}", ":f", "123",
            "comment:param:unknown:[f]:y")
        chkComment("/**\n * 123\n * @param x 456\n * @param x 789\n*/ function f(x: text) {}", ":f", "123|param:x=456",
            "comment:tag:duplicate:param[x]")
        chkComment("/**\n * 123\n * @param x 456\n * @param y 789\n*/ function f(z: text) {}", ":f", "123",
            "comment:param:unknown:[f]:x", "comment:param:unknown:[f]:y")
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

    @Test fun testParameterTagError() {
        val c = "/**\n* Header\n*\n* @param\n*/"
        chkComment("$c function f() {}", ":f", "Header", "tag:no_key:param")
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

    @Test fun testWarningPos() {
        tst.errMsgPos = true
        initTst()

        var code = """
            entity data {}

            /**
             * Header
             *
             * @param
             * @param x    X1
             * @param    x X2
             * @param       y    Y
             * @param 123 456
             * @return Ret
             * @return Ter
             * @returns Rets
             * @foo Bar
             */
            function f(x: integer) {}
        """.trimIndent()
        chkComment(code, ":f", "Header|param:x=X1|return:Ret|foo:Bar",
            "main.rell(10:11)|comment:param:invalid_name:[f]:123",
            "main.rell(9:17)|comment:param:unknown:[f]:y",
            "main.rell(13:4)|comment:tag:deprecated:returns",
            "main.rell(8:14)|comment:tag:duplicate:param[x]",
            "main.rell(12:4)|comment:tag:duplicate:return",
            "main.rell(13:4)|comment:tag:duplicate:return",
            "main.rell(14:4)|comment:tag:unknown:foo",
            "main.rell(6:4)|tag:no_key:param",
        )

        code = """
            function f() {}

            /**
             * Header
             *
             * @return Ret
             * @see Other
             */
            entity data {}
        """.trimIndent()
        chkComment(code, ":data", "Header|see:Other", "main.rell(6:4)|comment:tag:not_allowed:ENTITY:return")

        code = """
            function f() {}

                /** @return Ret */
            entity data {}
        """.trimIndent()
        chkComment(code, ":data", "", "main.rell(3:9)|comment:tag:not_allowed:ENTITY:return")
    }

    @Test fun testWarningMessage() {
        initTst()
        val c = "/**\n* @return foo\n*/"

        chkCompile("$c entity data {}", "OK")
        chkMsg("WARNING|comment:tag:not_allowed:ENTITY:return|Comment tag @return not allowed for an entity")

        chkCompile("$c object data {}", "OK")
        chkMsg("WARNING|comment:tag:not_allowed:OBJECT:return|Comment tag @return not allowed for an object")

        chkCompile("$c struct data {}", "OK")
        chkMsg("WARNING|comment:tag:not_allowed:STRUCT:return|Comment tag @return not allowed for a struct")

        chkCompile("$c enum data {}", "OK")
        chkMsg("WARNING|comment:tag:not_allowed:ENUM:return|Comment tag @return not allowed for an enum")

        chkCompile("$c val data = 123;", "OK")
        chkMsg("WARNING|comment:tag:not_allowed:CONSTANT:return|Comment tag @return not allowed for a constant")
    }

    @Test fun testMultiline() {
        val code = """
            /**
             * Desc 1
             *  Desc 2
             * Desc 3
             *
             * @return   Ret 1
             *   Ret 2
             * Ret 3
             *
             * @see
             * See 1
             * See 2
             *    See 3
             *
             */
            function f() = 123;
        """

        chkComment(code, ":f", "Desc 1\n Desc 2\nDesc 3|return:Ret 1\n  Ret 2\nRet 3|see:See 1\nSee 2\n   See 3")
    }

    @Test fun testMultilineNoAsterisk() {
        val code = """
            /**
             * Usage example:
             *
                ```
                    if (f() == 123) {
                        print(456);
                    }
                ```
             *
             * More info...
             *
             * @return Some value...
                   123 if foo
                      1230 if bar
                      1231 if baz
                   456 if oof
             */
            function f() = 123;
        """

        val exp =
            "Usage example:\n\n```\n    if (f() == 123) {\n        print(456);\n    }\n```\n\nMore info..." +
            "|return:Some value...\n123 if foo\n   1230 if bar\n   1231 if baz\n456 if oof"
        chkComment(code, ":f", exp)
    }

    @Test fun testAnnotatedParameterTagFunction() {
        chkAnnotatedParameterTag("function", "{}")
    }

    @Test fun testAnnotatedParameterTagOperation() {
        chkAnnotatedParameterTag("operation", "{}")
    }

    @Test fun testAnnotatedParameterTagQuery() {
        chkAnnotatedParameterTag("query", "= 0;")
    }

    private fun chkAnnotatedParameterTag(kw: String, body: String) {
        val c = """/**
            * SomeFunction
            * @param x FirstParam
            * @param y SecondParam
        */"""
        val code = "$c $kw f(x: integer, @dummy_annotation /** ParamNo2 */ y: text, /** ParamNo3 */ @dummy_annotation z: boolean) $body"
        val warning1 = "param:dummy_annotation:annotation_present:${kw.uppercase(Locale.getDefault())}:[:f]:y"
        val warning2 = "param:dummy_annotation:annotation_present:${kw.uppercase(Locale.getDefault())}:[:f]:z"
        chkComment(code, ":f", "SomeFunction|param:x=FirstParam;y=SecondParam;z=ParamNo3", warning1, warning2)
        chkComment(code, ":f.x", "FirstParam", warning1, warning2)
        chkComment(code, ":f.y", "SecondParam", warning1, warning2)
        chkComment(code, ":f.z", "ParamNo3", warning1, warning2)
    }

    @Test fun testFirstParamCommentWinsFunction() {
        chkFirstParamCommentWins("function", "{}")
    }

    @Test fun testFirstParamCommentWinsOperation() {
        chkFirstParamCommentWins("operation", "{}")
    }

    @Test fun testFirstParamCommentWinsQuery() {
        chkFirstParamCommentWins("query", "= 0;")
    }

    private fun chkFirstParamCommentWins(kw: String, body: String) {
        val code = "$kw f(/** first */ @dummy_annotation /** second */ x: integer) $body"
        val warning = "param:dummy_annotation:annotation_present:${kw.uppercase(Locale.getDefault())}:[:f]:x"
        chkComment(code, ":f.x", "first", warning)
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

    private fun chkComment(code: String, name: String, exp: String, vararg warns: String) {
        val act = processDocDef(code, name) { def ->
            def.docSymbol.comment?.strCode() ?: "n/a"
        }
        assertEquals(exp, act)
        chkWarn(*warns)
    }
}
