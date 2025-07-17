/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.module

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class AbstractModuleTest: BaseRellTest() {
    @Test fun testNormalSimple() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")
        def("import lib; import imp;")
        chk("lib.f()", "int[123]")
    }

    @Test fun testNormalLessSimple() {
        file("custom.rell", "abstract module; abstract function f(x: integer): integer;")
        file("lib.rell", "abstract module; import custom; function g(x: integer): text = '' + custom.f(x);")
        def("import lib;")
        def("import custom; override function custom.f(x: integer): integer = x * x;")
        chk("lib.g(123)", "text[15129]")
    }

    @Test fun testOverrideMissing() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")
        chkCompile("import lib;", "ct_err:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import lib; import imp;", "OK")
    }

    @Test fun testOverrideMissing2() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("bad.rell", "module; import lib;")
        file("mid.rell", "abstract module; import lib;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")

        chkCompile("import lib;", "ct_err:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import bad;", "ct_err:bad.rell:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import mid;", "ct_err:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import lib; import imp;", "OK")
    }

    @Test fun testOverrideInMainModule() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("client.rell", "abstract module; import lib; function g(): integer = lib.f() * 456;")
        file("mistake.rell", "module; import lib; function g(): integer = lib.f() * 456;")

        chkCompile("import mistake;", "ct_err:mistake.rell:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import client;", "ct_err:override:missing:[lib:f]:[lib.rell:1]")
        chkOverFn("import client; import imp;", "client.g()", "int[56088]")
        chkOverFn("import client; import lib; override function lib.f(): integer = 456;", "client.g()", "int[207936]")

        chkCompile("import client; import lib; import imp; override function lib.f(): integer = 456;", """ct_err:
            [override:conflict:[lib:f]:[import:imp:imp:imp.rell:1]:[direct::main.rell:1]]
            [override:conflict:[lib:f]:[direct::main.rell:1]:[import:imp:imp:imp.rell:1]]
        """)
    }

    @Test fun testOverrideConflict() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp1.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("imp2.rell", "module; import lib; override function lib.f(): integer = 456;")

        chkCompile("import imp1; import imp2;", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:imp2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:imp2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)

        chkCompile("import lib; import imp1; import imp2;", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:imp2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:imp2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)

        chkOverFn("import lib; import imp1;", "lib.f()", "int[123]")
        chkOverFn("import lib; import imp2;", "lib.f()", "int[456]")
    }

    @Test fun testOverrideConflict2() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp1.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("imp2.rell", "module; import lib; override function lib.f(): integer = 456;")
        file("client1.rell", "module; import lib; import imp1; function p(): integer = lib.f();")
        file("client2.rell", "module; import lib; import imp2; function q(): integer = lib.f();")

        chkCompile("import client1; import client2;", """ct_err:
            [override:conflict:[lib:f]:[import:client1:imp1:imp1.rell:1]:[import:client2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:client2:imp2:imp2.rell:1]:[import:client1:imp1:imp1.rell:1]]
        """)

        chkOverFn("import client1;", "client1.p()", "int[123]")
        chkOverFn("import client2;", "client2.q()", "int[456]")
    }

    @Test fun testOverrideConflict3() {
        file("lib.rell", """
            abstract module;
            abstract function g(): integer;
            override function g(): integer = 123;
            override function g(): integer = 123;
        """)

        chkCompile("import lib;", """ct_err:
            [lib.rell:override:conflict:[lib:g]:[direct:lib:lib.rell:4]:[direct:lib:lib.rell:5]]
            [lib.rell:override:conflict:[lib:g]:[direct:lib:lib.rell:5]:[direct:lib:lib.rell:4]]
        """)
    }

    @Test fun testOverrideConflict4() {
        file("lib.rell", "abstract module; abstract function f(): integer; override function f(): integer = 123;")
        file("badimp.rell", "module; import lib; override function lib.f(): integer = 456;")

        chkCompile("import badimp;", """ct_err:
            [badimp.rell:override:conflict:[lib:f]:[import:lib:lib:lib.rell:1]:[direct:badimp:badimp.rell:1]]
            [badimp.rell:override:conflict:[lib:f]:[direct:badimp:badimp.rell:1]:[import:lib:lib:lib.rell:1]]
        """)

        chkOverFn("import lib;", "lib.f()", "int[123]")
    }

    @Test fun testOverrideConflictMainModules() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp1.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("imp2.rell", "module; import lib; override function lib.f(): integer = 456;")
        mainModule("imp1", "imp2")

        chkCompile("", """ct_err:
            [imp1.rell:override:conflict:[lib:f]:[direct:imp1:imp1.rell:1]:[direct:imp2:imp2.rell:1]]
            [imp2.rell:override:conflict:[lib:f]:[direct:imp2:imp2.rell:1]:[direct:imp1:imp1.rell:1]]
        """)
    }

    @Test fun testOverrideConflictMultiImport() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp1.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("imp2.rell", "module; import lib; override function lib.f(): integer = 456;")
        file("mid1.rell", "module; import imp1;")
        file("mid2.rell", "module; import imp2;")

        chkCompile("import imp1; import foo: imp1;", "OK")
        chkCompile("import imp1; import mid1;", "OK")

        chkCompile("import imp1; import imp2;", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:imp2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:imp2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)

        chkCompile("import imp1; import mid2;", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:mid2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:mid2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)

        chkCompile("import mid1; import mid2;", """ct_err:
            [override:conflict:[lib:f]:[import:mid1:imp1:imp1.rell:1]:[import:mid2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:mid2:imp2:imp2.rell:1]:[import:mid1:imp1:imp1.rell:1]]
        """)
    }

    @Test fun testOverrideNonAbstract() {
        file("lib.rell", "abstract module; abstract function f(): integer = 123; function g(): integer = 456;")
        chkCompile("import lib; override function lib.f(): integer = 987;", "OK")
        chkCompile("import lib; override function lib.g(): integer = 987;", "ct_err:fn:override:not_abstract:[lib:g]")
    }

    @Test fun testOverrideWrongSignature() {
        file("lib.rell", "abstract module; abstract function f(x: text, y: boolean): integer;")
        chkCompile("import lib; override function lib.f(x: text, y: boolean): integer = 123;", "OK")
        chkCompile("import lib; override function lib.f(x: text, y: boolean): text = 'Hello';",
                "ct_err:fn:override:ret_type:lib.f:[integer]:[text]")
        chkCompile("import lib; override function lib.f(x: text, y: boolean): integer? = 123;",
                "ct_err:fn:override:ret_type:lib.f:[integer]:[integer?]")
        chkCompile("import lib; override function lib.f(x: text, y: boolean) {}",
                "ct_err:fn:override:ret_type:lib.f:[integer]:[unit]")
        chkCompile("import lib; override function lib.f(x: integer, y: boolean): integer = 123;",
                "ct_err:fn:override:param_type:lib.f:0:x:[text]:[integer]")
        chkCompile("import lib; override function lib.f(x: text?, y: boolean): integer = 123;",
                "ct_err:fn:override:param_type:lib.f:0:x:[text]:[text?]")
        chkCompile("import lib; override function lib.f(x: text, y: boolean?): integer = 123;",
                "ct_err:fn:override:param_type:lib.f:1:y:[boolean]:[boolean?]")
        chkCompile("import lib; override function lib.f(x: text, y: integer): integer = 123;",
                "ct_err:fn:override:param_type:lib.f:1:y:[boolean]:[integer]")
    }

    @Test fun testOverrideWrongSignature2() {
        file("lib.rell", "abstract module; abstract function g(x: text, y: boolean);")
        chkCompile("import lib; override function lib.g(x: text, y: boolean) {}", "OK")
        chkCompile("import lib; override function lib.g(x: text, y: boolean): integer = 123;",
                "ct_err:fn:override:ret_type:lib.g:[unit]:[integer]")
        chkCompile("import lib; override function lib.g(x: text, y: boolean): text = 'Hello';",
                "ct_err:fn:override:ret_type:lib.g:[unit]:[text]")
    }

    @Test fun testOverrideWrongSignature3() {
        file("lib.rell", "abstract module; abstract function h(x: text, y: boolean): integer?;")
        chkCompile("import lib; override function lib.h(x: text, y: boolean): integer? = 123;", "OK")
        chkCompile("import lib; override function lib.h(x: text, y: boolean): integer = 123;", "OK")
        chkCompile("import lib; override function lib.h(x: text, y: boolean): text = 'Hello';",
                "ct_err:fn:override:ret_type:lib.h:[integer?]:[text]")
        chkCompile("import lib; override function lib.h(x: text, y: boolean) {}",
                "ct_err:fn:override:ret_type:lib.h:[integer?]:[unit]")
    }

    @Test fun testOverrideWrongSignature4() {
        file("lib.rell", "abstract module; abstract function p(x: text?, y: boolean?);")

        chkCompile("import lib; override function lib.p(x: text?, y: boolean?) {}", "OK")
        chkCompile("import lib; override function lib.p(x: text, y: boolean?) {}",
                "ct_err:fn:override:param_type:lib.p:0:x:[text?]:[text]")
        chkCompile("import lib; override function lib.p(x: text?, y: boolean) {}",
                "ct_err:fn:override:param_type:lib.p:1:y:[boolean?]:[boolean]")

        chkCompile("import lib; override function lib.p(x: text, y: boolean) {}", """ct_err:
            [fn:override:param_type:lib.p:0:x:[text?]:[text]]
            [fn:override:param_type:lib.p:1:y:[boolean?]:[boolean]]
        """)
    }

    @Test fun testOverrideWrongSignature5() {
        file("lib.rell", "abstract module; abstract function h(x: text, y: boolean): text;")

        chkCompile("import lib; override function lib.h(x: text, y: boolean): text = '';", "OK")
        chkCompile("import lib; override function lib.h(y: text, x: boolean): text = '';", "OK")

        chkCompile("import lib; override function lib.h(y: boolean, x: text): text = '';", """ct_err:
            [fn:override:param_type:lib.h:0:x:[text]:[boolean]]
            [fn:override:param_type:lib.h:1:y:[boolean]:[text]]
        """)

        chkCompile("import lib; override function lib.h(x: boolean, y: text): text = '';", """ct_err:
            [fn:override:param_type:lib.h:0:x:[text]:[boolean]]
            [fn:override:param_type:lib.h:1:y:[boolean]:[text]]
        """)

        chkOverFn("import lib; override function lib.h(y: text, x: boolean): text = '' + x;", "lib.h('X', true)", "text[true]")
        chkOverFn("import lib; override function lib.h(y: text, x: boolean): text = '' + y;", "lib.h('X', true)", "text[X]")
    }

    @Test fun testOverrideImportAlias() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("imp1.rell", "module; import bil: lib; override function bil.f(): integer = 123;")
        file("imp2.rell", "module; namespace x { namespace y { import z: lib; } } override function x.y.z.f(): integer = 456;")

        chkOverFn("import lib; import imp1;", "lib.f()", "int[123]")
        chkOverFn("import lib; import imp2;", "lib.f()", "int[456]")

        chkOverFn("import lib; import imp1; import imp2;", "lib.f()", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:imp2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:imp2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)
    }

    @Test fun testOverrideImportSpecific() {
        file("lib.rell", "abstract module; abstract function f(): integer = 123;")
        file("lib2.rell", "abstract module; abstract function f(): integer; function g() = 123;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 456; function g() = 789;")

        chkOverFn("import lib;", "lib.f()", "int[123]")
        chkOverFn("import lib; import imp;", "lib.f()", "int[456]")
        chkOverFn("import lib; import imp.*;", "lib.f()", "int[456]")
        chkOverFn("import lib; import imp.{g};", "lib.f()", "int[456]")

        chkOverFn("import lib2;", "lib2.f()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
        chkOverFn("import lib2;", "lib2.g()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
        chkOverFn("import lib2.*;", "f()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
        chkOverFn("import lib2.*;", "g()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
        chkOverFn("import lib2.{f};", "f()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
        chkOverFn("import lib2.{g};", "g()", "ct_err:override:missing:[lib2:f]:[lib2.rell:1]")
    }

    @Test fun testOverrideInSameModule() {
        file("lib.rell", "abstract module; abstract function f(): integer; override function f(): integer = 123;")
        chkOverFn("import lib;", "lib.f()", "int[123]")

        chkOverFn("import lib; override function lib.f(): integer = 456;", "lib.f()", """ct_err:
            [override:conflict:[lib:f]:[import:lib:lib:lib.rell:1]:[direct::main.rell:1]]
            [override:conflict:[lib:f]:[direct::main.rell:1]:[import:lib:lib:lib.rell:1]]
        """)
    }

    @Test fun testOverrideInNamespace() {
        file("lib.rell", """
            abstract module;
            namespace x { namespace y { abstract function f(): integer; } }
            override function x.y.f(): integer = 123;
        """)
        chkOverFn("import lib;", "lib.x.y.f()", "int[123]")
    }

    @Test fun testOverrideInNamespace2() {
        file("lib.rell", """
            abstract module;
            namespace x { namespace y { abstract function f(): integer; } override function y.f(): integer = 123; }
        """)
        chkOverFn("import lib;", "lib.x.y.f()", "int[123]")
    }

    @Test fun testOverrideInNamespace3() {
        file("lib.rell", "abstract module; namespace x { namespace y { abstract function f(): integer; } }")
        chkOverFn("import lib;", "lib.x.y.f()", "ct_err:override:missing:[lib:x.y.f]:[lib.rell:1]")
        chkOverFn("import lib; override function lib.x.y.f(): integer = 123;", "lib.x.y.f()", "int[123]")
    }

    @Test fun testOverrideInNamespace4() {
        file("lib.rell", "abstract module; namespace x { namespace y { abstract function f(): integer; } }")
        file("imp.rell", "module; namespace a { namespace b { import lib; } } override function a.b.lib.x.y.f(): integer = 123;")
        chkOverFn("import lib; import imp;", "lib.x.y.f()", "int[123]")
    }

    @Test fun testImportRegularToAbstract() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("zero.rell", "abstract module;")
        chkCompile("import lib;", "ct_err:override:missing:[lib:f]:[lib.rell:1]")
        chkCompile("import zero;", "OK")
    }

    @Test fun testDefaultImplementation() {
        file("lib.rell", "abstract module; abstract function f(): integer = 987;")
        file("imp1.rell", "module; import lib; override function lib.f(): integer = 123;")
        file("imp2.rell", "module; import lib; override function lib.f(): integer = 456;")

        chkOverFn("import lib;", "lib.f()", "int[987]")
        chkOverFn("import lib; import imp1;", "lib.f()", "int[123]")
        chkOverFn("import lib; import imp2;", "lib.f()", "int[456]")

        chkOverFn("import lib; import imp1; import imp2;", "lib.f()", """ct_err:
            [override:conflict:[lib:f]:[import:imp1:imp1:imp1.rell:1]:[import:imp2:imp2:imp2.rell:1]]
            [override:conflict:[lib:f]:[import:imp2:imp2:imp2.rell:1]:[import:imp1:imp1:imp1.rell:1]]
        """)
    }

    @Test fun testAbstractWrongDef() {
        file("lib.rell", "module;")
        chkCompile("abstract entity user {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("abstract object state {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("abstract operation op() {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("abstract query q() = 123;", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("abstract struct rec {}", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("abstract import lib;", "ct_err:modifier:invalid:kw:abstract")
    }

    @Test fun testOverrideWrongDef() {
        file("lib.rell", "module;")
        file("wrong.rell", "override module;")
        chkCompile("override entity user {}", "ct_err:modifier:invalid:kw:override")
        chkCompile("override object state {}", "ct_err:modifier:invalid:kw:override")
        chkCompile("override operation op() {}", "ct_err:modifier:invalid:kw:override")
        chkCompile("override query q() = 123;", "ct_err:modifier:invalid:kw:override")
        chkCompile("override struct rec {}", "ct_err:modifier:invalid:kw:override")
        chkCompile("override import lib;", "ct_err:modifier:invalid:kw:override")
        chkCompile("import wrong;", "ct_err:wrong.rell:modifier:invalid:kw:override")
    }

    @Test fun testModifierDuplicate() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("wrong.rell", "abstract abstract module;")
        chkCompile("import wrong;", "ct_err:wrong.rell:modifier:dup:kw:abstract")
        chkCompile("import lib; override override function lib.f(): integer = 123;", "ct_err:modifier:dup:kw:override")
        chkCompile("abstract override function f(): integer = 123;",
                "ct_err:[modifier:bad_combination:kw:abstract,kw:override][fn:abstract:non_abstract_module::f]")
        chkCompile("override abstract function f(): integer = 123;",
                "ct_err:[modifier:bad_combination:kw:override,kw:abstract][fn:abstract:non_abstract_module::f]")
    }

    @Test fun testAbstractInRegularModule() {
        file("lib1.rell", "module; abstract function f(): integer;")
        file("lib2.rell", "module; abstract function f(): integer = 123;")
        chkCompile("import lib1;", "ct_err:lib1.rell:fn:abstract:non_abstract_module:lib1:lib1:f")
        chkCompile("import lib2;", "ct_err:lib2.rell:fn:abstract:non_abstract_module:lib2:lib2:f")
        chkCompile("abstract function f(): integer;", "ct_err:fn:abstract:non_abstract_module::f")
    }

    @Test fun testAbstractMainModule() {
        tst.wrapInit = true
        file("module.rell", "abstract module;")
        chkCompile("", "ct_err:module.rell:module:main_abstract:")
        chk("123", "ct_err:module.rell:module:main_abstract:")
    }

    @Test fun testAbstractMainModule2() {
        tst.wrapInit = true
        file("app.rell", "abstract module;")
        mainModule("app")
        chkCompile("", "ct_err:app.rell:module:main_abstract:app")
        chk("123", "ct_err:app.rell:module:main_abstract:app")
    }

    @Test fun testFunctionQualifiedName() {
        file("lib.rell", "abstract module; abstract function foo.bar(): integer = 123;")
        chkCompile("import lib;", "ct_err:lib.rell:fn:qname_no_override:foo.bar")
        chkCompile("function foo.bar(): integer = 123;", "ct_err:fn:qname_no_override:foo.bar")
    }

    @Test fun testFunctionNoBody() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        file("wrong.rell", "abstract module; function f(): integer;")

        chkCompile("import wrong;", "ct_err:wrong.rell:fn:no_body:wrong:f")
        chkCompile("import lib; override function lib.f(): integer;", "ct_err:fn:no_body:lib.f")

        chkCompile("import lib; function lib.f(): integer;", """ct_err:
            [override:missing:[lib:f]:[lib.rell:1]]
            [fn:no_body:lib.f]
            [fn:qname_no_override:lib.f]
        """)

        chkCompile("function f(): integer;", "ct_err:fn:no_body:f")
        chkCompile("import lib; override function lib.f(): integer;", "ct_err:fn:no_body:lib.f")
    }

    @Test fun testDefaultBodyCompilationError() {
        file("lib.rell", "abstract module; abstract function f(): integer { val x: integer = 'Hello'; return 123; }")
        chkCompile("import lib; override function lib.f(): integer = 456;", "ct_err:lib.rell:stmt_var_type:x:[integer]:[text]")
    }

    @Test fun testAbstractReturnTypeNotSpecified() {
        file("lib.rell", "abstract module; abstract function f();")
        chkCompile("import lib; override function lib.f(): integer { return 123; }",
                "ct_err:fn:override:ret_type:lib.f:[unit]:[integer]")
        chkCompile("import lib; override function lib.f() { return 123; }",
                "ct_err:fn:override:ret_type:lib.f:[unit]:[integer]")
        chkCompile("import lib; override function lib.f() {}", "OK")
    }

    @Test fun testOverrideReturnTypeNotSpecified() {
        file("lib.rell", "abstract module; abstract function f(): integer;")
        chkCompile("import lib; override function lib.f() { return 'Hello'; }",
                "ct_err:fn:override:ret_type:lib.f:[integer]:[text]")
        def("import lib; override function lib.f() { return 123; }")
        chk("lib.f()", "int[123]")
    }

    @Test fun testStackTrace() {
        file("lib.rell", "abstract module; abstract function f(x: integer): integer;")
        file("imp.rell", "module; import lib; override function lib.f(x: integer) { require(x > 0); return 123; }")
        def("import lib;")
        def("import imp;")

        chk("lib.f(1)", "int[123]")
        chk("lib.f(0)", "req_err:null")
        chkStack("imp:lib.f(imp.rell:1)", ":q(main.rell:3)")
    }

    @Test fun testDefaultParameterValues() {
        file("lib.rell", "abstract module; abstract function f(x: integer = 123): text = 'lib:'+x;")
        file("imp.rell", "module; import lib; override function lib.f(x: integer = 456) = 'ext:'+x;")

        chkFull("import lib; query q() = lib.f();", "text[lib:123]")
        chkFull("import lib; query q() = lib.f(456);", "text[lib:456]")
        chkFull("import lib; import imp; query q() = lib.f();", "text[ext:123]")
        chkFull("import lib; import imp; query q() = lib.f(789);", "text[ext:789]")
    }

    private fun chkOverFn(defs: String, expr: String, expected: String) {
        chkFull("$defs query q() = $expr;", expected)
    }
}
