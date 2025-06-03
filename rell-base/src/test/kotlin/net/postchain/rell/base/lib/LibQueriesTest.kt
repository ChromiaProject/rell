/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibQueriesTest: BaseRellTest() {
    @Test fun testGetMountNamesFilterKind() {
        file("lib.rell", """
            module;
            entity user {}
            object state {}
            query q() = "";
            operation op() {}
        """)
        def("import lib;")

        chkMntNames("", listOf(), listOf(), """{"entities":["user"],"objects":["state"],"operations":["op"],"queries":["q"]}""")
        chkMntNames("", listOf("query"), listOf(), """{"queries":["q"]}""")
        chkMntNames("", listOf("operation"), listOf(), """{"operations":["op"]}""")
        chkMntNames("", listOf("entity"), listOf(), """{"entities":["user"]}""")
        chkMntNames("", listOf("object"), listOf(), """{"objects":["state"]}""")
        chkMntNames("", listOf("query", "operation"), listOf(), """{"operations":["op"],"queries":["q"]}""")
    }

    @Test fun testGetMountNamesFilterModule() {
        file("a.rell", """module;operation op_a() {}""")
        file("b/module.rell", """module; import .c; operation op_b() {}""")
        file("b/c.rell", """module;operation op_c() {}""")
        def("import a;import b;")

        chkMntNames("", listOf("operation"), listOf(), """{"operations":["op_a","op_b","op_c"]}""")
        chkMntNames("query my_q() = 1;", listOf(), listOf(""), """{"entities":[],"objects":[],"operations":[],"queries":["my_q"]}""")
        chkMntNames("", listOf("operation"), listOf("a"), """{"operations":["op_a"]}""")
        chkMntNames("", listOf("operation"), listOf("b"), """{"operations":["op_b"]}""")
        chkMntNames("", listOf("operation"), listOf("b.c"), """{"operations":["op_c"]}""")
    }

    @Test fun testGetMountNamesCustomMountNames() {
        file( "lib.rell", """
            module;
            @mount("admin")
            entity user {}
            @mount("city")
            object state {}
            @mount("question")
            query q() = "";
            @mount("task")
            operation op() {}
        """)
        def("import lib;")

        chkMntNames( "", listOf(), listOf(), """{"entities":["admin"],"objects":["city"],"operations":["task"],"queries":["question"]}""")
    }

    @Test fun testGetMountNamesWrongInput() {
        chkMntNames("", listOf(), listOf("wrong_module_is_ignored"), """{}""")
        chkMntNames("", listOf(), listOf("a/b"), "rt_err:rell.get_mount_names:bad_module:a/b")
        chkMntNames("", listOf("quaries"), listOf(), "rt_err:rell.get_mount_names:bad_kind:quaries")
    }

    private fun chkMntNames(code: String, kinds: List<String>, modules: List<String>, expected: String) {
        tst.strictToString = false
        chkFull(code, "rell.get_mount_names", listOf(kinds.toRtValue(), modules.toRtValue()), expected)
    }

    private fun List<String>.toRtValue() =
        Rt_ListValue(R_ListType(R_TextType), map { Rt_TextValue.get(it) }.toMutableList())

    @Test fun testGetModuleArgs() {
        file("lib_a.rell", """
            module;
            struct module_args {
                x: integer = 123;
                y: text = 'hi';
            }
        """)
        file("lib_b.rell", """
            module;
            struct module_args {
                a: integer = 456;
                b: text = 'hello';
            }
        """)
        def("import lib_a;import lib_b;")

        chkModuleArgs(listOf("lib_a"), """{"lib_a":{"x":123,"y":"hi"}}""")
        chkModuleArgs(listOf("lib_b"), """{"lib_b":{"a":456,"b":"hello"}}""")
        chkModuleArgs(listOf("lib_a", "lib_b"), """{"lib_a":{"x":123,"y":"hi"},"lib_b":{"a":456,"b":"hello"}}""")
        chkModuleArgs(listOf(), """{"lib_a":{"x":123,"y":"hi"},"lib_b":{"a":456,"b":"hello"}}""")
    }

    @Test fun testGetModuleArgsWrongInput() {
        file("lib_a.rell", """
            module;
            struct module_args {
                x: integer = 123;
                y: text = 'hi';
            }
        """)
        def("import lib_a;")

        chkModuleArgs(listOf("unknown_module_is_ignored"), "{}")
        chkModuleArgs(listOf("a/b"), "rt_err:rell.get_module_args:bad_module:a/b")
    }

    @Test fun testGetModuleArgsEmptyArgs() {
        file("lib_a.rell", "module; object state {}")
        def("import lib_a;")

        chkModuleArgs(listOf("lib_a"), "{}")
        chkModuleArgs(listOf(), "{}")
    }

    @Test fun testGetModuleArgsNonDefault() {
        file("lib_a.rell", """
            module;
            struct module_args {
                x: integer;
                y: text;
            }
        """)
        tst.moduleArgs("lib_a" to "{'x':123, y:'hi'}")
        def("import lib_a;")

        chkModuleArgs(listOf("lib_a"), """{"lib_a":{"x":123,"y":"hi"}}""")
        chkModuleArgs(listOf("lib_b"), "{}")
        chkModuleArgs(listOf(), """{"lib_a":{"x":123,"y":"hi"}}""")
    }

    private fun chkModuleArgs(modules: List<String>, expected: String) {
        tst.strictToString = false
        chkFull("", "rell.get_module_args", listOf(modules.toRtValue()), expected)
    }
}
