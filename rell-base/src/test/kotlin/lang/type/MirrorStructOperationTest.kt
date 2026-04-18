/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class MirrorStructOperationTest: BaseRellTest() {
    @Test fun testConstructor() {
        def("operation new_user(name, rating: integer) {}")

        chk("_type_of(struct<new_user>('Bob', 123))", "text[struct<new_user>]")
        chk("struct<new_user>('Bob', 123)", "struct<new_user>[name=text[Bob],rating=int[123]]")
        chk("struct<new_user>(123, 'Bob')", "struct<new_user>[name=text[Bob],rating=int[123]]")

        chk("struct<new_user>()", "ct_err:attr_missing:[struct<new_user>]:name,rating")
        chk("struct<new_user>('Bob')", "ct_err:attr_missing:[struct<new_user>]:rating")
        chk("struct<new_user>(123)", "ct_err:attr_missing:[struct<new_user>]:name")
        chk("struct<new_user>(rating = 'Bob')",
            "ct_err:[attr_missing:[struct<new_user>]:name][attr_bad_type:0:rating:integer:text]")
        chk("struct<new_user>(name = 123)",
            "ct_err:[attr_missing:[struct<new_user>]:rating][attr_bad_type:0:name:text:integer]")
    }

    @Test fun testConstructorDefaultValues() {
        def("operation new_user_1(name = 'Bob', rating: integer = 123) {}")
        def("operation new_user_2(name = 'Bob', rating: integer) {}")
        def("operation new_user_3(name, rating: integer = 123) {}")

        chk("struct<new_user_1>()", "struct<new_user_1>[name=text[Bob],rating=int[123]]")
        chk("struct<new_user_1>('Alice')", "struct<new_user_1>[name=text[Alice],rating=int[123]]")
        chk("struct<new_user_1>(456)", "struct<new_user_1>[name=text[Bob],rating=int[456]]")
        chk("struct<new_user_1>('Alice',456)", "struct<new_user_1>[name=text[Alice],rating=int[456]]")

        chk("struct<new_user_2>()", "ct_err:attr_missing:[struct<new_user_2>]:rating")
        chk("struct<new_user_2>('Alice')", "ct_err:attr_missing:[struct<new_user_2>]:rating")
        chk("struct<new_user_2>(123)", "struct<new_user_2>[name=text[Bob],rating=int[123]]")
        chk("struct<new_user_2>('Alice',123)", "struct<new_user_2>[name=text[Alice],rating=int[123]]")

        chk("struct<new_user_3>()", "ct_err:attr_missing:[struct<new_user_3>]:name")
        chk("struct<new_user_3>(456)", "ct_err:attr_missing:[struct<new_user_3>]:name")
        chk("struct<new_user_3>('Alice')", "struct<new_user_3>[name=text[Alice],rating=int[123]]")
        chk("struct<new_user_3>('Alice',456)", "struct<new_user_3>[name=text[Alice],rating=int[456]]")
    }

    @Test fun testAttributeRead() {
        def("operation new_user(name, rating: integer) {}")
        chk("struct<new_user>('Bob',123).name", "text[Bob]")
        chk("struct<new_user>('Bob',123).rating", "int[123]")
        chk("struct<new_user>('Bob',123).bad_name", "ct_err:unknown_member:[struct<new_user>]:bad_name")
    }

    @Test fun testAttributeWrite() {
        def("operation new_user(name, rating: integer) {}")
        val init = "val s = struct<new_user>('Bob',123);"
        chkEx("{ $init s.name = 'Alice'; return s; }", "ct_err:attr_not_mutable:struct<new_user>.name")
        chkEx("{ $init s.rating = 456; return s; }", "ct_err:attr_not_mutable:struct<new_user>.rating")
    }

    @Test fun testInstanceMemberFunctions() {
        def("operation new_user(name, rating: integer) {}")
        chkInstanceMemberFunctions("struct<new_user>")
        chkInstanceMemberFunctions("struct<mutable new_user>")
    }

    private fun chkInstanceMemberFunctions(type: String) {
        val expr = "$type('Bob',123)"
        chk("$expr.to_gtv()", """gtv[["Bob", 123]]""")
        chk("$expr.to_gtv_pretty()", """gtv[["name": "Bob", "rating": 123]]""")
        chk("$expr.to_bytes()", "byte_array[a50e300ca2050c03426f62a30302017b]")
        chk("$expr.bad_name()", "ct_err:unknown_member:[$type]:bad_name")
    }

    @Test fun testStaticMemberFunctions() {
        def("operation new_user(name, rating: integer) {}")
        chkStaticMemberFunctions("struct<new_user>")
        chkStaticMemberFunctions("struct<mutable new_user>")
    }

    private fun chkStaticMemberFunctions(type: String) {
        chk("""$type.from_gtv(gtv.from_json('["Bob",123]'))""", "$type[name=text[Bob],rating=int[123]]")
        chk("""$type.from_gtv_pretty(gtv.from_json('{"name":"Bob","rating":123}'))""", "$type[name=text[Bob],rating=int[123]]")
        chk("$type.from_bytes(x'a50e300ca2050c03426f62a30302017b')", "$type[name=text[Bob],rating=int[123]]")
        chk("$type.bad_name()", "ct_err:unknown_member:[$type]:bad_name")
    }

    @Test fun testCycle() {
        def("operation op(a: integer, b: struct<op>?) {}")

        chk("struct<op>()", "ct_err:attr_missing:[struct<op>]:a,b")
        chk("struct<op>(a = 123, b = null)", "struct<op>[a=int[123],b=null]")
        chk("struct<op>(a = 123, b = struct<op>(a = 456, b = null))",
            "struct<op>[a=int[123],b=struct<op>[a=int[456],b=null]]")

        chkCompile("operation op2(a: integer, b: struct<op2>) {}", "OK")
    }

    @Test fun testToTestOp() {
        tst.testLib = true
        def("operation new_user(name, rating: integer) {}")
        chk("struct<new_user>('Bob',123).to_test_op()", """op[new_user("Bob",123)]""")
        chk("struct<mutable new_user>('Bob',123).to_test_op()", """op[new_user("Bob",123)]""")
        tst.testLib = false
        chk("struct<new_user>('Bob',123).to_test_op()", "ct_err:unknown_member:[struct<new_user>]:to_test_op")
        chk("struct<mutable new_user>('Bob',123).to_test_op()", "ct_err:unknown_member:[struct<mutable new_user>]:to_test_op")
    }

    @Test fun testToGtxOperation() {
        def("operation new_user(name, rating: integer) {}")

        chk("_type_of(struct<new_user>('Bob',123).to_gtx_operation())", "text[gtx_operation]")
        chk("_type_of(struct<mutable new_user>('Bob',123).to_gtx_operation())", "text[gtx_operation]")

        val expected = """gtx_operation[name=text[new_user],args=list<gtv>[gtv["Bob"],gtv[123]]]"""
        chk("struct<new_user>('Bob',123).to_gtx_operation()", expected)
        chk("struct<mutable new_user>('Bob',123).to_gtx_operation()", expected)
    }

    @Test fun testMutableBasic() {
        def("operation new_user(name = 'Bob', rating: integer = 123) {}")

        chk("_type_of(struct<new_user>())", "text[struct<new_user>]")
        chk("_type_of(struct<mutable new_user>())", "text[struct<mutable new_user>]")

        chk("struct<mutable new_user>()", "struct<mutable new_user>[name=text[Bob],rating=int[123]]")
        chk("struct<mutable new_user>('Alice',456)", "struct<mutable new_user>[name=text[Alice],rating=int[456]]")

        chkEx("{ val s = struct<mutable new_user>(); return s; }", "struct<mutable new_user>[name=text[Bob],rating=int[123]]")
        chkEx("{ val s = struct<mutable new_user>(); s.name = 'Alice'; return s; }",
                "struct<mutable new_user>[name=text[Alice],rating=int[123]]")
        chkEx("{ val s = struct<mutable new_user>(); s.rating = 456; return s; }",
                "struct<mutable new_user>[name=text[Bob],rating=int[456]]")
    }

    @Test fun testMutableToStruct() {
        def("operation new_user(name = 'Bob', rating: integer = 123) {}")

        val op = "new_user"
        val expr = "struct<$op>()"

        chk("_type_of($expr)", "text[struct<$op>]")
        chk("_type_of($expr.to_immutable())", "ct_err:unknown_member:[struct<new_user>]:to_immutable")
        chk("_type_of($expr.to_mutable())", "text[struct<mutable $op>]")

        chk("$expr.to_mutable()", "struct<mutable $op>[name=text[Bob],rating=int[123]]")
        chk("$expr.to_mutable().to_immutable()", "struct<$op>[name=text[Bob],rating=int[123]]")
        chk("$expr.to_immutable()", "ct_err:unknown_member:[struct<new_user>]:to_immutable")
    }

    @Test fun testBugParameterNameConflict() {
        tst.ideDefIdConflictError = false
        chkCompile("operation op(x: integer, x: text) {}", "ct_err:dup_param_name:x")
    }

    @Test fun testCopyMirrorStructOperation() {
        def("operation new_user(name, rating: integer) {}")

        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(); return a === b; }",
            "boolean[false]")
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(); return b; }",
            "struct<new_user>[name=text[Alice],rating=int[100]]")

        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(name = 'Bob'); return b; }",
            "struct<new_user>[name=text[Bob],rating=int[100]]")
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(rating = 200); return b; }",
            "struct<new_user>[name=text[Alice],rating=int[200]]")
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(name = 'Bob', rating = 200); return b; }",
            "struct<new_user>[name=text[Bob],rating=int[200]]")
    }

    @Test fun testCopyMutableMirrorStructOperation() {
        def("operation new_user(name, rating: integer) {}")

        chkEx("{ val a = struct<mutable new_user>('Alice', 100); val b = a.copy(); return a === b; }",
            "boolean[false]")
        chkEx("{ val a = struct<mutable new_user>('Alice', 100); val b = a.copy(); return b; }",
            "struct<mutable new_user>[name=text[Alice],rating=int[100]]")

        chkEx("""
            {
                val a = struct<mutable new_user>('Alice', 100);
                val b = a.copy(name = 'Bob');
                a.name = 'Charlie';
                return b;
            }""",
            "struct<mutable new_user>[name=text[Bob],rating=int[100]]")
        chkEx("""
            {
                val a = struct<mutable new_user>('Alice', 100);
                val b = a.copy();
                a.rating = 999;
                return b.rating;
            }""",
            "int[100]")
    }

    @Test fun testCopyMirrorStructDefaultValues() {
        def("operation new_data(x: integer = 100, y: text = 'default') {}")

        chkEx("{ val a = struct<new_data>(); val b = a.copy(); return b; }",
            "struct<new_data>[x=int[100],y=text[default]]")
        chkEx("{ val a = struct<new_data>(x = 200); val b = a.copy(y = 'custom'); return b; }",
            "struct<new_data>[x=int[200],y=text[custom]]")
        chkEx("{ val a = struct<new_data>(y = 'hello'); val b = a.copy(x = 999); return b; }",
            "struct<new_data>[x=int[999],y=text[hello]]")
    }

    @Test fun testCopyMirrorStructOperationErrors() {
        def("operation new_user(name, rating: integer) {}")

        // Unnamed arguments
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy('Bob'); return b; }",
            "ct_err:copy:unnamed_arg")

        // Wrong type
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(name = 123); return b; }",
            """ct_err:[expr_call_argtype:[rell.struct_ext(struct<new_user>).copy]:0:name:text:integer]
               [expr_call_argtype:[copy]:name:text:integer]""")
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(rating = 'wrong'); return b; }",
            "ct_err:[expr_call_argtype:[rell.struct_ext(struct<new_user>).copy]:1:rating:integer:text][expr_call_argtype:[copy]:rating:integer:text]")

        // Unknown argument
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(age = 25); return b; }",
            """ct_err:[expr:call:unknown_named_arg:[rell.struct_ext(struct<new_user>).copy]:age]
               [expr:call:unknown_named_arg:age]""")

        // Duplicate argument
        chkEx("{ val a = struct<new_user>('Alice', 100); val b = a.copy(name = 'Bob', name = 'Charlie'); return b; }",
            "ct_err:expr:call:named_arg_dup:name")
    }
}
