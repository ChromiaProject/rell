package net.postchain.rell.base.lib

import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibQueriesTest: BaseRellTest(false) {

    @Test fun testGetMountNamesFilterKind() {
        file("lib.rell", """module;
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
        file( "lib.rell", """module;
            @mount("admin")
            entity user {}
            @mount("city")
            object state {}
            @mount("question")
            query q() = "";
            @mount("task")
            operation op() {}
        """
        )
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
}
