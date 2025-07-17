/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class LibTest: BaseRellTest() {
    @Test fun testPrint() {
        chkEx("{ print('Hello'); return 123; }", "int[123]")
        chkOut("Hello")
        chkLog()

        chkEx("{ print(12345); return 123; }", "int[123]")
        chkOut("12345")
        chkLog()

        chkEx("{ print(1, 2, 3, 4, 5); return 123; }", "int[123]")
        chkOut("1 2 3 4 5")
        chkLog()

        chkEx("{ print(); return 123; }", "int[123]")
        chkOut("")
        chkLog()

        chkEx("{ print(null, 123, _nullable('hello')); return 123; }", "int[123]")
        chkOut("null 123 hello")
        chkLog()
    }

    @Test fun testLog() {
        def("function f() { log('this is f()'); }")

        chkEx("{ log('Hello'); return 123; }", "int[123]")
        chkLog("[:q(main.rell:2)] Hello")
        chkOut()

        chkEx("{ log(12345); return 123; }", "int[123]")
        chkLog("[:q(main.rell:2)] 12345")
        chkOut()

        chkEx("{ log(1, 2, 3, 4, 5); return 123; }", "int[123]")
        chkLog("[:q(main.rell:2)] 1 2 3 4 5")
        chkOut()

        chkEx("{ log(); return 123; }", "int[123]")
        chkOut()
        chkLog("[:q(main.rell:2)]")

        chkEx("{\n    log('Hello'); log('World');\n    log('Bye');\n    return 123;\n}", "int[123]")
        chkOut()
        chkLog("[:q(main.rell:3)] Hello", "[:q(main.rell:3)] World", "[:q(main.rell:4)] Bye")

        chkEx("{ f(); return 0; }", "int[0]")
        chkOut()
        chkLog("[:f(main.rell:1)] this is f()")

        chkEx("{ log(null, 123, _nullable('hello')); return 123; }", "int[123]")
        chkOut()
        chkLog("[:q(main.rell:2)] null 123 hello")
    }

    @Test fun testExists() {
        chkEmptyExists("exists", true)
    }

    @Test fun testEmpty() {
        chkEmptyExists("empty", false)
    }

    private fun chkEmptyExists(f: String, exists: Boolean) {
        def("function zlist(l: list<integer>?): list<integer>? = l;")
        def("function zmap(m: map<integer, text>?): map<integer, text>? = m;")

        chkEx("{ var x: integer? = _nullable(123); return $f(x); }", "boolean[${exists}]")
        chkEx("{ var x: integer? = null; return $f(x); }", "boolean[${!exists}]")

        chk("$f([123])", "boolean[${exists}]")
        chk("$f(zlist([123]))", "boolean[${exists}]")
        chk("$f(list<integer>())", "boolean[${!exists}]")
        chk("$f(zlist(list<integer>()))", "boolean[${!exists}]")
        chk("$f(zlist(null))", "boolean[${!exists}]")

        chk("$f([123 : 'Hello'])", "boolean[${exists}]")
        chk("$f(zmap([123 : 'Hello']))", "boolean[${exists}]")
        chk("$f(map<integer,text>())", "boolean[${!exists}]")
        chk("$f(zmap(map<integer,text>()))", "boolean[${!exists}]")
        chk("$f(zmap(null))", "boolean[${!exists}]")

        chk("$f(123)", "ct_err:expr_call_badargs:[$f]:[integer]")
        chk("$f(false)", "ct_err:expr_call_badargs:[$f]:[boolean]")
        chk("$f('Hello')", "ct_err:expr_call_badargs:[$f]:[text]")
        chk("$f(null)", "ct_err:expr_call_badargs:[$f]:[null]")
    }

    @Test fun testDeprecatedError() {
        tst.deprecatedError = true

        chkCompile("function f(v: GTXValue){}", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("function f(v: list<GTXValue>){}", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("struct rec { v: GTXValue; }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("struct rec { v: list<GTXValue>; }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("struct rec { v: map<text,GTXValue>; }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("struct rec { v: map<text,list<GTXValue?>>?; }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")

        chkCompile("function f() { GTXValue.from_bytes(x''); }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("function f() { GTXValue.from_json(''); }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
    }

    @Test fun testDeprecatedDefaultMode() {
        chkCompile("function f(v: GTXValue){}", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("struct rec { v: list<GTXValue>; }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")
        chkCompile("function f() { GTXValue.from_bytes(x''); }", "ct_err:deprecated:ALIAS:[rell:GTXValue]:gtv")

        chkWarn()
        chkFn("= is_signer(x'1234');", "boolean[false]")
        chkWarn("deprecated:FUNCTION:[rell:is_signer]:op_context.is_signer")

        chkFn("= op_context.is_signer(x'1234');", "boolean[false]")
        chkWarn()
    }

    @Test fun testDeprecatedFunctions() {
        tst.deprecatedError = true

        chkCompile("function f(x: integer?) { requireNotEmpty(x); }",
                "ct_err:deprecated:ALIAS:[rell:requireNotEmpty]:require_not_empty")

        chk("empty(_nullable_int(123))", "boolean[false]")

        chk("byte_array([1,2,3,4])", "ct_err:deprecated:CONSTRUCTOR:[rell:byte_array]:byte_array.from_list")
        chk("byte_array('1234')", "byte_array[1234]")
        chk("x'1234'.len()", "ct_err:deprecated:ALIAS:[rell:byte_array.len]:size")
        chk("x'1234'.decode()", "ct_err:deprecated:FUNCTION:[rell:byte_array.decode]:text.from_bytes")
        chk("x'1234'.toList()", "ct_err:deprecated:ALIAS:[rell:byte_array.toList]:to_list")

        chk("(123).hex()", "ct_err:deprecated:ALIAS:[rell:integer.hex]:to_hex")
        chk("integer.parseHex('1234')", "ct_err:deprecated:ALIAS:[rell:integer.parseHex]:from_hex")

        chk("'Hello'.len()", "ct_err:deprecated:ALIAS:[rell:text.len]:size")
        chk("'Hello'.upperCase()", "ct_err:deprecated:ALIAS:[rell:text.upperCase]:upper_case")
        chk("'Hello'.lowerCase()", "ct_err:deprecated:ALIAS:[rell:text.lowerCase]:lower_case")
        chk("'Hello'.compareTo('Bye')", "ct_err:deprecated:ALIAS:[rell:text.compareTo]:compare_to")
        chk("'Hello'.startsWith('Hell')", "ct_err:deprecated:ALIAS:[rell:text.startsWith]:starts_with")
        chk("'Hello'.endsWith('Hell')", "ct_err:deprecated:ALIAS:[rell:text.endsWith]:ends_with")
        chk("'Hello'.charAt(3)", "ct_err:deprecated:ALIAS:[rell:text.charAt]:char_at")
        chk("'Hello'.indexOf('ll')", "ct_err:deprecated:ALIAS:[rell:text.indexOf]:index_of")
        chk("'Hello'.lastIndexOf('ll')", "ct_err:deprecated:ALIAS:[rell:text.lastIndexOf]:last_index_of")
        chk("'Hello'.encode()", "ct_err:deprecated:ALIAS:[rell:text.encode]:to_bytes")

        chk("[1,2,3].indexOf(1)", "ct_err:deprecated:ALIAS:[rell:list.indexOf]:index_of")
        chk("[1,2,3].removeAt(1)", "ct_err:deprecated:ALIAS:[rell:list.removeAt]:remove_at")
        chk("[1,2,3].containsAll([1,3])", "ct_err:deprecated:ALIAS:[rell:collection.containsAll]:contains_all")
        chk("[1,2,3].removeAll([1,2])", "ct_err:deprecated:ALIAS:[rell:collection.removeAll]:remove_all")
        chk("[1,2,3].addAll([4,5,6])", "ct_err:deprecated:ALIAS:[rell:collection.addAll]:add_all")
        chk("[1,2,3].len()", "ct_err:deprecated:ALIAS:[rell:collection.len]:size")
        chk("[1,2,3]._set(0, 1)", "ct_err:deprecated:ALIAS:[rell:list._set]:set")

        chk("set([1,2,3]).containsAll([1,3])", "ct_err:deprecated:ALIAS:[rell:collection.containsAll]:contains_all")
        chk("set([1,2,3]).removeAll([1,2])", "ct_err:deprecated:ALIAS:[rell:collection.removeAll]:remove_all")
        chk("set([1,2,3]).addAll([4,5,6])", "ct_err:deprecated:ALIAS:[rell:collection.addAll]:add_all")
        chk("set([1,2,3]).len()", "ct_err:deprecated:ALIAS:[rell:collection.len]:size")

        chk("[123:'Hello'].len()", "ct_err:deprecated:ALIAS:[rell:map.len]:size")
        chkEx("{ [123:'Hello'].putAll([456:'Bye']); return 0; }", "ct_err:deprecated:ALIAS:[rell:map.putAll]:put_all")

        chk("(123).signum()", "ct_err:deprecated:ALIAS:[rell:integer.signum]:sign")
        chk("(123.0).signum()", "ct_err:deprecated:ALIAS:[rell:decimal.signum]:sign")
    }

    @Test fun testDeprecatedFunctionsGtv() {
        tst.deprecatedError = true
        def("struct rec { x: integer; }")

        chk("gtv.fromBytes(x'1234')", "ct_err:deprecated:ALIAS:[rell:gtv.fromBytes]:from_bytes")
        chk("gtv.fromJSON('{}')", "ct_err:deprecated:ALIAS:[rell:gtv.fromJSON]:from_json")
        chk("gtv.fromJSON(json('{}'))", "ct_err:deprecated:ALIAS:[rell:gtv.fromJSON]:from_json")
        chk("rec(5).to_gtv().toBytes()", "ct_err:deprecated:ALIAS:[rell:gtv.toBytes]:to_bytes")
        chk("rec(5).to_gtv().toJSON()", "ct_err:deprecated:ALIAS:[rell:gtv.toJSON]:to_json")

        chk("rec.fromBytes(x'1234')", "ct_err:deprecated:ALIAS:[rell:rell.struct_ext.fromBytes]:from_bytes")
        chk("rec.fromGTXValue(gtv.from_bytes(x'1234'))",
            "ct_err:deprecated:FUNCTION:[rell:rell.struct_ext.fromGTXValue]:from_gtv")
        chk("rec.fromPrettyGTXValue(gtv.from_bytes(x'1234'))",
            "ct_err:deprecated:FUNCTION:[rell:rell.struct_ext.fromPrettyGTXValue]:from_gtv_pretty")
        chk("rec(5).toBytes()", "ct_err:deprecated:ALIAS:[rell:rell.struct_ext.toBytes]:to_bytes")
        chk("rec(5).toGTXValue()", "ct_err:deprecated:FUNCTION:[rell:rell.struct_ext.toGTXValue]:to_gtv")
        chk("rec(5).toPrettyGTXValue()",
            "ct_err:deprecated:FUNCTION:[rell:rell.struct_ext.toPrettyGTXValue]:to_gtv_pretty")
    }

    @Test fun testRellNamespaceConflict() {
        chkCompile("function rell() = 0;", "ct_err:name_conflict:sys:rell:NAMESPACE")
        chkCompile("namespace rell {}", "ct_err:name_conflict:sys:rell:NAMESPACE")
    }

    @Test fun testSysQueries() {
        chk("rell.get_rell_version()", "ct_err:unknown_name:[rell:rell]:get_rell_version")
        chk("rell.get_app_structure()", "ct_err:unknown_name:[rell:rell]:get_app_structure")
    }

    @Test fun testVersionProdLib() {
        chkVerCt("function f() = crypto.eth_privkey_to_address(x'');", "0.13.5",
            "VER:lib:FUNCTION:[rell:crypto.eth_privkey_to_address]")
        chkVerCt("function f(m: rell.meta) {}", "0.13.5", "VER:lib:TYPE:[rell:rell.meta]")
        chkVerCt("function f() = null.to_gtv();", "0.10.6", "VER:lib:FUNCTION:[rell:null_ext.to_gtv]")
    }

    @Test fun testVersionTestLib() {
        tst.testLib = true
        tst.compatibilityVer("0.11.0")
        chkCompile("function f(m: rell.meta) {}", "ct_err:version:lib:TYPE:[rell:rell.meta]:0.13.5:0.11.0")
        chkCompile("function f() = rell.test.DEFAULT_FIRST_BLOCK_TIME;", "OK")
        chkCompile("function f() = rell.test.block_interval;", "OK")
        chkCompile("function f() { rell.test.set_block_interval(0); }", "OK")
    }
}
