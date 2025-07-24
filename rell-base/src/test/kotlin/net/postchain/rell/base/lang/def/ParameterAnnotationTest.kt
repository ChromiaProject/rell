package net.postchain.rell.base.lang.def;

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

internal class ParameterAnnotationTest: BaseRellTest() {
    @Test fun testFunctionHiddenParamAnnotation() {
        def("function foo(@dummy_annotation x: integer): integer { return x + 17; }")
        def("function bar(x: integer, @dummy_annotation y: text) = y.repeat(x);")
        def("function baz(@dummy_annotation a: gtv, z: big_integer) { return 10L; }")
        def("function quix(@dummy_annotation name) = \"We're here.\";")
        def("function quam(@dummy_annotation dec: decimal) = dec * 3.1415;")
        def("function thud(@dummy_annotation text?) = \"Riders in black\";")
        chk("foo(0)", "int[17]")
        chk("bar(2, \"abc\")", "text[abcabc]");
        chk("baz((45).to_gtv(), 0L)", "bigint[10]")
        chk("quix(\"Hello world!\")", "text[We're here.]")
        chk("quam(2.0)", "dec[6.283]")
        chk("thud(\"A knife in the dark\")", "text[Riders in black]")
    }

    @Test fun testFunctionHiddenParamAnnotationWarning() {
        chkCompileDummyWarn("function foo(@dummy_annotation x: integer): integer { return x + 17; }", "FUNCTION:[:foo]:x")
        chkCompileDummyWarn("function bar(x: integer, @dummy_annotation y: text) = y.repeat(x);", "FUNCTION:[:bar]:y")
        chkCompileDummyWarn("function baz(@dummy_annotation a: gtv, z: big_integer) { return 10L; }", "FUNCTION:[:baz]:a")
        chkCompileDummyWarn("function quix(@dummy_annotation name) = \"We're here.\";", "FUNCTION:[:quix]:name")
        chkCompileDummyWarn("function quam(@dummy_annotation dec: decimal) = dec * 3.1415;", "FUNCTION:[:quam]:dec")
        chkCompileDummyWarn("function thud(@dummy_annotation text?) = \"Riders in black\";", "FUNCTION:[:thud]:text")

        val ns = "namespace a { namespace b { struct c {} } }"
        chkCompileDummyWarn("$ns function fred(@dummy_annotation a.b.c): integer { return 0; }", "FUNCTION:[:fred]:c")
        chkCompileDummyWarn("$ns function waldo(@dummy_annotation a.b.c?): boolean { return false; }", "FUNCTION:[:waldo]:c")
    }

    @Test fun testFunctionHiddenParamAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("function foo(@dummy_annotation x: integer): integer { return x + 17; }", dummyErr)
        chkCompile("function bar(x: integer, @dummy_annotation y: text) = y.repeat(x);", dummyErr)
        chkCompile("function baz(@dummy_annotation a: gtv, z: big_integer) { return 10L; }", dummyErr)
        chkCompile("function quix(@dummy_annotation name) = \"We're here.\";", dummyErr)
        chkCompile("function quam(@dummy_annotation dec: decimal) = dec * 3.1415;", dummyErr)
    }

    @Test fun testFunctionParamAnnotationInvalidType() {
        chkCompile("function foo(@test x: integer): integer { return x - 1231; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("function bar(x: integer, @extendable y: text) {}", "ct_err:modifier:invalid:ann:extendable")
        chkCompile("function baz(@extend(foo) a: gtv, z: big_integer) {}", "ct_err:modifier:invalid:ann:extend")
        chkCompile("@extendable function quix(@sort name) {}", "ct_err:modifier:invalid:ann:sort")
        chkCompile("@extendable function quam(@sort_desc dec: decimal) = false;",
            "ct_err:modifier:invalid:ann:sort_desc")
    }

    @Test fun testFunctionParamModifierInvalidType() {
        chkCompile("function foo(abstract x: integer) {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("function foo(override x: integer) {}", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testFunctionParamModifierWithKw() {
        chkCompile("function foo(operation x: integer) {}", "ct_err:syntax")
        chkCompile("function foo(key x: integer) {}", "ct_err:syntax")
    }

    @Test fun testOperationHiddenParamAnnotation() {
        tst.testLib = true
        def("operation foo(@dummy_annotation x: integer) {}")
        def("operation bar(x: integer, @dummy_annotation y: text) {}")
        def("operation baz(@dummy_annotation a: gtv, z: big_integer) {}")
        def("operation quix(@dummy_annotation name) {}")
        def("operation quam(@dummy_annotation arr: byte_array) {}")
        def("operation thud(@dummy_annotation text?) {}")
        chk("foo(1)", "op[foo(1)]")
        chk("bar(-17, \"old leather boots\")", "op[bar(-17,\"old leather boots\")]")
        chk("baz((0).to_gtv(), -238974827349287493827L)", "op[baz(0,-238974827349287493827L)]")
        chk("quix(\"foo\")", "op[quix(\"foo\")]")
        chk("quam(x\"DEADBEEF\")", "op[quam(x\"DEADBEEF\")]")
        chk("thud(null)", "op[thud(null)]")
    }

    @Test fun testOperationHiddenParamAnnotationWarning() {
        chkCompileDummyWarn("operation foo(@dummy_annotation x: integer) {}", "OPERATION:[:foo]:x")
        chkCompileDummyWarn("operation bar(x: integer, @dummy_annotation y: text) {}", "OPERATION:[:bar]:y")
        chkCompileDummyWarn("operation baz(@dummy_annotation a: gtv, z: big_integer) {}", "OPERATION:[:baz]:a")
        chkCompileDummyWarn("operation quix(@dummy_annotation name) {}", "OPERATION:[:quix]:name")
        chkCompileDummyWarn("operation quam(@dummy_annotation arr: byte_array) {}", "OPERATION:[:quam]:arr")
        chkCompileDummyWarn("operation thud(@dummy_annotation text?) {}", "OPERATION:[:thud]:text")

        val ns = "namespace a { namespace b { struct c {} } }"
        chkCompileDummyWarn("$ns operation fred(@dummy_annotation a.b.c) {}", "OPERATION:[:fred]:c")
        chkCompileDummyWarn("$ns operation waldo(@dummy_annotation a.b.c?) {}", "OPERATION:[:waldo]:c")
    }

    @Test fun testOperationHiddenParamAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("operation foo(@dummy_annotation x: integer) {}", dummyErr)
        chkCompile("operation bar(x: integer, @dummy_annotation y: text) {}", dummyErr)
        chkCompile("operation baz(@dummy_annotation a: gtv, z: big_integer) {}", dummyErr)
        chkCompile("operation quix(@dummy_annotation name) {}", dummyErr)
        chkCompile("operation quam(@dummy_annotation arr: byte_array) {}", dummyErr)
    }

    @Test fun testOperationParamAnnotationInvalidType() {
        chkCompile("operation foo(@test x: integer) {}", "ct_err:modifier:invalid:ann:test")
        chkCompile("operation bar(x: integer, @extendable y: text) {}", "ct_err:modifier:invalid:ann:extendable")
        chkCompile("operation baz(@extend(foo) a: gtv, z: big_integer) {}", "ct_err:modifier:invalid:ann:extend")
        chkCompile("@mount('x') operation quix(@sort name) {}", "ct_err:modifier:invalid:ann:sort")
        chkCompile("@extendable operation quam(@sort_desc dec: decimal) {}",
            "ct_err:[modifier:invalid:ann:extendable][modifier:invalid:ann:sort_desc]")
    }

    @Test fun testOperationParamModifierInvalidType() {
        chkCompile("operation foo(abstract x: integer) {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("operation foo(override x: integer) {}", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testOperationParamModifierWithKw() {
        chkCompile("operation foo(operation x: integer) {}", "ct_err:syntax")
        chkCompile("operation foo(key x: integer) {}", "ct_err:syntax")
    }

    @Test fun testQueryHiddenParamAnnotation() {
        def("query foo(@dummy_annotation x: integer): integer = x + 1;")
        def("query bar(x: integer, @dummy_annotation y: text): text { return (x).to_text() + y; }")
        def("query baz(@dummy_annotation a: gtv, z: big_integer): boolean { return z.to_gtv() == a; }")
        def("query quix(@dummy_annotation name): text = name.reversed();")
        def("query quam(@dummy_annotation dec: decimal) = dec * 3.1415;")
        def("query thud(@dummy_annotation text?) = \"Riders in black\";")
        chk("foo(-1)", "int[0]")
        chk("bar(12, \"ab\")", "text[12ab]")
        chk("baz(\"xyz\".to_gtv(), 9999999999999999999999999999L)", "boolean[false]")
        chk("quix(\"xela\")", "text[alex]")
        chk("quam(2)", "dec[6.283]")
        chk("thud(\"\")", "text[Riders in black]")
    }

    @Test fun testQueryHiddenParamAnnotationWarning() {
        chkCompileDummyWarn("query foo(@dummy_annotation x: integer): integer = x + 1;", "QUERY:[:foo]:x")
        chkCompileDummyWarn("query bar(x: integer, @dummy_annotation y: text): text { return (x).to_text() + y; }", "QUERY:[:bar]:y")
        chkCompileDummyWarn("query baz(@dummy_annotation a: gtv, z: big_integer): boolean { return z.to_gtv() == a; }", "QUERY:[:baz]:a")
        chkCompileDummyWarn("query quix(@dummy_annotation name): text = name.reversed();", "QUERY:[:quix]:name")
        chkCompileDummyWarn("query quam(@dummy_annotation dec: decimal) = dec * 3.1415;", "QUERY:[:quam]:dec")
        chkCompileDummyWarn("query thud(@dummy_annotation text?) = \"Riders in black\";", "QUERY:[:thud]:text")

        val ns = "namespace a { namespace b { struct c {} } }"
        chkCompileDummyWarn("$ns query fred(@dummy_annotation a.b.c): integer { return 0; }", "QUERY:[:fred]:c")
        chkCompileDummyWarn("$ns query waldo(@dummy_annotation a.b.c?): boolean { return false; }", "QUERY:[:waldo]:c")
    }

    @Test fun testQueryHiddenParamAnnotationNoHiddenLib() {
        tst.hiddenLib = false
        val dummyErr = "ct_err:modifier:invalid:ann:dummy_annotation"
        chkCompile("query foo(@dummy_annotation x: integer): integer = x + 1;", dummyErr)
        chkCompile("query bar(x: integer, @dummy_annotation y: text): text { return (x).to_text() + y; }", dummyErr)
        chkCompile("query baz(@dummy_annotation a: gtv, z: big_integer): boolean { return z.to_gtv() == a; }", dummyErr)
        chkCompile("query quix(@dummy_annotation name): text = name.reversed();", dummyErr)
        chkCompile("query quam(@dummy_annotation dec: decimal) = dec * 3.1415;", dummyErr)
    }

    @Test fun testQueryParamAnnotationInvalidType() {
        chkCompile("query foo(@test x: integer) { return x + 1; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("query bar(x: integer, @extendable y: text) { return x\"DEADBEEF\"; }",
            "ct_err:modifier:invalid:ann:extendable")
        chkCompile("query baz(@extend(foo) a: gtv, z: big_integer): boolean { return z.to_gtv() == a; }",
            "ct_err:modifier:invalid:ann:extend")
        chkCompile("@mount('x') query quix(@sort name) = name.reversed();", "ct_err:modifier:invalid:ann:sort")
        chkCompile("query quam(@sort_desc dec: decimal) = dec * 3.1415;", "ct_err:modifier:invalid:ann:sort_desc")
    }

    @Test fun testQueryParamModifierInvalidType() {
        chkCompile("query foo(abstract x: integer) { return x + 1; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("query foo(override x: integer) = x - 1;", "ct_err:modifier:invalid:kw:override")
    }

    @Test fun testQueryParamModifierWithKw() {
        chkCompile("query foo(operation x: integer) { return x + 1; }", "ct_err:syntax")
        chkCompile("query foo(key x: integer) = x - 1;", "ct_err:syntax")
    }

    private fun chkCompileDummyWarn(code: String, warningSuffix: String) {
        val warningPrefix = "param:dummy_annotation:annotation_present"
        chkCompile(code, "OK")
        chkWarn("$warningPrefix:$warningSuffix")
    }
}
