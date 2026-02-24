/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeDefinitionDocPosTest: BaseCodeDocTest() {
    @Test fun testModule() {
        file("file.rell", "module;")
        file("foo/a.rell", "")
        file("foo/b.rell", "")
        file("bar/module.rell", "module;")

        chkPos0("", "file", "file.rell:1")
        chkPos0("", "foo", "foo/a.rell:1")
        chkPos0("", "bar", "bar/module.rell:1")
    }

    @Test fun testDefs() {
        chkPos("struct data {}", "data")
        chkPos("entity data {}", "data")
        chkPos("object state {}", "state")
        chkPos("val X = 123;", "X")
        chkPos("function f() {}", "f")
        chkPos("operation op() {}", "op")
        chkPos("query q() = 123;", "q")
        chkPos("enum colors {}", "colors")
    }

    @Test fun testEnumValue() {
        val code = "enum colors {\nred,\ngreen,\nblue,\n}"
        chkPos0(code, ":colors.red", "main.rell:2")
        chkPos0(code, ":colors.green", "main.rell:3")
        chkPos0(code, ":colors.blue", "main.rell:4")
    }

    @Test fun testAttr() {
        chkAttr("struct")
        chkAttr("entity")
        chkAttr("object")
    }

    private fun chkAttr(defKind: String) {
        val code = "$defKind data {\nx: integer = 0;\ny:text = '';\nz:boolean = false;\n}"
        chkPos0(code, ":data.x", "main.rell:2")
        chkPos0(code, ":data.y", "main.rell:3")
        chkPos0(code, ":data.z", "main.rell:4")
    }

    @Test fun testParam() {
        val code = "function f(\nx:integer,\ny:text,\nz:boolean,\n) {}"
        chkPos0(code, ":f.x", "main.rell:2")
        chkPos0(code, ":f.y", "main.rell:3")
        chkPos0(code, ":f.z", "main.rell:4")
    }

    @Test fun testImport() {
        file("lib.rell", "module;\nval X = 123;")

        chkPos0("", "lib:X", "lib.rell:2")

        chkPos0("import lib;", ":lib", "n/a")
        chkPos0("import bil: lib;", ":bil", "n/a")
        chkPos0("import ns: lib.*;", ":ns", "n/a")

        chkPos0("import lib.{X};", ":X", "lib.rell:2")
        chkPos0("import lib.{Y:X};", ":Y", "main.rell:1")

        chkPos0("import ns:lib.{X};", ":ns", "n/a") //TODO support
        chkPos0("import ns:lib.{Y:X};", ":ns", "n/a") //TODO support
    }

    @Test fun testNamespace() {
        // Not supported ATM (not needed).
        chkPos0("namespace ns {}", ":ns", "n/a")
    }

    private fun chkPos(code: String, name: String) {
        chkPos0("\nnamespace root {\n$code\n}", ":root.$name", "main.rell:3")
    }

    private fun chkPos0(code: String, name: String, exp: String) {
        val act = processDocDef(code, name) { def ->
            def.docSourcePos?.str() ?: "n/a"
        }
        assertEquals(exp, act)
    }
}
