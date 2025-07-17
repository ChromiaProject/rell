/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class OperationTest: BaseRellTest(useSql = true) {
    @Test fun testReturn() {
        chkOp("print('Hello'); print('World');")
        chkOut("Hello", "World")

        chkOp("print('Hello'); return;")
        chkOut("Hello")

        chkOp("print('Hello'); return; print('World');", "ct_err:stmt_deadcode")
        chkOut()

        chkOp("return 123;", "ct_err:stmt_return_op_value")
    }

    @Test fun testGuard() {
        chkOp("guard {}")

        chkOp("print('Bob'); guard { print('Alice'); } print('Trudy');")
        chkOut("Bob", "Alice", "Trudy")

        chkOp("guard {} guard {}", "ct_err:stmt_guard_after_guard")
        chkOp("{ guard {} }", "ct_err:stmt_guard_nested")
        chkOp("if (2 > 1) { guard {} }", "ct_err:stmt_guard_nested")
        chkOp("if (2 > 1) guard {}", "ct_err:stmt_guard_nested")
        chkOp("while (true) { guard {} }", "ct_err:stmt_guard_nested")
        chkOp("while (true) guard {}", "ct_err:stmt_guard_nested")
    }

    @Test fun testGuardUpdate() {
        def("entity user { name: text; mutable score: integer; }")

        chkOp("guard { create user(name = 'Bob', score = 123); }", "ct_err:no_db_update:guard")
        chkOp("guard { update user @*{} ( .score += 10 ); }", "ct_err:no_db_update:guard")
        chkOp("guard { delete user @*{}; }", "ct_err:no_db_update:guard")

        chkOp("create user(name = 'Bob', score = 123); guard {}", "ct_err:no_db_update:guard")
        chkOp("update user @*{} ( .score += 10 ); guard {}", "ct_err:no_db_update:guard")
        chkOp("delete user @*{}; guard {}", "ct_err:no_db_update:guard")

        chkOp("guard {} create user(name = 'Bob', score = 123);", "OK")
        chkOp("guard {} update user @*{} ( .score += 10 );", "OK")
        chkOp("guard {} delete user @*{};", "OK")
    }

    @Test fun testGuardUpdateFunction() {
        def("entity user { name: text; mutable score: integer; }")
        def("function f() { delete user @* {}; }")

        chkOp("f();", "OK")
        chkOp("guard { f(); }", "rt_err:no_db_update:def")
        chkOp("f(); guard {}", "rt_err:no_db_update:def")
    }

    @Test fun testGuardInQueryFunction() {
        chkCompile("query q() { guard {} return 123; }", "ct_err:stmt_guard_wrong_def:QUERY")
        chkCompile("function f() { guard {} }", "ct_err:stmt_guard_wrong_def:FUNCTION")
    }

    @Test fun testCallOperation() {
        tst.testLib = true
        def("operation op(x: integer, y: text) {}")
        chk("op(123, 'Hello')", """op[op(123,"Hello")]""")
        chk("op('Hello', 123)", "ct_err:[expr_call_argtype:[op]:0:x:integer:text][expr_call_argtype:[op]:1:y:text:integer]")
        chk("op(123, 'Hello', 456)", "ct_err:expr:call:too_many_args:[op]:2:3")
        chk("op(123+456, 'Hello' + 'World')", """op[op(579,"HelloWorld")]""")
        chk("'' + op(123, 'Hello')", """text[op(123,"Hello")]""")
        chk("_type_of(op(123, 'Hello'))", "text[rell.test.op]")
    }

    @Test fun testCallOperationSideEffect() {
        tst.testLib = true
        def("operation foo(x: integer, y: text) { print('foo'); }")
        def("function f() { print('f'); return 0; }")
        chk("f()", "int[0]")
        chkOut("f")
        chk("foo(123, 'Hello')", """op[foo(123,"Hello")]""")
        chkOut()
    }

    @Test fun testCallOperationDefautParameters() {
        tst.testLib = true
        def("operation foo(x: integer = 123, y: text = 'Hello'){}")
        chk("foo()", """op[foo(123,"Hello")]""")
        chk("foo(456)", """op[foo(456,"Hello")]""")
        chk("foo(456,'Bye')", """op[foo(456,"Bye")]""")
        chk("foo('Bye')", "ct_err:expr_call_argtype:[foo]:0:x:integer:text")
        chk("foo(y = 'Bye')", """op[foo(123,"Bye")]""")
    }

    @Test fun testCallOperationNoTestLib() {
        def("operation foo(x: integer){}")
        chk("foo(123)", "ct_err:expr:operation_call:no_test:foo")
    }
}
