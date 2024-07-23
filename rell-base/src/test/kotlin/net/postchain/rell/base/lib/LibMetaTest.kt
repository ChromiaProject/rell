/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibMetaTest: BaseRellTest() {
    @Test fun testType() {
        chkCompile("struct data { m: rell.meta; }", "OK")
        chkCompile("struct data { m: set<rell.meta>; }", "OK")
        chkCompile("function f(m: rell.meta) = m.to_gtv();", "ct_err:fn:invalid:rell.meta:to_gtv")
        chkCompile("function f() = rell.meta.from_gtv(gtv.from_bytes(x''));", "ct_err:fn:invalid:rell.meta:from_gtv")
    }

    @Test fun testConstructor() {
        def("entity data {}")
        def("object state {}")
        def("operation op() {}")
        def("query qq() = 0;")

        chk("_type_of(rell.meta(data))", "text[rell.meta]")
        chk("_type_of(rell.meta(state))", "text[rell.meta]")
        chk("_type_of(rell.meta(op))", "text[rell.meta]")
        chk("_type_of(rell.meta(qq))", "text[rell.meta]")

        chk("rell.meta(data)", "rell.meta[:data]")
        chk("rell.meta(state)", "rell.meta[:state]")
        chk("rell.meta(op)", "rell.meta[:op]")
        chk("rell.meta(qq)", "rell.meta[:qq]")
    }

    @Test fun testConstructorComplexName() {
        file("lib.rell", """
            module;
            namespace b {
                entity data {}
                object state {}
                operation op() {}
                query qq() = 0;
            }
        """)
        def("namespace a { import lib; }")

        chk("rell.meta(a.lib.b.data)", "rell.meta[lib:b.data]")
        chk("rell.meta(a.lib.b.state)", "rell.meta[lib:b.state]")
        chk("rell.meta(a.lib.b.op)", "rell.meta[lib:b.op]")
        chk("rell.meta(a.lib.b.qq)", "rell.meta[lib:b.qq]")
    }

    @Test fun testConstructorUnsupportedDef() {
        def("struct rec {}")
        def("enum color { red }")
        def("function f() {}")
        def("val X = 123;")
        def("namespace ns {}")

        chk("rell.meta(rec)", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta(color)", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta(f)", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta(X)", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta(ns)", "ct_err:expr_call:bad_arg:[rell.meta]")
    }

    @Test fun testConstructorBadArgument() {
        def("entity data {}")
        chk("rell.meta()", "ct_err:fn:sys:wrong_arg_count:1:1:0")
        chk("rell.meta(0)", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta('data')", "ct_err:expr_call:bad_arg:[rell.meta]")
        chk("rell.meta(data, 0)", "ct_err:fn:sys:wrong_arg_count:1:1:2")
        chk("rell.meta(foo)", "ct_err:unknown_name:foo")
    }

    @Test fun testUseAsConstant() {
        def("entity data {}")
        def("operation op() {}")
        def("val data_meta = rell.meta(data);")
        def("val op_meta = rell.meta(op);")

        chk("_type_of(data_meta)", "text[rell.meta]")
        chk("_type_of(op_meta)", "text[rell.meta]")
        chk("data_meta", "rell.meta[:data]")
        chk("op_meta", "rell.meta[:op]")
    }

    @Test fun testDefinitionNames() {
        file("app/lib.rell", "module; namespace a.b { entity data {} }")
        def("object state {}")
        def("import app.lib.{a.b.data};")
        chkMeta("", "rell.meta(data)", "entity", "app.lib:a.b.data", "app.lib", "app.lib:a.b.data", "data", "a.b.data")
        chkMeta("", "rell.meta(state)", "object", ":state", "", ":state", "state", "state")
    }

    @Test fun testMountName() {
        def("entity data {}")
        def("object state {}")
        def("operation op() {}")
        def("query qq() = 0;")
        def("@mount('my_data') entity data_2 {}")
        def("@mount('my_state') object state_2 {}")
        def("@mount('my_op') operation op_2() {}")
        def("@mount('my_qq') query qq_2() = 0;")
        def("namespace ns { entity data_3 {} }")
        def("@mount('foo.bar') object state_3 {}")

        chk("_type_of(rell.meta(data).mount_name)", "text[text]")

        chk("rell.meta(data).mount_name", "text[data]")
        chk("rell.meta(state).mount_name", "text[state]")
        chk("rell.meta(op).mount_name", "text[op]")
        chk("rell.meta(qq).mount_name", "text[qq]")

        chk("rell.meta(data_2).mount_name", "text[my_data]")
        chk("rell.meta(state_2).mount_name", "text[my_state]")
        chk("rell.meta(op_2).mount_name", "text[my_op]")
        chk("rell.meta(qq_2).mount_name", "text[my_qq]")

        chk("rell.meta(ns.data_3).mount_name", "text[ns.data_3]")
        chk("rell.meta(state_3).mount_name", "text[foo.bar]")
    }

    @Test fun testToText() {
        file("app/lib.rell", "module; namespace a.b { entity data {} }")
        def("namespace ns { object state {} }")
        def("operation op() {}")
        def("import app.lib.{a.b.data};")

        chk("rell.meta(data).to_text()", "ct_err:unknown_member:[rell.meta]:to_text")
        chk("rell.meta(ns.state).to_text()", "ct_err:unknown_member:[rell.meta]:to_text")
        chk("rell.meta(op).to_text()", "ct_err:unknown_member:[rell.meta]:to_text")

        chk("'' + rell.meta(data)", "text[meta[app.lib:a.b.data]]")
        chk("'' + rell.meta(ns.state)", "text[meta[:ns.state]]")
        chk("'' + rell.meta(op)", "text[meta[:op]]")
    }

    @Test fun testCurrentModule() {
        val fn = "rell.meta.current_module()"
        file("module.rell", "function x() = $fn; namespace ns { function y() = $fn; }")
        file("dir/module.rell", "function m() = $fn; namespace ns1 { function m2() = $fn; }")
        file("dir/a.rell", "function a() = $fn; namespace ns2 { function a2() = $fn; }")
        file("file.rell", "module; function f() = $fn; namespace ns { function g() = $fn; }")
        def("import dir;")
        def("import file;")

        chkMetaMod("", fn, "", "", "")
        chkMetaMod("", "x()", "", "", "")
        chkMetaMod("", "ns.y()", "", "", "")

        chkMetaMod("", "dir.m()", "dir", "dir", "")
        chkMetaMod("", "dir.ns1.m2()", "dir", "dir", "")
        chkMetaMod("", "dir.a()", "dir", "dir", "")
        chkMetaMod("", "dir.ns2.a2()", "dir", "dir", "")

        chkMetaMod("", "file.f()", "file", "file", "")
        chkMetaMod("", "file.ns.g()", "file", "file", "")
    }

    @Test fun testCurrentModuleNested() {
        val fn = "rell.meta.current_module()"
        file("a/module.rell", "function f() = $fn;")
        file("a/b/module.rell", "function f() = $fn;")
        file("a/b/c/module.rell", "function f() = $fn;")
        def("import a; import a.b; import a.b.c;")

        chkMetaMod("", "a.f()", "a", "a", "")
        chkMetaMod("", "b.f()", "a.b", "b", "")
        chkMetaMod("", "c.f()", "a.b.c", "c", "")
    }

    @Test fun testCurrentModuleMountName() {
        val fn = "rell.meta.current_module()"
        file("a.rell", "module; function f() = $fn;")
        file("b.rell", "@mount('mnt_b') module; function f() = $fn;")
        file("c/module.rell", "@mount('mnt_c') module; function f() = $fn;")
        file("c/d.rell", "module; function f() = $fn;")
        file("e/module.rell", "module; function f() = $fn;")
        file("e/f/module.rell", "@mount('mnt_f') module; function f() = $fn;")
        file("e/f/g/module.rell", "module; function f() = $fn;")
        file("e/f/g/h/module.rell", "@mount('.mnt_h') module; function f() = $fn;")
        file("e/f/g/h/i/module.rell", "@mount('mnt_i') module; function f() = $fn;")
        def("import a; import b; import c; import c.d;")
        def("import e; import e.f; import e.f.g; import e.f.g.h; import e.f.g.h.i;")

        chkMetaMod("", "a.f()", "a", "a", "")
        chkMetaMod("", "b.f()", "b", "b", "mnt_b")
        chkMetaMod("", "c.f()", "c", "c", "mnt_c")
        chkMetaMod("", "d.f()", "c.d", "d", "mnt_c")
        chkMetaMod("", "e.f()", "e", "e", "")
        chkMetaMod("", "f.f()", "e.f", "f", "mnt_f")
        chkMetaMod("", "g.f()", "e.f.g", "g", "mnt_f")
        chkMetaMod("", "h.f()", "e.f.g.h", "h", "mnt_f.mnt_h")
        chkMetaMod("", "i.f()", "e.f.g.h.i", "i", "mnt_i")
    }

    @Test fun testKindText() {
        def("entity data {}")
        def("object state {}")
        def("operation op() {}")
        def("query qq() = 0;")

        chk("rell.meta(data).kind_text", "text[entity]")
        chk("rell.meta(state).kind_text", "text[object]")
        chk("rell.meta(op).kind_text", "text[operation]")
        chk("rell.meta(qq).kind_text", "text[query]")
    }

    @Test fun testModule() {
        file("a.rell", "module;")
        file("b.rell", "@mount('mnt_b') module;")
        file("c/module.rell", "module;")
        file("c/d.rell", "@mount('mnt_d') module;")
        file("c/d/e/module.rell", "module;")
        def("import a; import b; import c; import c.d; import c.d.e;")

        chkMetaMod("", "rell.meta(a)", "a", "a", "")
        chkMetaMod("", "rell.meta(b)", "b", "b", "mnt_b")
        chkMetaMod("", "rell.meta(c)", "c", "c", "")
        chkMetaMod("", "rell.meta(d)", "c.d", "d", "mnt_d")
        chkMetaMod("", "rell.meta(e)", "c.d.e", "e", "mnt_d")
    }

    @Test fun testModuleComplex() {
        file("lib.rell", "module;")
        file("mid.rell", "module; import lib; import bil:lib;")
        file("s1.rell", "module; import lib;")
        file("s2.rell", "module; import bil:lib;")

        chkMetaMod("import lib;", "rell.meta(lib)", "lib", "lib", "")
        chkMetaMod("import mid;", "rell.meta(mid.lib)", "lib", "lib", "")
        chkMetaMod("import mid;", "rell.meta(mid.bil)", "lib", "lib", "")
        chkMetaMod("import mid.*;", "rell.meta(lib)", "lib", "lib", "")
        chkMetaMod("import mid.*;", "rell.meta(bil)", "lib", "lib", "")
        chkMetaMod("import a:mid.*;", "rell.meta(a.lib)", "lib", "lib", "")
        chkMetaMod("import a:mid.*;", "rell.meta(a.bil)", "lib", "lib", "")

        chkMetaMod("import mid.{lib,bil};", "rell.meta(lib)", "lib", "lib", "")
        chkMetaMod("import mid.{lib,bil};", "rell.meta(bil)", "lib", "lib", "")
        chkMetaMod("import a:mid.{lib,bil};", "rell.meta(a.lib)", "lib", "lib", "")
        chkMetaMod("import a:mid.{lib,bil};", "rell.meta(a.bil)", "lib", "lib", "")
        chkMetaMod("import mid.{x:lib,y:bil};", "rell.meta(x)", "lib", "lib", "")
        chkMetaMod("import mid.{x:lib,y:bil};", "rell.meta(y)", "lib", "lib", "")

        chkMetaMod("import s1.*; import s2.*;", "rell.meta(lib)", "lib", "lib", "")
        chkMetaMod("import s1.*; import s2.*;", "rell.meta(bil)", "lib", "lib", "")
    }

    private fun chkMetaMod(defs: String, expr: String, moduleName: String, simpleName: String, mountName: String) {
        chkMeta(defs, expr, "module", moduleName, moduleName, moduleName, simpleName, mountName)
    }

    private fun chkMeta(
        defs: String,
        expr: String,
        kind: String,
        value: String,
        moduleName: String,
        fullName: String,
        simpleName: String,
        mountName: String,
    ) {
        chkFull("$defs query q() = $expr;", "rell.meta[$value]")
        chkFull("$defs query q() = $expr.kind_text;", "text[$kind]")
        chkFull("$defs query q() = $expr.module_name;", "text[$moduleName]")
        chkFull("$defs query q() = $expr.full_name;", "text[$fullName]")
        chkFull("$defs query q() = $expr.simple_name;", "text[$simpleName]")
        chkFull("$defs query q() = $expr.mount_name;", "text[$mountName]")
    }
}
