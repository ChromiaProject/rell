/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.RellVersions
import org.junit.Test

/** Version control tests for cases with no other suitable test class. */
class VersionControlTest: BaseRellTest(false) {
    @Test fun testNameListSetMap() {
        chkNameOldKw("list")
        chkNameOldKw("set")
        chkNameOldKw("map")
    }

    private fun chkNameOldKw(name: String) {
        chkVerCt("namespace ns { struct $name {} }", "0.11.0", "VER:name:$name")
        chkVerCt("namespace ns { function $name() {} }", "0.11.0", "VER:name:$name")
        chkVerCt("namespace ns { operation $name() {} }", "0.11.0", "VER:name:$name")
        chkVerCt("struct data { $name: integer; }", "0.11.0", "VER:name:$name")
        chkVerCt("entity data { $name: integer; }", "0.11.0", "VER:name:$name")

        chkVerCt("function f($name: integer) {}", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { val $name: integer; }", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { var $name: integer; }", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { val $name = 123; }", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { var $name = 123; }", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { val ($name, foo) = (123, 'A'); }", "0.11.0", "VER:name:$name")
        chkVerCt("function f() { val (foo, $name) = (123, 'A'); }", "0.11.0", "VER:name:$name")
        chkVerCt("function f(l: list<integer>) { for ($name in l) {} }", "0.11.0", "VER:name:$name")
        chkVerCt("function f(l: list<(integer,text)>) { for (($name,foo) in l) {} }", "0.11.0", "VER:name:$name")
        chkVerCt("function f(l: list<(integer,text)>) { for ((foo,$name) in l) {} }", "0.11.0", "VER:name:$name")
    }

    @Test fun testNameReserved() {
        chkNameReserved("alias")
        chkNameReserved("as")
        chkNameReserved("catch")
        chkNameReserved("const")
        chkNameReserved("finally")
        chkNameReserved("final")
        chkNameReserved("fun")
        chkNameReserved("internal")
        chkNameReserved("is")
        chkNameReserved("native")
        chkNameReserved("private")
        chkNameReserved("protected")
        chkNameReserved("savepoint")
        chkNameReserved("sealed")
        chkNameReserved("static")
        chkNameReserved("super")
        chkNameReserved("this")
        chkNameReserved("throw")
        chkNameReserved("trait")
        chkNameReserved("transact")
        chkNameReserved("try")
        chkNameReserved("typealias")
        chkNameReserved("yield")
    }

    private fun chkNameReserved(name: String) {
        val sinceVer = "0.13.12"
        val prevVer = RellTestUtils.getPrevVersion(sinceVer)

        tst.compatibilityVer(RellVersions.VERSION_STR)
        chkNameReserved0(name, "ct_err:name:reserved:$name:$sinceVer")
        tst.compatibilityVer(sinceVer)
        chkNameReserved0(name, "ct_err:name:reserved:$name:$sinceVer")
        tst.compatibilityVer(prevVer)
        chkNameReserved0(name, "OK")
        tst.compatibilityVer(null)
        chkNameReserved0(name, "OK")
    }

    private fun chkNameReserved0(name: String, exp: String) {
        chkNameReservedDef0(name, exp)
        chkNameReservedFunction0(name, exp)
        chkNameReservedImport0(name, exp)
    }

    private fun chkNameReservedDef0(name: String, exp: String) {
        chkCompile("namespace ns { val $name = 0; }", exp)
        chkCompile("namespace ns { struct $name {} }", exp)
        chkCompile("namespace ns { entity $name {} }", exp)
        chkCompile("namespace ns { object $name {} }", exp)
        chkCompile("namespace ns { enum $name {} }", exp)
        chkCompile("namespace ns { function $name() {} }", exp)
        chkCompile("namespace ns { operation $name() {} }", exp)
        chkCompile("namespace ns { query $name() = 0; }", exp)
        chkCompile("namespace ns { namespace $name {} }", exp)
        chkCompile("namespace ns { namespace a.$name.b {} }", exp)
        chkCompile("struct data { $name: integer; }", exp)
        chkCompile("entity data { $name: integer; }", exp)
    }

    private fun chkNameReservedFunction0(name: String, exp: String) {
        chkCompile("function f($name: integer) {}", exp)
        chkCompile("function f() { val $name: integer; }", exp)
        chkCompile("function f() { var $name: integer; }", exp)
        chkCompile("function f() { val $name = 123; }", exp)
        chkCompile("function f() { var $name = 123; }", exp)
        chkCompile("function f() { val ($name, foo) = (123, 'A'); }", exp)
        chkCompile("function f() { val (foo, $name) = (123, 'A'); }", exp)
        chkCompile("function f(l: list<integer>) { for ($name in l) {} }", exp)
        chkCompile("function f(l: list<(integer,text)>) { for (($name,foo) in l) {} }", exp)
        chkCompile("function f(l: list<(integer,text)>) { for ((foo,$name) in l) {} }", exp)
    }

    private fun chkNameReservedImport0(name: String, exp: String) {
        val t = RellCodeTester(tstCtx)
        t.file("lib.rell", "module; val X = 123;")
        t.file("$name.rell", "module;")
        t.file("a/$name.rell", "module;")
        t.file("b/$name/c.rell", "module;")
        t.compatibilityVer = tst.compatibilityVer

        t.chkCompile("namespace ns { import $name: lib; }", exp)
        t.chkCompile("namespace ns { import $name: lib.*; }", exp)
        t.chkCompile("namespace ns { import lib.{$name:X}; }", exp)
        t.chkCompile("namespace ns { import $name: lib.{X}; }", exp)

        t.chkCompile("namespace ns { import $name; }", exp)
        t.chkCompile("namespace ns { import a.$name; }", exp)
        t.chkCompile("namespace ns { import foo: a.$name; }", exp)
        t.chkCompile("namespace ns { import b.$name.c; }", exp)
        t.chkCompile("namespace ns { import foo: b.$name.c; }", exp)
    }

    @Test fun testGtvBigInteger() {
        val gtv = "gtv.from_bytes(x'a60302017b')"
        chk(gtv, "gtv[123L]")

        chkVerRtExpr("integer.from_gtv($gtv)", "0.11.0", "gtv_err:type:[integer]:INTEGER:BIGINTEGER", "int[123]")
        chkVerRtExpr("decimal.from_gtv($gtv)", "0.11.0", "gtv_err:type:[decimal]:STRING:BIGINTEGER", "dec[123]")
    }
}
