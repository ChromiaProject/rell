/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def;

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

internal class AttributeAnnotationTest: BaseRellTest() {
    @Test fun testEntityHiddenAttrAnnotation() {
        tstCtx.useSql = true
        chkDefOpExp(
            "entity foo { @dummy_annotation x: integer; }",
            "create foo(100);",
            "foo @ {} (.x)",
            "int[100]",
        )
        chkDefOpExp(
            "entity bar { x: integer = 10; @dummy_annotation y: text = 'Paper in my hand.'; }",
            "create bar(2, 'abc'); create bar(y = 'On top of the hill.');",
            "bar @* {} (.x, .y)",
            "list<(x:integer,y:text)>[(int[2],text[abc]),(int[10],text[On top of the hill.])]",
        )
        chkDefOpExp(
            "entity baz { @dummy_annotation a: boolean; z: big_integer; }",
            "create baz(false, 0L);",
            "baz @ {} (.a, .z)",
            "(a=boolean[false],z=bigint[0])",
        )
        chkDefOpExp(
            "entity quix { @dummy_annotation name; }",
            "create quix(\"Hello world!\");",
            "quix @ {} (.name)",
            "text[Hello world!]",
        )
        chkDefOpExp(
            "entity quam { @dummy_annotation dec: decimal; }",
            "create quam(2.0);",
            "quam @ {} (.dec)",
            "dec[2]",
        )
        chkDefOpExp(
            "entity thud { @dummy_annotation text; }",
            "create thud(\"A knife in the dark\");",
            "thud @ {} (.text)",
            "text[A knife in the dark]",
        )
    }

    @Test fun testEntityHiddenAttrAnnotationWarning() {
        chkCompileDummyWarn("entity foo { @dummy_annotation x: integer; }", "x")
        chkCompileDummyWarn("entity bar { x: integer = -1; @dummy_annotation y: text = 'Monarch'; }", "y")
        chkCompileDummyWarn("entity baz { @dummy_annotation a: boolean; z: big_integer; }", "a")
        chkCompileDummyWarn("entity quix { @dummy_annotation name; }", "name")
        chkCompileDummyWarn("entity quam { @dummy_annotation dec: decimal; }", "dec")
        chkCompileDummyWarn("entity thud { @dummy_annotation text; }", "text")
    }

    @Test fun testEntityHiddenAttrAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("entity foo { @dummy_annotation x: integer; }", dummyErr)
        chkCompile("entity bar { x: integer = -255; @dummy_annotation y: text = 'Revoluția!'; }", dummyErr)
        chkCompile("entity baz { @dummy_annotation a: boolean; z: big_integer; }", dummyErr)
        chkCompile("entity quix { @dummy_annotation name; }", dummyErr)
        chkCompile("entity quam { @dummy_annotation dec: decimal; }", dummyErr)
    }

    @Test fun testEntityAttrAnnotationInvalidType() {
        chkCompile("entity foo { @test x: integer; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("entity bar { x: integer = 6; @extendable y: text = ''; }", "ct_err:modifier:invalid:ann:extendable")
        chkCompile("entity baz { @extend(foo) a: boolean; z: big_integer; }", "ct_err:modifier:invalid:ann:extend")
        chkCompile("@extendable entity quix { @sort name; }",
            "ct_err:[modifier:invalid:ann:extendable][modifier:invalid:ann:sort]")
        chkCompile("@extendable entity quam { @sort_desc dec: decimal; }",
            "ct_err:[modifier:invalid:ann:extendable][modifier:invalid:ann:sort_desc]")
    }

    @Test fun testEntityAttrModifierInvalidType() {
        chkCompile("entity foo { abstract x: integer = 99; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("entity foo { override x: integer; }", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testEntityAttrModifierWithKw() {
        chkCompile("entity foo { operation x: integer = 3; }", "ct_err:syntax")
        chkCompile("entity foo { val x: integer; }", "ct_err:syntax")
    }

    @Test fun testEntityHiddenAttrAnnotationWithValidModifier() {
        tstCtx.useSql = true
        chkDefOpExp(
            "entity foo { mutable @dummy_annotation x: integer; }",
            "create foo(100); update foo @* {} ( .x += 10 );",
            "foo @ {} (.x)",
            "int[110]",
        )
        chkDefOpExp(
            "entity bar { @dummy_annotation mutable x: integer; }",
            "create bar(101); update bar @* {} ( .x += 10 );",
            "bar @ {} (.x)",
            "int[111]",
        )
        chkDefOpExp(
            "entity baz { y: boolean; mutable @dummy_annotation x: integer; }",
            "create baz(false, 102); update baz @* {} ( .x += 10 );",
            "baz @ {} (.y, .x)",
            "(y=boolean[false],x=int[112])",
        )
        chkDefOpExp(
            "entity bop { @dummy_annotation mutable x: integer; y: boolean; }",
            "create bop(103, true); update bop @* {} ( .x += 10 );",
            "bop @ {} (.x, .y)",
            "(x=int[113],y=boolean[true])",
        )
    }

    @Test fun testEntityHiddenAttrAnnotationWithKeyAndIndex() {
        tstCtx.useSql = true
        chkDefOpExp(
            "entity user { @dummy_annotation name; pubkey; index name, pubkey; }",
            "create user('Alex', x'');",
            "user @ {} (.name, .pubkey )",
            "(name=text[Alex],pubkey=byte_array[])",
        )
        chkDefOpExp(
            "entity user { @dummy_annotation name; pubkey; key name, pubkey; }",
            "create user('Anton', x'00');",
            "user @ {} (.name, .pubkey )",
            "(name=text[Anton],pubkey=byte_array[00])",
        )
        chkDefOpExp(
            "entity user { index name; index @dummy_annotation balance: integer; }",
            "create user('Iaroslav', 9001);",
            "user @ {} (.name, .balance)",
            "(name=text[Iaroslav],balance=int[9001])",
        )
        chkDefOpExp(
            "entity user { index name; key @dummy_annotation balance: integer; }",
            "create user('Old Tom Paine', -100);",
            "user @ {} (.name, .balance)",
            "(name=text[Old Tom Paine],balance=int[-100])",
        )
        chkDefOpExp(
            "entity user { index name; key @dummy_annotation pubkey; }",
            "create user('King George III', x'0ddba111');",
            "user @ {} (.name, .pubkey )",
            "(name=text[King George III],pubkey=byte_array[0ddba111])",
        )
        chkDefOpExp(
            "entity user { key name; index @dummy_annotation balance: integer; }",
            "create user('Lafayette', 1000000);",
            "user @ {} (.name, .balance)",
            "(name=text[Lafayette],balance=int[1000000])",
        )
        chkDefOpExp(
            "entity user { key name; index @dummy_annotation pubkey; }",
            "create user('Louis XVI', x'DEADBEEF');",
            "user @ {} (.name, .pubkey )",
            "(name=text[Louis XVI],pubkey=byte_array[deadbeef])",
        )
        chkDefOpExp(
            "entity user { key name; key @dummy_annotation balance: integer; }",
            "create user('George Washington', 3);",
            "user @ {} (.name, .balance)",
            "(name=text[George Washington],balance=int[3])",
        )
    }

    @Test fun testEntityHiddenAttrAnnotationWithKeyAndIndexComplexCases() {
        chkCompile("entity user { key mutable @dummy_annotation name, pubkey; }",
            "ct_err:attr:key_index:too_complex:name:KEY:modifiers")
        chkCompile("entity user { key @dummy_annotation name = 'Ben F.', pubkey; }",
            "ct_err:attr:key_index:too_complex:name:KEY:modifiers")
        chkCompile("entity user { index mutable @dummy_annotation name, pubkey; }",
            "ct_err:attr:key_index:too_complex:name:INDEX:modifiers")
        chkCompile("entity user { index @dummy_annotation name = 'Ben F.', pubkey; }",
            "ct_err:attr:key_index:too_complex:name:INDEX:modifiers")
        chkCompile("entity user { key name = 'Ben F.', pubkey; }",
                "ct_err:attr:key_index:too_complex:name:KEY:expr")
        chkCompile("entity user { index name = 'Ben F.', pubkey; }",
                "ct_err:attr:key_index:too_complex:name:INDEX:expr")
    }

    @Test fun testObjectHiddenAttrAnnotation() {
        tstCtx.useSql = true
        chkDefOpExp("object foo { @dummy_annotation x: integer = 2025; }", "", "foo.x", "int[2025]")
        chkDefOpExp(
            "object bar { x: integer = 256; @dummy_annotation y: text = \"Dance in the oldest boots I own\"; }",
            "",
            "(bar.x, bar.y)",
            "(int[256],text[Dance in the oldest boots I own])",
        )
        chkDefOpExp(
            "object baz { @dummy_annotation a: boolean = false; z: big_integer = -20L; }",
            "",
            "(baz.a, baz.z)",
            "(boolean[false],bigint[-20])",
        )
        chkDefOpExp("object quix { @dummy_annotation name = 'Old Tom Paine'; }", "", "quix.name", "text[Old Tom Paine]")
        chkDefOpExp("object quam { @dummy_annotation arr: byte_array = x'ba11ad'; }", "", "quam.arr", "byte_array[ba11ad]")
        chkDefOpExp("object thud { @dummy_annotation text = 'Liberty Tree'; }", "", "thud.text", "text[Liberty Tree]")
    }

    @Test fun testObjectHiddenAttrAnnotationWarning() {
        chkCompileDummyWarn("object foo { @dummy_annotation x: integer = 2025; }", "x")
        chkCompileDummyWarn("object bar { x: integer = 256; @dummy_annotation y: text = \"Oldest boots I own\"; }", "y")
        chkCompileDummyWarn("object baz { @dummy_annotation a: boolean = false; z: big_integer = -20L; }", "a")
        chkCompileDummyWarn("object quix { @dummy_annotation name = 'Old Tom Paine'; }", "name")
        chkCompileDummyWarn("object quam { @dummy_annotation arr: byte_array = x'ba11ad'; }", "arr")
        chkCompileDummyWarn("object thud { @dummy_annotation text = 'Liberty Tree'; }", "text")
    }

    @Test fun testObjectHiddenAttrAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("object foo { @dummy_annotation x: integer = 2025; }", dummyErr)
        chkCompile("object bar { x: integer = 256; @dummy_annotation y: text = \"Oldest boots I own\"; }", dummyErr)
        chkCompile("object baz { @dummy_annotation a: boolean = false; z: big_integer = -20L; }", dummyErr)
        chkCompile("object quix { @dummy_annotation name = \"Old Tom Paine\"; }", dummyErr)
        chkCompile("object quam { @dummy_annotation arr: byte_array = x'ba11ad'; }", dummyErr)
        chkCompile("object thud { @dummy_annotation text = 'Liberty Tree'; }", dummyErr)
    }

    @Test fun testObjectAttrAnnotationInvalidType() {
        chkCompile("object foo { @test x: integer = 2025; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("object bar { x: integer = 256; @extendable y: text = 'Oldest boots I own'; }",
            "ct_err:modifier:invalid:ann:extendable")
        chkCompile("object baz { @extend(foo) a: boolean = false; z: big_integer = -20L; }",
            "ct_err:modifier:invalid:ann:extend")
        chkCompile("@mount('x') object quix { @sort name = 'Old Tom Paine'; }", "ct_err:modifier:invalid:ann:sort")
        chkCompile("@extendable object quam { @sort_desc dec: decimal = 0.1134; }",
            "ct_err:[modifier:invalid:ann:extendable][modifier:invalid:ann:sort_desc]")
    }

    @Test fun testObjectAttrModifierInvalidType() {
        chkCompile("object foo { abstract x: integer = 0; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("object foo { override x: integer = -9999999999; }", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testObjectAttrModifierWithKw() {
        chkCompile("object foo { operation x: integer; }", "ct_err:syntax")
        chkCompile("object foo { key x: integer; }", "ct_err:syntax")
    }

    @Test fun testStructHiddenAttrAnnotation() {
        chkDefOpExp("struct foo { @dummy_annotation x: integer; }", "", "foo(-1)", "foo[x=int[-1]]")
        chkDefOpExp(
            "struct bar { x: integer = 10; @dummy_annotation y: text = 'river of discontent'; }",
            "",
            "bar(12, 'ab')",
            "bar[x=int[12],y=text[ab]]",
        )
        chkDefOpExp(
            "struct baz { @dummy_annotation a: gtv; z: big_integer; }",
            "",
            "baz('xyz'.to_gtv(), 9999999999999999999999999999L)",
            "baz[a=gtv[\"xyz\"],z=bigint[9999999999999999999999999999]]",
        )
        chkDefOpExp("struct quix { @dummy_annotation name; }", "", "quix('xela')", "quix[name=text[xela]]")
        chkDefOpExp("struct quam { @dummy_annotation dec: decimal; }", "", "quam(2.0)", "quam[dec=dec[2]]")
        chkDefOpExp("struct thud { @dummy_annotation text?; }", "", "thud(null)", "thud[text=null]")
    }

    @Test fun testStructHiddenAttrAnnotationWarning() {
        chkCompileDummyWarn("struct foo { @dummy_annotation x: integer; }", "x")
        chkCompileDummyWarn("struct bar { x: integer = -987654321; @dummy_annotation y: text = 'King George III'; }", "y")
        chkCompileDummyWarn("struct baz { @dummy_annotation a: gtv; z: big_integer; }", "a")
        chkCompileDummyWarn("struct quix { @dummy_annotation name; }", "name")
        chkCompileDummyWarn("struct quam { @dummy_annotation dec: decimal; }", "dec")
        chkCompileDummyWarn("struct thud { @dummy_annotation text?; }", "text")

        val ns = "namespace a { namespace b { struct c {} } }"
        chkCompileDummyWarn("$ns struct fred { @dummy_annotation a.b.c; }", "c")
        chkCompileDummyWarn("$ns struct waldo { @dummy_annotation a.b.c?; }", "c")
    }

    @Test fun testStructHiddenAttrAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("struct foo { @dummy_annotation x: integer; }", dummyErr)
        chkCompile("struct bar { x: integer = 65535; @dummy_annotation y: text = 'I rode out one evening'; }", dummyErr)
        chkCompile("struct baz { @dummy_annotation a: gtv; z: big_integer; }", dummyErr)
        chkCompile("struct quix { @dummy_annotation name; }", dummyErr)
        chkCompile("struct quam { @dummy_annotation dec: decimal; }", dummyErr)
        chkCompile("struct thud { @dummy_annotation text?; }", dummyErr)
    }

    @Test fun testStructAttrAnnotationInvalidType() {
        chkCompile("struct foo { @test x: integer; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("struct bar { x: integer = 7; @extendable y: text = 'These are the rights of man.'; }",
            "ct_err:modifier:invalid:ann:extendable")
        chkCompile("struct baz { @extend(foo) a: gtv; z: big_integer; }", "ct_err:modifier:invalid:ann:extend")
        chkCompile("@mount('x') struct quix { @sort name; }",
            "ct_err:[modifier:invalid:ann:mount][modifier:invalid:ann:sort]")
        chkCompile("struct quam { @sort_desc dec: decimal; }", "ct_err:modifier:invalid:ann:sort_desc")
    }

    @Test fun testStructAttrModifierInvalidType() {
        chkCompile("struct foo { abstract x: integer; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("struct foo { override x: integer = -2; }", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testStructAttrModifierWithKw() {
        chkCompile("struct foo { operation x: integer = 1; }", "ct_err:syntax")
        chkCompile("struct foo { entity x: integer; }", "ct_err:syntax")
    }

    private fun chkCompileDummyWarn(code: String, warningSuffix: String) {
        val warningPrefix = "param:dummy_annotation:annotation_present:ATTRIBUTE"
        chkCompile(code, "OK")
        chkWarn("$warningPrefix:$warningSuffix")
    }

    private fun chkDefOpExp(defCode: String, ops: String, readCode: String, expected: String) {
        def(defCode)
        chkOp(ops)
        chk(readCode, expected)
        resetTst()
    }
}
