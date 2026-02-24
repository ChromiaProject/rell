/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestUtils
import org.apache.commons.lang3.StringUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class MountTest: BaseRellTest(useSql = true) {
    @Test fun testInvalidAnnotation() {
        chkCompile("@foo entity user { x: integer; }", "ct_err:modifier:invalid:ann:foo")
        chkCompile("@foo struct rec { x: integer; }", "ct_err:modifier:invalid:ann:foo")
        chkCompile("@foo function f(){}", "ct_err:modifier:invalid:ann:foo")
    }

    @Test fun testAllowedDefinitions() {
        file("sub.rell", "@mount('foo') module;")

        chkCompile("import sub;", "OK")
        chkCompile("@mount('foo') entity user { x: integer; }", "OK")
        chkCompile("@mount('foo') object state { mutable x: integer = 0; }", "OK")
        chkCompile("@mount('foo') operation o(){}", "OK")
        chkCompile("@mount('foo') query q() = 123;", "OK")
        chkCompile("@mount('foo') namespace ns {}", "OK")
        chkCompile("@mount('foo') @external('bar') namespace {}", "OK")

        chkCompile("@mount('foo') struct rec { x: integer; }", "ct_err:modifier:invalid:ann:mount")
        chkCompile("@mount('foo') enum en { A, B, C }", "ct_err:modifier:invalid:ann:mount")
        chkCompile("@mount('foo') function f(){}", "ct_err:modifier:invalid:ann:mount")
        chkCompile("@mount('foo') import sub;", "ct_err:modifier:invalid:ann:mount")
    }

    @Test fun testWrongArguments() {
        chkCompile("@mount query q() = 123;", "ct_err:ann:mount:arg_count:0")
        chkCompile("@mount() query q() = 123;", "ct_err:ann:mount:arg_count:0")
        chkCompile("@mount(123) query q() = 123;", "ct_err:ann:mount:arg_type:integer")
        chkCompile("@mount(true) query q() = 123;", "ct_err:ann:mount:arg_type:boolean")
        chkCompile("@mount(x'abcd') query q() = 123;", "ct_err:ann:mount:arg_type:byte_array")
        chkCompile("@mount('hello', 'world') query q() = 123;", "ct_err:ann:mount:arg_count:2")
        chkCompile("@mount('hello', 123) query q() = 123;", "ct_err:ann:mount:arg_count:2")
        chkCompile("@mount('hello') query q() = 123;", "OK")
        chkCompile("@mount(foo.bar) query q() = 123;", "ct_err:ann:arg:name_not_value:foo.bar")

        chkCompile("@mount('') query q() = 123;", "ct_err:ann:mount:empty:QUERY")
        chkCompile("@mount('foo') query q() = 123;", "OK")
        chkCompile("@mount('foo.bar') query q() = 123;", "OK")
        chkCompile("@mount('foo_bar') query q() = 123;", "OK")
        chkCompile("@mount('foo7') query q() = 123;", "OK")
        chkCompile("@mount('_foo') query q() = 123;", "OK")
        chkCompile("@mount('foo_') query q() = 123;", "OK")

        chkInvalidMountName(".")
        chkInvalidMountName("foo bar")
        chkInvalidMountName("foo-bar")
        chkInvalidMountName("3")
        chkInvalidMountName("7foo")
        chkInvalidMountName("foo[bar]")
        chkInvalidMountName("bar[foo]")
        chkInvalidMountName("[bar]")
    }

    private fun chkInvalidMountName(s: String) {
        chkCompile("@mount('$s') query q() = 123;", "ct_err:ann:mount:invalid:$s")
    }

    @Test fun testEntity() {
        def("@mount('foo.bar') entity user { name; }")
        insert("c0.foo.bar", "name", "0,'Bob'")
        chk("user @* {} ( .name )", "list<text>[text[Bob]]")
        chkOp("create user('Alice');")
        chkDataRaw("c0.foo.bar(0,Bob)", "c0.foo.bar(1,Alice)")
    }

    @Test fun testObject() {
        def("@mount('foo.bar') object state { mutable value: integer = 123; }")
        chkDataRaw("c0.foo.bar(0,123)")
        chkOp("state.value = 456;")
        chkDataRaw("c0.foo.bar(0,456)")
        chk("state.value", "int[456]")
    }

    @Test fun testNameTooLongEntityObject() {
        val name58 = "a".repeat(58)
        val name59 = "b".repeat(59)

        chkCompile("entity $name58 {}", "OK")
        chkCompile("entity $name59 {}", "ct_err:mount:too_long:entity:58:59:$name59")
        chkCompile("object $name58 {}", "OK")
        chkCompile("object $name59 {}", "ct_err:mount:too_long:entity:58:59:$name59")

        chkCompile("@mount('data') entity $name59 {}", "OK")
        chkCompile("@mount('$name59') entity data {}", "ct_err:mount:too_long:entity:58:59:$name59")
        chkCompile("@mount('data') object $name59 {}", "OK")
        chkCompile("@mount('$name59') object data {}", "ct_err:mount:too_long:entity:58:59:$name59")
    }

    @Test fun testNameTooLongAttr() {
        val name63 = "a".repeat(63)
        val name64 = "b".repeat(64)

        chkCompile("entity data { $name63: integer; }", "OK")
        chkCompile("entity data { $name64: integer; }", "ct_err:mount:too_long:attr:63:64:$name64")
        chkCompile("object data { $name63: integer = 0; }", "OK")
        chkCompile("object data { $name64: integer = 0; }", "ct_err:mount:too_long:attr:63:64:$name64")

        chkCompile("struct data { $name63: integer; }", "OK")
        chkCompile("struct data { $name64: integer; }", "OK")
    }

    @Test fun testNameTooLongVersionControl() {
        val name59 = "b".repeat(59)
        chkVerCt("entity $name59 {}", "0.12.0", "OK", "ct_err:mount:too_long:entity:58:59:$name59")
        chkVerCt("object $name59 {}", "0.12.0", "OK", "ct_err:mount:too_long:entity:58:59:$name59")
        chkVerCt("@mount('$name59') entity data {}", "0.12.0", "OK", "ct_err:mount:too_long:entity:58:59:$name59")
        chkVerCt("@mount('$name59') object data {}", "0.12.0", "OK", "ct_err:mount:too_long:entity:58:59:$name59")

        val name64 = "b".repeat(64)
        chkVerCt("entity data { $name64: integer; }", "0.12.0", "OK", "ct_err:mount:too_long:attr:63:64:$name64")
        chkVerCt("object data { $name64: integer = 0; }", "0.12.0", "OK", "ct_err:mount:too_long:attr:63:64:$name64")
    }

    @Test fun testOperation() {
        chkOpFull("@mount('foo.bar') operation some() { print('Hello!'); }", name = "foo.bar")
        chkOut("Hello!")
    }

    @Test fun testQuery() {
        chkFull("@mount('foo.bar') query some() = 123;", "foo.bar", listOf(), "int[123]")
    }

    @Test fun testEmptyMount() {
        file("sub.rell", "@mount('') module;")
        chkCompile("@mount('') entity user { x: integer; }", "ct_err:ann:mount:empty:ENTITY")
        chkCompile("@mount('') object state { mutable x: integer = 0; }", "ct_err:ann:mount:empty:OBJECT")
        chkCompile("@mount('') operation o(){}", "ct_err:ann:mount:empty:OPERATION")
        chkCompile("@mount('') query q() = 123;", "ct_err:ann:mount:empty:QUERY")
        chkCompile("@mount('') namespace ns {}", "OK")
        chkCompile("@mount('') @external('bar') namespace {}", "OK")
        chkCompile("import sub;", "OK")
    }

    // Module path does not affect mount names.
    @Test fun testSubmodules() {
        file("lib/module.rell", "module;")
        file("lib/foo/bar.rell", "entity user { name; }")
        def("import lib; import lib.foo;")

        insert("c0.user", "name", "0,'Bob'")
        chk("foo.user @* {} ( .name )", "list<text>[text[Bob]]")
        chkOp("create foo.user('Alice');")
        chkDataRaw("c0.user(0,Bob)", "c0.user(1,Alice)")
    }

    @Test fun testNamespaces() {
        val def = "object obj { x: integer = 123; }"

        chkMountName("$def", "c0.obj")
        chkMountName("@mount('foo.bar') $def", "c0.foo.bar")

        chkMountName("namespace ns { $def }", "c0.ns.obj")
        chkMountName("@mount('') namespace ns { $def }", "c0.obj")
        chkMountName("@mount('foo.bar') namespace ns { $def }", "c0.foo.bar.obj")

        chkMountName("namespace { $def }", "c0.obj")
        chkMountName("@mount('') namespace { $def }", "c0.obj")
        chkMountName("@mount('foo.bar') namespace { $def }", "c0.foo.bar.obj")
        chkMountName("@mount('foo.') namespace { $def }", "ct_err:ann:mount:tail:no_name:foo.:NAMESPACE")

        chkMountName("namespace ns1 { namespace ns2 { $def } }", "c0.ns1.ns2.obj")
        chkMountName("@mount('') namespace ns1 { namespace ns2 { $def } }", "c0.ns2.obj")
        chkMountName("@mount('foo.bar') namespace ns1 { namespace ns2 { $def } }", "c0.foo.bar.ns2.obj")

        chkMountName("namespace ns1 { @mount('') namespace ns2 { $def } }", "c0.obj")
        chkMountName("namespace ns1 { @mount('foo.bar') namespace ns2 { $def } }", "c0.foo.bar.obj")
        chkMountName("namespace ns1 { namespace ns2 { @mount('foo.bar') $def } }", "c0.foo.bar")

        chkMountName("@mount('foo.bar') namespace ns1 { namespace ns2 { @mount('bob.alice') $def } }", "c0.bob.alice")
        chkMountName("namespace ns1 { @mount('foo.bar') namespace ns2 { @mount('bob.alice') $def } }", "c0.bob.alice")
        chkMountName("@mount('foo.bar') namespace ns1 { @mount('bob.alice') namespace ns2 { $def } }", "c0.bob.alice.obj")
        chkMountName("@mount('foo.bar') namespace ns1 { @mount('') namespace ns2 { $def } }", "c0.obj")
    }

    @Test fun testComplexNamespaces() {
        val def = "object obj { x: integer = 123; }"
        chkMountName("$def", "c0.obj")
        chkMountName("@mount('foo.bar') $def", "c0.foo.bar")
        chkMountName("namespace ns1.ns2 { $def }", "c0.ns1.ns2.obj")
        chkMountName("@mount('') namespace ns1.ns2 { $def }", "c0.obj")
        chkMountName("@mount('foo.bar') namespace ns1.ns2 { $def }", "c0.foo.bar.obj")
        chkMountName("namespace ns1.ns2 { @mount('foo.bar') $def }", "c0.foo.bar")
        chkMountName("@mount('foo.bar') namespace ns1.ns2 { @mount('bob.alice') $def }", "c0.bob.alice")
    }

    private fun chkMountName(code: String, expected0: String) {
        val t = RellCodeTester(tstCtx)
        t.def(code)

        val expected = if (expected0.startsWith("ct_err:")) expected0 else "$expected0(0,123)"

        val actual = RellTestUtils.catchCtErr(false) {
            val dump = t.dumpDatabaseTables()
            assertEquals(1, dump.size)
            dump[0]
        }

        assertEquals(expected, actual)

    }

    @Test fun testSameNamespaceDifferentMounts() {
        def("""
            @mount('foo') namespace ns { object obj1 { x: integer = 123; } }
            @mount('bar') namespace ns { object obj2 { y: integer = 456; } }
        """)
        chkDataRaw("c0.bar.obj2(0,456)", "c0.foo.obj1(0,123)")
    }

    @Test fun testModule() {
        file("lib.rell", """
            @mount('foo.bar') module;
            object obj1 { x: integer = 111; }
            @mount('bob') object obj2 { x: integer = 222; }
            namespace ns1 { object obj3 { x: integer = 333; } }
            @mount('alice') namespace ns2 { object obj4 { x: integer = 444; } }
            @mount('') namespace ns3 { object obj5 { x: integer = 555; } }
        """)
        def("import lib;")
        chkDataRaw("c0.alice.obj4(0,444)", "c0.bob(0,222)", "c0.foo.bar.ns1.obj3(0,333)", "c0.foo.bar.obj1(0,111)", "c0.obj5(0,555)")
    }

    @Test fun testModule2() {
        file("lib/module.rell", "@mount('foo.bar') module; object obj1 { x: integer = 111; }")
        file("lib/part.rell", "object obj2 { x: integer = 222; }")
        def("import lib;")
        chkDataRaw("c0.foo.bar.obj1(0,111)", "c0.foo.bar.obj2(0,222)")
    }

    @Test fun testModule3() {
        chkFiles("a.b.c", "c0.o1(0,111)", "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }")

        chkFiles("a.b.c", "c0.foo.bar.o1(0,111)",
                "module.rell" to "@mount('foo.bar') module;",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.foo.bar.o1(0,111)",
                "a/module.rell" to "@mount('foo.bar') module;",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.o1(0,111)",
                "x/module.rell" to "@mount('foo.bar') module;",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.o1(0,111)",
                "a/b/module.rell" to "enum junk { X }",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.foo.bar.o1(0,111)",
                "a/module.rell" to "@mount('foo.bar') module;",
                "a/b/module.rell" to "enum junk { X }",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.o1(0,111)",
                "a/b/some.rell" to "enum junk { X }",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.foo.bar.o1(0,111)",
                "a/module.rell" to "@mount('foo.bar') module;",
                "a/b/some.rell" to "enum junk { X }",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )

        chkFiles("a.b.c", "c0.foo.bar.o1(0,111)",
                "a/module.rell" to "@mount('foo.bar') module;",
                "a/b/some.rell" to "@mount('some') module; enum junk { X }",
                "a/b/c/module.rell" to "module; object o1 { x: integer = 111; }"
        )
    }

    private fun chkFiles(module: String, expected: String, vararg files: Pair<String, String>) {
        val t = RellCodeTester(tstCtx)
        for ((path, text) in files) t.file(path, text)
        t.def("import $module;")
        t.chkDataRaw(expected)
    }

    @Test fun testConflictFileLevel() {
        chkConflictGeneric("main.rell", "main.rell") { foo, bar, exp ->
            val t = RellCodeTester(tstCtx)
            t.ideDefIdConflictError = false
            t.errMsgPos = true
            t.chkCompile("$foo\n$bar", exp)
        }
    }

    @Test fun testConflictModuleLevel() {
        chkConflictGeneric("lib/a.rell", "lib/b.rell") { foo, bar, exp ->
            val t = RellCodeTester(tstCtx)
            t.errMsgPos = true
            t.file("lib/a.rell", "$foo")
            t.file("lib/b.rell", "\n\n$bar")
            t.chkCompile("import lib;", exp)
        }
    }

    @Test fun testConflictAppLevel() {
        chkConflictGeneric("a/lib.rell", "b/lib.rell") { foo, bar, exp ->
            val t = RellCodeTester(tstCtx)
            t.errMsgPos = true
            t.file("a/lib.rell", "$foo")
            t.file("b/lib.rell", "\n\n$bar")
            t.chkCompile("import a; import b;", exp)
        }
    }

    private fun chkConflictGeneric(fooFile: String, barFile: String, testFn: (String, String, String) -> Unit) {
        val td = ConflictTestData(fooFile, barFile, testFn)

        chkConflictGenericErr(td, "ENTITY", "entity foo {}")
        chkConflictGenericErr(td, "OBJECT", "object foo {}")
        chkConflictGenericErr(td, "OPERATION", "operation foo(){}")
        chkConflictGenericErr(td, "QUERY", "query foo()=0;")

        chkConflictGenericErr(td, "ENTITY", "entity foo {}", "OBJECT", "object bar {}")
        chkConflictGenericErr(td, "OBJECT", "object foo {}", "ENTITY", "entity bar {}")

        chkConflictGenericOk(td, "entity foo {}", "operation bar() {}")
        chkConflictGenericOk(td, "entity foo {}", "query bar()=0;")
        chkConflictGenericOk(td, "object foo {}", "operation bar() {}")
        chkConflictGenericOk(td, "object foo {}", "query bar()=0;")
        chkConflictGenericOk(td, "operation foo() {}", "query bar()=0;")
    }

    private fun chkConflictGenericErr(td: ConflictTestData, kind: String, foo: String) {
        val bar = foo.replace("foo", "bar")
        chkConflictGenericErr(td, kind, foo, kind, bar)
    }

    private fun chkConflictGenericErr(td: ConflictTestData, fooKind: String, foo: String, barKind: String, bar: String) {
        val fooCol = foo.indexOf("foo") + 1
        val barCol = bar.indexOf("bar") + 1
        val fooPos = "${td.fooFile}(2:$fooCol)"
        val barPos = "${td.barFile}(4:$barCol)"
        val fooName = "${td.fooModule}foo"
        val barName = "${td.barModule}bar"

        td.testFn("$foo", "$bar", "OK")
        td.testFn("@mount('bar')\n$foo", "@mount('foo')\n$bar", "OK")

        td.testFn("\n$foo","@mount('foo')\n$bar", """ct_err:
            [$fooPos:mnt_conflict:user:[$fooName]:foo:$barKind:[$barName]:$barPos]
            [$barPos:mnt_conflict:user:[$barName]:foo:$fooKind:[$fooName]:$fooPos]
        """)

        td.testFn("@mount('bar')\n$foo", "\n$bar", """ct_err:
            [$fooPos:mnt_conflict:user:[$fooName]:bar:$barKind:[$barName]:$barPos]
            [$barPos:mnt_conflict:user:[$barName]:bar:$fooKind:[$fooName]:$fooPos]
        """)

        td.testFn("@mount('abc')\n$foo", "@mount('abc')\n$bar", """ct_err:
            [$fooPos:mnt_conflict:user:[$fooName]:abc:$barKind:[$barName]:$barPos]
            [$barPos:mnt_conflict:user:[$barName]:abc:$fooKind:[$fooName]:$fooPos]
        """)

        val foo2 = bar.replace("bar", "foo")

        if (td.fooModule == td.barModule) {
            td.testFn("\n$foo", "\n$foo2", """ct_err:
                [$fooPos:name_conflict:user:foo:$barKind:$barPos]
                [$barPos:name_conflict:user:foo:$fooKind:$fooPos]
            """)
        } else {
            td.testFn("\n$foo", "\n$foo2", """ct_err:
                [$fooPos:mnt_conflict:user:[${td.fooModule}foo]:foo:$barKind:[${td.barModule}foo]:$barPos]
                [$barPos:mnt_conflict:user:[${td.barModule}foo]:foo:$fooKind:[${td.fooModule}foo]:$fooPos]
            """)
        }
    }

    private fun chkConflictGenericOk(td: ConflictTestData, foo: String, bar: String) {
        td.testFn("$foo", "$bar", "OK")

        td.testFn("@mount('bar') $foo", "$bar", "OK")
        td.testFn("$bar", "@mount('bar') $foo", "OK")

        td.testFn("$foo", "@mount('foo') $bar", "OK")
        td.testFn("@mount('foo') $bar", "$foo", "OK")

        td.testFn("@mount('abc') $foo", "@mount('abc') $bar", "OK")
        td.testFn("@mount('abc') $bar", "@mount('abc') $foo", "OK")
    }

    private class ConflictTestData(val fooFile: String, val barFile: String, val testFn: (String, String, String) -> Unit) {
        val fooModule = fileToModule(fooFile)
        val barModule = fileToModule(barFile)
        private fun fileToModule(file: String) = if (file == "main.rell") "" else StringUtils.substringBefore(file, "/") + ":"
    }

    @Test fun testConflictAppLevel2() {
        chkMountConflict("", "123", "int[123]")

        chkMountConflict("import a.obj1;", "obj1.foo.x", "text[obj1]")
        chkMountConflict("import a.obj2;", "obj2.foo.x", "text[obj2]")
        chkMountConflict("import a.obj1; import a.op2;", "obj1.foo.x", "text[obj1]")
        chkMountConflict("import a.obj1; import a.q2;", "obj1.foo.x", "text[obj1]")

        chkMountConflictErr("import a.obj1; import a.obj2;", """ct_err:
            [a/obj1.rell:mnt_conflict:user:[a.obj1:foo]:foo:OBJECT:[a.obj2:foo]:a/obj2.rell(1:16)]
            [a/obj2.rell:mnt_conflict:user:[a.obj2:foo]:foo:OBJECT:[a.obj1:foo]:a/obj1.rell(1:16)]
        """)

        chkMountConflictErr("import a.obj1; import a.cls2;", """ct_err:
            [a/cls2.rell:mnt_conflict:user:[a.cls2:foo]:foo:OBJECT:[a.obj1:foo]:a/obj1.rell(1:16)]
            [a/obj1.rell:mnt_conflict:user:[a.obj1:foo]:foo:ENTITY:[a.cls2:foo]:a/cls2.rell(1:16)]
        """)

        chkMountConflict("import a.cls1;", "123", "int[123]")
        chkMountConflict("import a.cls1; import a.op2;", "123", "int[123]")
        chkMountConflict("import a.cls1; import a.q2;", "123", "int[123]")

        chkMountConflictErr("import a.cls1; import a.cls2;", """ct_err:
            [a/cls1.rell:mnt_conflict:user:[a.cls1:foo]:foo:ENTITY:[a.cls2:foo]:a/cls2.rell(1:16)]
            [a/cls2.rell:mnt_conflict:user:[a.cls2:foo]:foo:ENTITY:[a.cls1:foo]:a/cls1.rell(1:16)]
        """)

        chkMountConflictErr("import a.cls1; import a.obj2;", """ct_err:
            [a/cls1.rell:mnt_conflict:user:[a.cls1:foo]:foo:OBJECT:[a.obj2:foo]:a/obj2.rell(1:16)]
            [a/obj2.rell:mnt_conflict:user:[a.obj2:foo]:foo:ENTITY:[a.cls1:foo]:a/cls1.rell(1:16)]
        """)

        chkMountConflict("import a.op1;", "123", "int[123]")
        chkMountConflict("import a.op1; import a.cls2;", "123", "int[123]")
        chkMountConflict("import a.op1; import a.obj2;", "123", "int[123]")
        chkMountConflict("import a.op1; import a.q2;", "123", "int[123]")

        chkMountConflictErr("import a.op1; import a.op2;", """ct_err:
            [a/op1.rell:mnt_conflict:user:[a.op1:foo]:foo:OPERATION:[a.op2:foo]:a/op2.rell(1:19)]
            [a/op2.rell:mnt_conflict:user:[a.op2:foo]:foo:OPERATION:[a.op1:foo]:a/op1.rell(1:19)]
        """)

        chkMountConflict("import a.q1;", "123", "int[123]")
        chkMountConflict("import a.q1; import a.cls2;", "123", "int[123]")
        chkMountConflict("import a.q1; import a.obj2;", "123", "int[123]")
        chkMountConflict("import a.q1; import a.op2;", "123", "int[123]")

        chkMountConflictErr("import a.q1; import a.q2;", """ct_err:
            [a/q1.rell:mnt_conflict:user:[a.q1:foo]:foo:QUERY:[a.q2:foo]:a/q2.rell(1:15)]
            [a/q2.rell:mnt_conflict:user:[a.q2:foo]:foo:QUERY:[a.q1:foo]:a/q1.rell(1:15)]
        """)
    }

    private fun chkMountConflict(imp: String, code: String, exp: String) {
        val t = prepareMountConflict(imp)
        t.chkFull("query q() = $code;", "q", listOf(), exp)
    }

    private fun chkMountConflictErr(imp: String, exp: String) {
        val t = prepareMountConflict(imp)
        t.chkCompile("", exp)
    }

    private fun prepareMountConflict(imp: String): RellCodeTester {
        val t = RellCodeTester(tstCtx)
        t.file("a/obj1.rell", "module; object foo { x: text = 'obj1'; }")
        t.file("a/obj2.rell", "module; object foo { x: text = 'obj2'; }")
        t.file("a/cls1.rell", "module; entity foo { x: text = 'cls1'; }")
        t.file("a/cls2.rell", "module; entity foo { x: text = 'cls2'; }")
        t.file("a/op1.rell", "module; operation foo() {}")
        t.file("a/op2.rell", "module; operation foo() {}")
        t.file("a/q1.rell", "module; query foo() = 'q1';")
        t.file("a/q2.rell", "module; query foo() = 'q2';")
        t.def(imp)
        return t
    }

    @Test fun testConflictSystemTable() {
        chkConflictSystemTable("blocks")
        chkConflictSystemTable("transactions")
        chkConflictSystemTable("blockchains")
        chkConflictSystemTable("configurations")
        chkConflictSystemTable("meta")
        chkConflictSystemTable("peerinfos")

        for (table in SqlConstants.SYSTEM_OBJECTS) {
            chkConflictSystemTable(table)
        }

        chkConflictSystemTable("sys")
        chkConflictSysTable("classes")
        chkConflictSysTable("attributes")
        chkConflictSysTable("user")
        chkConflictSysTable("foo")
    }

    private fun chkConflictSystemTable(table: String) {
        chkCompile("@mount('') namespace foo { entity $table {} }", "ct_err:mnt_conflict:sys:[foo.$table]:$table")
        chkCompile("namespace foo { @mount('$table') entity user {} }", "ct_err:mnt_conflict:sys:[foo.user]:$table")
        chkCompile("@mount('') namespace foo { @external('bar') @log entity $table {} }", "ct_err:mnt_conflict:sys:[[bar]:foo.$table]:$table")
        chkCompile("@external('bar') namespace { @mount('$table') @log entity user {} }", "ct_err:mnt_conflict:sys:[[bar]:user]:$table")
        chkCompile("namespace foo { @mount('$table') object user {} }", "ct_err:mnt_conflict:sys:[foo.user]:$table")
        chkCompile("namespace foo { @mount('$table') operation user() {} }", "OK")
        chkCompile("namespace foo { @mount('$table') query user() = 0; }", "OK")
    }

    private fun chkConflictSysTable(table: String) {
        chkCompile("@mount('sys.$table') entity user {}", "ct_err:mnt_conflict:sys:[user]:sys.$table")
        chkCompile("namespace sys { entity $table {} }", "ct_err:mnt_conflict:sys:[sys.$table]:sys.$table")
        chkCompile("@external('foo') namespace { @mount('sys.$table') @log entity user {} }", "ct_err:mnt_conflict:sys:[[foo]:user]:sys.$table")
        chkCompile("@external('foo') namespace { namespace sys { @log entity $table {} } }", "ct_err:mnt_conflict:sys:[[foo]:sys.$table]:sys.$table")
    }

    @Test fun testConflictSystemQuery() {
        chkCompile("query get_rell_version() = 0;", "OK")
        chkCompile("query get_postchain_version() = 0;", "OK")
        chkCompile("query get_build() = 0;", "OK")
        chkCompile("query get_build_details() = 0;", "OK")
        chkCompile("query get_app_structure() = 0;", "OK")

        chkCompile("@mount('rell.') query get_rell_version() = '123';",
                "ct_err:mnt_conflict:sys:[get_rell_version]:rell.get_rell_version:QUERY:[rell:get_rell_version]")
        chkCompile("@mount('rell.') query get_postchain_version() = '123';",
                "ct_err:mnt_conflict:sys:[get_postchain_version]:rell.get_postchain_version:QUERY:[rell:get_postchain_version]")
        chkCompile("@mount('rell.') query get_build() = '123';",
                "ct_err:mnt_conflict:sys:[get_build]:rell.get_build:QUERY:[rell:get_build]")
        chkCompile("@mount('rell.') query get_build_details() = '123';",
                "ct_err:mnt_conflict:sys:[get_build_details]:rell.get_build_details:QUERY:[rell:get_build_details]")
    }

    @Test fun testConflictDifferentChains() {
        chkCompile("""
            namespace ns1 { @external('foo') namespace { @log entity user {} } }
            namespace ns2 { @external('bar') namespace { @log entity user {} } }
        """, "OK")

        chkCompile("namespace ns1 { @external('foo') namespace { @log entity user {} } } namespace ns2 { entity user {} }", "OK")

        chkCompile("""
            @external('foo') namespace { @mount('some') @log entity user {} }
            @external('bar') namespace { @mount('some') @log entity company {} }
        """, "OK")

        chkCompile("@external('foo') namespace { @mount('some') @log entity user {} } @mount('some') entity company {}", "OK")
    }

    @Test fun testConflictFileLevelAndModuleLevel() {
        file("foo/a.rell", "@mount('some') entity user {} @mount('some') entity company {}")
        file("foo/b.rell", "@mount('some') entity department {}")

        chkCompile("import foo;", """ct_err:
            [foo/a.rell:mnt_conflict:user:[foo:user]:some:ENTITY:[foo:company]:foo/a.rell(1:53)]
            [foo/a.rell:mnt_conflict:user:[foo:user]:some:ENTITY:[foo:department]:foo/b.rell(1:23)]
            [foo/a.rell:mnt_conflict:user:[foo:company]:some:ENTITY:[foo:user]:foo/a.rell(1:23)]
            [foo/b.rell:mnt_conflict:user:[foo:department]:some:ENTITY:[foo:user]:foo/a.rell(1:23)]
        """)
    }

    @Test fun testConflictFileLevelAndAppLevel() {
        file("foo/a.rell", "module; @mount('some') entity user {} @mount('some') entity company {}")
        file("bar/b.rell", "module; @mount('some') entity department {}")

        chkCompile("import foo.a; import bar.b;", """ct_err:
            [bar/b.rell:mnt_conflict:user:[bar.b:department]:some:ENTITY:[foo.a:user]:foo/a.rell(1:31)]
            [foo/a.rell:mnt_conflict:user:[foo.a:user]:some:ENTITY:[bar.b:department]:bar/b.rell(1:31)]
            [foo/a.rell:mnt_conflict:user:[foo.a:user]:some:ENTITY:[foo.a:company]:foo/a.rell(1:61)]
            [foo/a.rell:mnt_conflict:user:[foo.a:company]:some:ENTITY:[foo.a:user]:foo/a.rell(1:31)]
        """)
    }

    @Test fun testConflictModuleLevelAndAppLevel() {
        file("foo/a.rell", "@mount('some') entity user {}")
        file("foo/b.rell", "@mount('some') entity company {}")
        file("bar/c.rell", "@mount('some') entity department {}")

        chkCompile("import foo; import bar;", """ct_err:
            [bar/c.rell:mnt_conflict:user:[bar:department]:some:ENTITY:[foo:user]:foo/a.rell(1:23)]
            [foo/a.rell:mnt_conflict:user:[foo:user]:some:ENTITY:[bar:department]:bar/c.rell(1:23)]
            [foo/a.rell:mnt_conflict:user:[foo:user]:some:ENTITY:[foo:company]:foo/b.rell(1:23)]
            [foo/b.rell:mnt_conflict:user:[foo:company]:some:ENTITY:[foo:user]:foo/a.rell(1:23)]
        """)
    }

    @Test fun testRelativePathBaseParentModule() {
        file("a/module.rell", "@mount('x') module; object o1 { p: integer = 111; }")
        file("a/part.rell", "object o4 { p: integer = 444; }")
        file("a/b/module.rell", "@mount('.y') module; object o2 { q: integer = 222; }")
        file("a/b/c/module.rell", "@mount('.z') module; object o3 { r: integer = 333; }")
        def("import a; import a.b; import a.b.c;")
        chkDataRaw("c0.x.o1(0,111)", "c0.x.o4(0,444)", "c0.x.y.o2(0,222)", "c0.x.y.z.o3(0,333)")
    }

    @Test fun testRelativePathBaseParentModule2() {
        file("a/module.rell", "@mount('x') module; object o1 { p: integer = 111; }")
        file("a/b/module.rell", "module; object o2 { q: integer = 222; }")
        file("a/b/c/module.rell", "module; object o3 { r: integer = 333; }")
        file("a/d/module.rell", "@mount('.y') module; object o4 { p: integer = 444; }")
        file("a/d/e/module.rell", "module; object o5 { p: integer = 555; }")
        def("import a; import a.b; import a.b.c; import a.d; import a.d.e;")
        chkDataRaw("c0.x.o1(0,111)", "c0.x.o2(0,222)", "c0.x.o3(0,333)", "c0.x.y.o4(0,444)", "c0.x.y.o5(0,555)")
    }

    @Test fun testRelativePathBaseRootModule() {
        file("module.rell", "@mount('x') module; object o1 { p: integer = 111; }")
        file("a.rell", "object o2 { p: integer = 222; }")
        file("b.rell", "@mount('.y.') object o3 { p: integer = 333; }")
        file("c.rell", "module; object o4 { p: integer = 444; }")
        def("import c;")
        chkDataRaw("c0.x.o1(0,111)", "c0.x.o2(0,222)", "c0.x.o4(0,444)", "c0.x.y.o3(0,333)")
    }

    @Test fun testRelativePathBaseCurrentModule() {
        file("a/module.rell", "@mount('x') module; @mount('.y') object o { p: integer = 123; }")
        def("import a;")
        chkDataRaw("c0.x.y(0,123)")
    }

    @Test fun testRelativePathBaseNamespace() {
        def("@mount('x') namespace foo { @mount('.y') object o { p: integer = 123; } }")
        chkDataRaw("c0.x.y(0,123)")
    }

    @Test fun testRelativePathSyntaxNamespace() {
        chkRelativePathNs(".", "ct_err:ann:mount:invalid:.")

        chkRelativePathNs(".d", "c0.a.b.c.d.foo")
        chkRelativePathNs(".d.", "c0.a.b.c.d.sub.foo")

        chkRelativePathNs("^", "c0.a.b.foo")
        chkRelativePathNs("^^", "c0.a.foo")
        chkRelativePathNs("^^^", "c0.foo")
        chkRelativePathNs("^^^^", "ct_err:ann:mount:up:3:4")
        chkRelativePathNs("^.", "c0.a.b.sub.foo")

        chkRelativePathNs("^.z", "c0.a.b.z.foo")
        chkRelativePathNs("^^.y", "c0.a.y.foo")
        chkRelativePathNs("^^^.x", "c0.x.foo")
        chkRelativePathNs("^.z.", "c0.a.b.z.sub.foo")

        chkRelativePathNs("^.c", "c0.a.b.c.foo")
        chkRelativePathNs("^^.b.c", "c0.a.b.c.foo")
        chkRelativePathNs("^^^.a.b.c", "c0.a.b.c.foo")
    }

    @Test fun testRelativePathSyntaxEntity() {
        chkRelativePathEnt(".", "ct_err:ann:mount:invalid:.")

        chkRelativePathEnt(".d", "c0.a.b.c.d")
        chkRelativePathEnt(".d.", "c0.a.b.c.d.foo")

        chkRelativePathEnt("^", "ct_err:ann:mount:invalid:^:OBJECT")
        chkRelativePathEnt("^^", "ct_err:ann:mount:invalid:^^:OBJECT")
        chkRelativePathEnt("^^^", "ct_err:ann:mount:invalid:^^^:OBJECT")
        chkRelativePathEnt("^^^^", "ct_err:ann:mount:up:3:4")

        chkRelativePathEnt("^.", "c0.a.b.foo")
        chkRelativePathEnt("^^.", "c0.a.foo")
        chkRelativePathEnt("^^^.", "c0.foo")
        chkRelativePathEnt("^^^^.", "ct_err:ann:mount:up:3:4")

        chkRelativePathEnt("^.z", "c0.a.b.z")
        chkRelativePathEnt("^^.y", "c0.a.y")
        chkRelativePathEnt("^^^.x", "c0.x")
        chkRelativePathEnt("^^^^.x", "ct_err:ann:mount:up:3:4")

        chkRelativePathEnt("^.z.", "c0.a.b.z.foo")
        chkRelativePathEnt("^^^^.x.", "ct_err:ann:mount:up:3:4")

        chkRelativePathEnt("^.c", "c0.a.b.c")
        chkRelativePathEnt("^^.b.c", "c0.a.b.c")
        chkRelativePathEnt("^^^.a.b.c", "c0.a.b.c")
        chkRelativePathEnt("^.c.foo", "c0.a.b.c.foo")
    }

    private fun chkRelativePathNs(path: String, expTable: String) {
        chkRelativePath0("@mount('a.b.c') namespace top { @mount('$path') namespace sub { object foo { p: integer = 123; } } }", expTable)
    }

    private fun chkRelativePathEnt(path: String, expTable: String) {
        chkRelativePath0("@mount('a.b.c') namespace ns { @mount('$path') object foo { p: integer = 123; } }", expTable)
    }

    private fun chkRelativePath0(code: String, expected: String) {
        return chkMountName(code, expected)
    }

    @Test fun testRelativePathSyntaxMisc() {
        file("a/module.rell", "@mount('foo.') module;")
        chkCompile("import a;", "ct_err:a/module.rell:ann:mount:tail:no_name:foo.:MODULE")
        chkCompile("@mount('bar.') @external('something') namespace {}", "ct_err:ann:mount:tail:no_name:bar.:NAMESPACE")
        chkCompile("@mount('a.b.c') namespace foo { @mount(' ^^.x') query q()=123; }", "ct_err:ann:mount:invalid: ^^.x")
        chkCompile("@mount('a.b.c') namespace foo { @mount('^^ .x') query q()=123; }", "ct_err:ann:mount:invalid:^^ .x")
        chkCompile("@mount('a.b.c') namespace foo { @mount('^^.x') query q()=123; }", "OK")
    }

    @Test fun testPostchainConflict() {
        chkCompile("operation nop() {}", "ct_err:mount:conflict:sys:OPERATION:nop")
        chkCompile("operation nop(x: integer) {}", "ct_err:mount:conflict:sys:OPERATION:nop")
        chkCompile("operation nop(x: integer, y: text) {}", "ct_err:mount:conflict:sys:OPERATION:nop")
        chkCompile("operation timeb() {}", "ct_err:mount:conflict:sys:OPERATION:timeb")

        chkCompile("query last_block_info() = 123;", "ct_err:mount:conflict:sys:QUERY:last_block_info")
        chkCompile("query tx_confirmation_time() = 123;", "ct_err:mount:conflict:sys:QUERY:tx_confirmation_time")

        chkCompile("query nop() = 123;", "OK")
        chkCompile("query timeb() = 123;", "OK")
        chkCompile("operation last_block_info() {}", "OK")
        chkCompile("operation tx_confirmation_time() {}", "OK")
    }
}
