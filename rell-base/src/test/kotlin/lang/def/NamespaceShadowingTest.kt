/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class NamespaceShadowingTest: BaseRellTest() {
    @Test fun testFunctionCall() {
        chkCompile(expected = "OK", code = """
            function g() {}
            namespace ns {
                val g = 123;
                function f() { g(); }
            }
        """)
    }

    @Test fun testFunctionExtend() {
        chkCompile(expected = "OK", code = """
            @extendable function f() {}
            namespace ns {
                val f = 123;
                @extend(f) function g() {}
            }
        """)
    }

    @Test fun testFunctionOverride() {
        file("lib.rell", "abstract module; abstract function f();")
        chkCompile(expected = "OK", code = """
            import lib.{f};
            namespace ns {
                val f = 123;
                override function f() {}
            }
        """)
    }

    @Test fun testEntityAt() {
        chkCompile(expected = "OK", code = """
            entity user {}
            namespace ns {
                val user = 123;
                function f() { return user @* {}; }
            }
        """)

        chkCompile(expected = "ct_err:expr_novalue:struct:[ns.user]", code = """
            entity user {}
            namespace ns {
                struct user {}
                function f() { return user @* {}; }
            }
        """)
    }

    @Test fun testEntityAtNamespace() {
        chkCompile(expected = "OK", code = """
            namespace foo { entity user {} }
            namespace ns {
                val foo = 123;
                function f() { return foo.user @* {}; }
            }
        """)
    }

    @Test fun testEntityCreate() {
        chkCompile(expected = "OK", code = """
            entity user {}
            namespace ns {
                val user = 123;
                function f() { create user(); }
            }
        """)

        chkCompile(expected = "ct_err:wrong_name:entity:struct:user", code = """
            entity user {}
            namespace ns {
                struct user {}
                function f() { create user(); }
            }
        """)
    }

    @Test fun testEntityUpdate() {
        chkCompile(expected = "OK", code = """
            entity user { mutable x: integer; }
            namespace ns {
                val user = 123;
                function f() { update user @* {} ( x = 0 ); }
            }
        """)

        chkCompile(expected = "ct_err:wrong_name:entity:struct:user", code = """
            entity user { mutable x: integer; }
            namespace ns {
                struct user {}
                function f() { update user @* {} ( x = 0 ); }
            }
        """)
    }

    @Test fun testValueRead() {
        chkFull(expected = "int[123]", code = """
            val g = 123;
            namespace ns {
                function g() {}
                function f() { return g; }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testValueMember() {
        chkCompile(expected = "OK", code = """
            val g = 123;
            namespace ns {
                function g() {}
                function f() { g.to_gtv(); }
            }
        """)
    }

    @Test fun testTypeExplicit() {
        chkCompile(expected = "OK", code = """
            struct s {}
            namespace ns {
                val s = 123;
                function f(x: s) {}
            }
        """)
    }

    @Test fun testTypeImplicit() {
        chkCompile(expected = "OK", code = """
            struct s {}
            namespace ns {
                val s = 123;
                function f(s) {}
            }
        """)
    }

    @Test fun testMirrorEntity() {
        chkFull(expected = "struct<user>[]", code = """
            entity user {}
            namespace ns {
                val user = 123;
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorObject() {
        chkFull(expected = "struct<user>[]", code = """
            object user {}
            namespace ns {
                val user = 123;
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorOperation() {
        chkFull(expected = "struct<user>[]", code = """
            operation user() {}
            namespace ns {
                val user = 123;
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorObjectVsEntity() {
        chkFull(expected = "struct<ns.user>[]", code = """
            entity user {}
            namespace ns {
                object user {}
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorOperationVsEntity() {
        chkFull(expected = "struct<ns.user>[]", code = """
            entity user {}
            namespace ns {
                operation user() {}
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorOperationVsObject() {
        chkFull(expected = "struct<ns.user>[]", code = """
            object user {}
            namespace ns {
                operation user() {}
                function f() { return struct<user>(); }
            }
            query q() = ns.f();
        """)
    }

    @Test fun testMirrorWildcardImportEntity() {
        initMirrorWildcardImport()
        chkFull("import lib_entity.*; import lib_object.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[ENTITY:[lib_entity:foo],OBJECT:[lib_object:foo]]")
        chkFull("import lib_entity.*; import lib_operation.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[ENTITY:[lib_entity:foo],OPERATION:[lib_operation:foo]]")
        chkFull("import lib_entity.*; import lib_struct.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[ENTITY:[lib_entity:foo],STRUCT:[lib_struct:foo]]")
        chkFull("import lib_entity.*; import lib_enum.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[ENTITY:[lib_entity:foo],ENUM:[lib_enum:foo]]")
        chkFull("import lib_entity.*; import lib_function.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[ENTITY:[lib_entity:foo],FUNCTION:[lib_function:foo]]")
        chkFull("import lib_entity.*; import lib_const.*; query q() = struct<foo>();", "struct<lib_entity:foo>[]")
    }

    @Test fun testMirrorWildcardImportObject() {
        initMirrorWildcardImport()
        chkFull("import lib_object.*; import lib_entity.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OBJECT:[lib_object:foo],ENTITY:[lib_entity:foo]]")
        chkFull("import lib_object.*; import lib_operation.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OBJECT:[lib_object:foo],OPERATION:[lib_operation:foo]]")
        chkFull("import lib_object.*; import lib_struct.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OBJECT:[lib_object:foo],STRUCT:[lib_struct:foo]]")
        chkFull("import lib_object.*; import lib_enum.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OBJECT:[lib_object:foo],ENUM:[lib_enum:foo]]")
        chkFull("import lib_object.*; import lib_function.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OBJECT:[lib_object:foo],FUNCTION:[lib_function:foo]]")
        chkFull("import lib_object.*; import lib_const.*; query q() = struct<foo>();", "struct<lib_object:foo>[]")
    }

    @Test fun testMirrorWildcardImportOperation() {
        initMirrorWildcardImport()
        chkFull("import lib_operation.*; import lib_entity.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OPERATION:[lib_operation:foo],ENTITY:[lib_entity:foo]]")
        chkFull("import lib_operation.*; import lib_object.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OPERATION:[lib_operation:foo],OBJECT:[lib_object:foo]]")
        chkFull("import lib_operation.*; import lib_struct.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OPERATION:[lib_operation:foo],STRUCT:[lib_struct:foo]]")
        chkFull("import lib_operation.*; import lib_enum.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OPERATION:[lib_operation:foo],ENUM:[lib_enum:foo]]")
        chkFull("import lib_operation.*; import lib_function.*; query q() = struct<foo>();",
            "ct_err:namespace:ambig:foo:[OPERATION:[lib_operation:foo],FUNCTION:[lib_function:foo]]")
        chkFull("import lib_operation.*; import lib_const.*; query q() = struct<foo>();", "struct<lib_operation:foo>[]")
    }

    private fun initMirrorWildcardImport() {
        file("lib_entity.rell", "module; @mount('foo1') entity foo {}")
        file("lib_object.rell", "module; @mount('foo2') object foo {}")
        file("lib_operation.rell", "module; @mount('foo3') operation foo() {}")
        file("lib_struct.rell", "module; struct foo {}")
        file("lib_enum.rell", "module; enum foo {}")
        file("lib_function.rell", "module; function foo() {}")
        file("lib_const.rell", "module; val foo = 123;")
    }

    @Test fun testGenericTypeArg() {
        chkCompile(expected = "OK", code = """
            entity user {}
            namespace ns {
                val user = 123;
                function f(x: list<user>) {}
            }
        """)

        chkCompile(expected = "OK", code = """
            struct user {}
            namespace ns {
                function user() = 'Bob';
                function f(x: list<user>) {}
            }
        """)

        chkCompile(expected = "OK", code = """
            enum user {}
            namespace ns {
                object user {}
                function f(x: list<user>) {}
            }
        """)
    }

    @Test fun testGenericTypeName() {
        chkCompile("namespace ns { val list = 123; function f(x: list<text>) {} }", "OK")
        chkCompile("namespace ns { function list() = 'Bob'; function f(x: list<text>) {} }", "OK")
        chkCompile("namespace ns { operation list() {} function f(x: list<text>) {} }", "OK")
        chkCompile("namespace ns { object list {} function f(x: list<text>) {} }", "OK")
        chkCompile("namespace ns { struct list {} function f(x: list<text>) {} }", "ct_err:type:not_generic:ns.list")
        chkCompile("namespace ns { entity list {} function f(x: list<text>) {} }", "ct_err:type:not_generic:ns.list")
        chkCompile("namespace ns { enum list {} function f(x: list<text>) {} }", "ct_err:type:not_generic:ns.list")
    }
}
