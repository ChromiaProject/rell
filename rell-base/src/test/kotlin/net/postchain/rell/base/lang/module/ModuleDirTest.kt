/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.module

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import kotlin.test.Test

class ModuleDirTest: BaseRellTest() {
    @Test fun testImportFileDirConflict() {
        chkFileDirConflict("a/foo.rell", "module;", "a/foo/sub.rell", "module;", "a.foo", "ct_err:import:file_dir:a.foo")
        chkFileDirConflict("a/foo.rell", "module;", "a/foo/sub.rell", "", "a.foo", "ct_err:import:file_dir:a.foo")
        chkFileDirConflict("a/foo.rell", "", "a/foo/sub.rell", "module;", "a.foo", "ct_err:import:file_dir:a.foo")
        chkFileDirConflict("a/foo.rell", "", "a/foo/sub.rell", "", "a.foo", "ct_err:import:file_dir:a.foo")

        val e = "[main.rell:import:file_dir:a.foo]"
        chkFileDirConflict("a/foo.rell", "syntax error;", "a/foo/sub.rell", "module;", "a.foo", "ct_err:$e[a/foo.rell:syntax]")
        chkFileDirConflict("a/foo.rell", "syntax error;", "a/foo/sub.rell", "", "a.foo", "ct_err:$e[a/foo.rell:syntax]")
        chkFileDirConflict("a/foo.rell", "module;", "a/foo/sub.rell", "syntax error;", "a.foo", "ct_err:$e[a/foo/sub.rell:syntax]")
        chkFileDirConflict("a/foo.rell", "", "a/foo/sub.rell", "syntax error;", "a.foo", "ct_err:$e[a/foo/sub.rell:syntax]")
        chkFileDirConflict("a/foo.rell", "syntax error;", "a/foo/sub.rell", "syntax error;", "a.foo",
                "ct_err:$e[a/foo/sub.rell:syntax][a/foo.rell:syntax]")

        chkFileDirConflict("a/foo.rell", "module;", "a/foo/sub.txt", "", "a.foo", "OK")
        chkFileDirConflict("a/foo.rell", "", "a/foo/sub.txt", "", "a.foo", "ct_err:import:not_found:a.foo")
        chkFileDirConflict("a/foo.txt", "", "a/foo/sub.rell", "", "a.foo", "OK")
        chkFileDirConflict("a/foo.txt", "", "a/foo/sub.rell", "module;", "a.foo", "ct_err:import:not_found:a.foo")
    }

    private fun chkFileDirConflict(path1: String, text1: String, path2: String, text2: String, imp: String, exp: String) {
        val c = RellTestContext(useSql = false)
        val t = RellCodeTester(c)
        t.file(path1, text1)
        t.file(path2, text2)
        t.chkCompile("import $imp;", exp)
    }

    @Test fun testSyntaxError() {
        file("a/foo/code.rell", "syntax error;")
        chkCompile("import a.foo;", "ct_err:a/foo/code.rell:syntax")
        chkCompile("import a.foo; query q() = foo.f();", "ct_err:[main.rell:unknown_name:[foo]:f][a/foo/code.rell:syntax]")
    }

    @Test fun testNoRellFiles() {
        file("a/foo/code.txt", "Hello!")
        chkCompile("import a.foo;", "ct_err:import:not_found:a.foo")
    }

    @Test fun testErrorInOneFile() {
        file("a/a1.rell", "query q() = ERROR;")
        file("a/a2.rell", "")
        file("a/a3.rell", "")
        file("b/b1.rell", "")
        file("b/b2.rell", "query q() = ERROR;")
        file("b/b3.rell", "")
        file("c/c1.rell", "")
        file("c/c2.rell", "")
        file("c/c3.rell", "query q() = ERROR;")
        file("d/d1.rell", "")
        file("d/d2.rell", "")
        file("d/d3.rell", "module; query q() = ERROR;")
        chkCompile("import a;", "ct_err:a/a1.rell:unknown_name:ERROR")
        chkCompile("import b;", "ct_err:b/b2.rell:unknown_name:ERROR")
        chkCompile("import c;", "ct_err:c/c3.rell:unknown_name:ERROR")
        chkCompile("import d;", "OK")
        chkCompile("import d.d3;", "ct_err:d/d3.rell:unknown_name:ERROR")
    }

    @Test fun testNamesFromAllFilesAreImported() {
        file("a/a1.rell", "function f1(): integer = 123;")
        file("a/a2.rell", "function f2(): integer = 456;")
        file("a/a3.rell", "function f3(): integer = 789;")
        file("a/a4.rell", "module; function f4(): integer = 987;")
        chkImp("import a;", "a.f1()", "int[123]")
        chkImp("import a;", "a.f2()", "int[456]")
        chkImp("import a;", "a.f3()", "int[789]")
        chkImp("import a;", "a.f4()", "ct_err:unknown_name:[a]:f4")
        chkImp("import a.a4;", "a4.f4()", "int[987]")
    }

    @Test fun testDirModuleFilesSeeEachOthersDefs() {
        file("a/a1.rell", "function f(): integer = 123;")
        file("a/a2.rell", "function g(): integer = f();")
        file("b/b1.rell", "function g(): integer = f();")
        file("b/b2.rell", "function f(): integer = 456;")
        chkImp("import a;", "a.f()", "int[123]")
        chkImp("import a;", "a.g()", "int[123]")
        chkImp("import b;", "b.f()", "int[456]")
        chkImp("import b;", "b.g()", "int[456]")
    }

    @Test fun testFileModulesDoNotSeeEachOthersDefs() {
        file("a/a1.rell", "module; function f(): integer = 123;")
        file("a/a2.rell", "module; function g(): integer = f();")
        file("b/b1.rell", "module; function g(): integer = f();")
        file("b/b2.rell", "module; function f(): integer = 456;")
        chkImp("import a.a1;", "a1.f()", "int[123]")
        chkImp("import a.a2;", "a2.g()", "ct_err:a/a2.rell:unknown_name:f")
        chkImp("import b.b1;", "b1.g()", "ct_err:b/b1.rell:unknown_name:f")
        chkImp("import b.b2;", "b2.f()", "int[456]")
    }

    @Test fun testDirModuleFilesDoNotSeeFileModuleDefs() {
        file("a/a1.rell", "module; function f(): integer = 123;")
        file("a/a2.rell", "function g(): integer = f();")
        file("b/b1.rell", "module; function f(): integer = 123;")
        file("b/b2.rell", "import b.b1; function g(): integer = b1.f();")
        chkImp("import a;", "a.g()", "ct_err:a/a2.rell:unknown_name:f")
        chkImp("import b;", "b.g()", "int[123]")
    }

    @Test fun testFileModulesDoNotSeeDirModuleDefs() {
        file("a/a1.rell", "function g(): integer = 123;")
        file("a/a2.rell", "module; function f(): integer = g();")
        file("b/b1.rell", "function g(): integer = 123;")
        file("b/b2.rell", "module; import b; function f(): integer = b.g();")
        chkImp("import a.a2;", "a2.f()", "ct_err:a/a2.rell:unknown_name:g")
        chkImp("import b.b2;", "b2.f()", "int[123]")
    }

    @Test fun testInterFileNameConflict() {
        file("a/a1.rell", "function f(): integer = 123;")
        file("a/a2.rell", "function   f(): integer = 123;")
        file("a/a3.rell", "function      f(): integer = 456;")
        file("b/b1.rell", "function f(): integer = 123;")
        file("b/b2.rell", "module; function f(): integer = 123;")
        file("c/c1.rell", "module; function f(): integer = 123;")
        file("c/c2.rell", "function f(): integer = 123;")

        chkCompile("import a;", """ct_err:
            [a/a1.rell:name_conflict:user:f:FUNCTION:a/a2.rell(1:12)]
            [a/a2.rell:name_conflict:user:f:FUNCTION:a/a1.rell(1:10)]
            [a/a3.rell:name_conflict:user:f:FUNCTION:a/a1.rell(1:10)]
        """)

        chkImp("import b;", "b.f()", "int[123]")
        chkImp("import b.b2;", "b2.f()", "int[123]")
        chkImp("import c;", "c.f()", "int[123]")
        chkImp("import c.c1;", "c1.f()", "int[123]")
    }

    @Test fun testInterFileNameConflict2() {
        file("a/a1.rell", "module; function f(): integer = 123;")
        file("a/a2.rell", "module; function f(): integer = 123;")
        file("a/a3.rell", "module; function f(): integer = 456;")
        chkImp("import a.a1;", "a1.f()", "int[123]")
        chkImp("import a.a2;", "a2.f()", "int[123]")
        chkImp("import a.a3;", "a3.f()", "int[456]")
    }

    @Test fun testInterFileNamespace() {
        file("a/a1.rell", "namespace ns { function f(): integer = 123; }")
        file("a/a2.rell", "namespace ns { function g(): integer = 456; }")
        chkImp("import a;", "a.ns.f()", "int[123]")
        chkImp("import a;", "a.ns.g()", "int[456]")
    }

    @Test fun testInterFileNamespace2() {
        file("a/a1.rell", "namespace ns { function f(): integer = g() * 456; }")
        file("a/a2.rell", "namespace ns { function g(): integer = 123; }")
        chkImp("import a;", "a.ns.f()", "int[56088]")
        chkImp("import a;", "a.ns.g()", "int[123]")
    }

    @Test fun testInterFileNamespace3() {
        file("a/a1.rell", """
            namespace x {
                namespace y { function f1(): integer = f3() + 1; }
                namespace y { function f2(): integer = 123; }
            }
            namespace x {
                namespace y { function f3(): integer = y.g2() + 2; }
            }
        """)

        file("a/a2.rell", """
            namespace x {
                namespace y { function g1(): integer = x.y.f1() + 4; }
            }
            namespace x {
                namespace y { function g2(): integer = f2() + 3; }
            }
        """)

        chkImp("import a;", "a.x.y.f1()", "int[129]")
        chkImp("import a;", "a.x.y.f2()", "int[123]")
        chkImp("import a;", "a.x.y.f3()", "int[128]")
        chkImp("import a;", "a.x.y.g1()", "int[133]")
        chkImp("import a;", "a.x.y.g2()", "int[126]")
    }

    @Test fun testNoFilesInDirectory() {
        file("a/a.txt", "Hello")
        chkCompile("import a;", "ct_err:import:not_found:a")
    }

    @Test fun testNoDirFilesInDirectory() {
        file("a/a1.rell", "module;")
        file("a/a2.rell", "module;")
        chkCompile("import a;", "ct_err:import:not_found:a")
        chkCompile("import a.a1;", "OK")
        chkCompile("import a.a2;", "OK")
    }

    @Test fun testModuleRellNoHeader() {
        file("a/module.rell", "function f(): integer = 123;")
        file("b/module.rell", "function module_f(): integer = 123; function module_g(): integer = b1_f();")
        file("b/b1.rell", "function b1_f(): integer = 456; function b1_g(): integer = module_f();")
        chkImp("import a;", "a.f()", "int[123]")
        chkImp("import a.module;", "a.f()", "ct_err:syntax")
        chkImp("import b;", "b.module_f()", "int[123]")
        chkImp("import b;", "b.module_g()", "int[456]")
        chkImp("import b;", "b.b1_f()", "int[456]")
        chkImp("import b;", "b.b1_g()", "int[123]")
    }

    @Test fun testModuleRellWithHeader() {
        file("a/module.rell", "module; function f(): integer = 123;")
        file("b/module.rell", "module; function module_f(): integer = 123; function module_g(): integer = b1_f();")
        file("b/b1.rell", "function b1_f(): integer = 456; function b1_g(): integer = module_f();")
        chkImp("import a;", "a.f()", "int[123]")
        chkImp("import a.module;", "a.f()", "ct_err:syntax")
        chkImp("import b;", "b.module_f()", "int[123]")
        chkImp("import b;", "b.module_g()", "int[456]")
        chkImp("import b;", "b.b1_f()", "int[456]")
        chkImp("import b;", "b.b1_g()", "int[123]")
    }

    @Test fun testFileModulesDoNotSeeDefsOfModuleRell() {
        file("a/module.rell", "function f(): integer = 123;")
        file("a/a1.rell", "module; function g(): integer = f();")
        file("a/a2.rell", "module; import a; function g(): integer = a.f();")
        file("b/module.rell", "module; function f(): integer = 456;")
        file("b/b1.rell", "module; function g(): integer = f();")
        file("b/b2.rell", "module; import b; function g(): integer = b.f();")
        chkImp("import a;", "a.f()", "int[123]")
        chkImp("import a.a1;", "a1.g()", "ct_err:a/a1.rell:unknown_name:f")
        chkImp("import a.a2;", "a2.g()", "int[123]")
        chkImp("import b;", "b.f()", "int[456]")
        chkImp("import b.b1;", "b1.g()", "ct_err:b/b1.rell:unknown_name:f")
        chkImp("import b.b2;", "b2.g()", "int[456]")
    }

    @Test fun testModuleRellDoesNotSeeDefsFromFileModules() {
        file("a/module.rell", "function g(): integer = f();")
        file("a/a1.rell", "module; function f(): integer = 123;")
        file("b/module.rell", "module; function g(): integer = f();")
        file("b/b1.rell", "module; function f(): integer = 456;")
        file("c/module.rell", "import c.c1; function g(): integer = c1.f();")
        file("c/c1.rell", "module; function f(): integer = 789;")
        file("d/module.rell", "module; import d.d1; function g(): integer = d1.f();")
        file("d/d1.rell", "module; function f(): integer = 987;")
        chkImp("import a;", "a.g()", "ct_err:a/module.rell:unknown_name:f")
        chkImp("import b;", "b.g()", "ct_err:b/module.rell:unknown_name:f")
        chkImp("import c;", "c.g()", "int[789]")
        chkImp("import d;", "d.g()", "int[987]")
    }

    @Test fun testDefsFromSubDirFilesNotVisible() {
        file("a/a1.rell", "function g(): integer = f();")
        file("a/sub/sub1.rell", "function f(): integer = 123;")
        file("b/b1.rell", "import b.sub; function g(): integer = sub.f();")
        file("b/sub/sub1.rell", "function f(): integer = 456;")
        file("c/c1.rell", "function f(): integer = 789;")
        file("c/sub/sub1.rell", "function f(): integer = 987;")
        chkImp("import a;", "a.g()", "ct_err:a/a1.rell:unknown_name:f")
        chkImp("import b;", "b.g()", "int[456]")
        chkImp("import c;", "c.f()", "int[789]")
        chkImp("import c.sub;", "sub.f()", "int[987]")
    }

    @Test fun testDefsFromParentDirFilesNotVisible() {
        file("a/a1.rell", "function g(): integer = 123;")
        file("a/sub/sub1.rell", "function f(): integer = g();")
        file("b/b1.rell", "function g(): integer = 456;")
        file("b/sub/sub1.rell", "import b; function f(): integer = b.g();")
        chkImp("import a;", "a.g()", "int[123]")
        chkImp("import b;", "b.g()", "int[456]")
    }

    @Test fun testFilesSeeEachOthersImports() {
        file("a.rell", "module; function f(): integer = 123;")
        file("b/b1.rell", "import a;")
        file("b/b2.rell", "function g(): integer = a.f();")
        file("c/c1.rell", "import a; function p(): integer = a.f() * 456;")
        file("c/c2.rell", "import a; function q(): integer = a.f() * 789;")
        file("d/d1.rell", "function g(): integer = a.f();")
        file("d/d2.rell", "import a;")
        chkImp("import b;", "b.g()", "int[123]")
        chkImp("import c;", "c.p()", "int[56088]")
        chkImp("import c;", "c.q()", "int[97047]")
        chkImp("import d;", "d.g()", "int[123]")
    }

    @Test fun testFilesSeeEachOthersImports2() {
        file("a.rell", "module; function f(): integer = 123;")
        file("b/b1.rell", "import x: a;")
        file("b/b2.rell", "function g(): integer = x.f();")
        file("c/c1.rell", "import x: a; function p(): integer = x.f() * 456;")
        file("c/c2.rell", "import x: a; function q(): integer = x.f() * 789;")
        file("d/d1.rell", "function g(): integer = x.f();")
        file("d/d2.rell", "import x: a;")
        chkImp("import b;", "b.g()", "int[123]")
        chkImp("import c;", "c.p()", "int[56088]")
        chkImp("import c;", "c.q()", "int[97047]")
        chkImp("import d;", "d.g()", "int[123]")
    }

    @Test fun testImportsInterFileNameConflict() {
        file("a/x.rell", "module; function f(): integer = 123;")
        file("b/x.rell", "module; function f(): integer = 456;")
        file("c/c1.rell", "import a.x; function g1(): integer = x.f();")
        file("c/c2.rell", "import b.x; function g2(): integer = x.f();")
        file("d/d1.rell", "import a.x; function g(): integer = x.f();")
        file("d/d2.rell", "function   x(): integer = 789;")
        file("e/e1.rell", "function   x(): integer = 987;")
        file("e/e2.rell", "import a.x; function g(): integer = x();")
        file("f/f1.rell", "import a.x; function g1(): integer = x.f();")
        file("f/f2.rell", "import a.x; function g2(): integer = x.f();")

        chkCompile("import c;", """ct_err:
            [c/c1.rell:name_conflict:user:x:IMPORT:c/c2.rell(1:10)]
            [c/c2.rell:name_conflict:user:x:IMPORT:c/c1.rell(1:10)]
        """)

        chkCompile("import d;", """ct_err:
            [d/d1.rell:name_conflict:user:x:FUNCTION:d/d2.rell(1:12)]
            [d/d2.rell:name_conflict:user:x:IMPORT:d/d1.rell(1:10)]
        """)

        chkCompile("import d;", """ct_err:
            [d/d1.rell:name_conflict:user:x:FUNCTION:d/d2.rell(1:12)]
            [d/d2.rell:name_conflict:user:x:IMPORT:d/d1.rell(1:10)]
        """)

        chkCompile("import e;", """ct_err:
            [e/e1.rell:name_conflict:user:x:IMPORT:e/e2.rell(1:10)]
            [e/e2.rell:name_conflict:user:x:FUNCTION:e/e1.rell(1:12)]
        """)

        chkImp("import f;", "f.g1()", "int[123]")
        chkImp("import f;", "f.g2()", "int[123]")
    }

    private fun chkImp(imp: String, code: String, exp: String) {
        chkFull("$imp query q() = $code;", exp)
    }
}
