/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FunctionTest: BaseRellTest() {
    @Test fun testSimple() {
        chkFn("function f(): integer = 123;", "f()", "int[123]")
        chkFn("function f(): integer { return 123; }", "f()", "int[123]")
        chkFn("function f(x: integer): integer { return x; }", "f(123)", "int[123]")
        chkFn("function f(x: integer): integer { return x; }", "f(456)", "int[456]")
    }

    @Test fun testNoReturnValue() {
        chkFn("function f(){}", "123", "int[123]")
        chkFn("function f(): integer {}", "123", "ct_err:fun_noreturn:f")
        chkFn("function f(): integer { if (1 > 0) return 123; }", "123", "ct_err:fun_noreturn:f")
        chkFn("function f() = print('Hello');", "f()", "ct_err:query_exprtype_unit")

        chkFnEx("function f() = print('Hello');", "{ f(); return 123; }", "int[123]")
        chkOut("Hello")
    }

    @Test fun testReturnType() {
        chkFn("function f(): integer = 123;", "f()", "int[123]")
        chkFn("function f(): integer = 'Hello';", "f()", "ct_err:fn_rettype:[integer]:[text]")
        chkFn("function f(): integer { return 'Hello'; }", "f()", "ct_err:fn_rettype:[integer]:[text]")
        chkFn("function f(): integer { if (1 > 0) return 123; return 'Hello'; }", "f()", "ct_err:fn_rettype:[integer]:[text]")
        chkFn("function f(): integer { if (1 > 0) return 123; return 456; }", "f()", "int[123]")
        chkFn("function g(x: integer): integer = x + 1; function f(x: integer) = g(x);", "f(123)", "int[124]")
    }

    @Test fun testDbSelect() {
        tstCtx.useSql = true
        def("entity user { name: text; }")
        chkOp("create user('Bob'); create user('Alice');")

        chkFn("function f(name: text): user = user @ { name };", "f('Bob')", "user[1]")
        chkFn("function f(name: text): user = user @ { name };", "f('Alice')", "user[2]")
    }

    @Test fun testDbUpdate() {
        tstCtx.useSql = true
        def("entity user { name: text; mutable score: integer; }")
        chkOp("create user('Bob', 100); create user('Alice', 250);")

        val fn = "function f(name: text, s: integer): integer { update user @ { name } ( score += s ); return s; }"

        // Database modifications must fail at run-time when indirectly invoked from a query.
        chkFn(fn, "f('Bob', 500)", "rt_err:no_db_update:def")
        tst.chkData("user(1,Bob,100)", "user(2,Alice,250)")

        // When f() is called from an operation, everything must work.
        chkFnOp(fn, "f('Bob', 500);")
        tst.chkData("user(1,Bob,600)", "user(2,Alice,250)")
        chkFnOp(fn, "f('Alice', 750);")
        tst.chkData("user(1,Bob,600)", "user(2,Alice,1000)")
    }

    @Test fun testResultType() {
        chkFnEx("function f(): integer = 123;", "{ var x: text; x = f(); return 123; }", "ct_err:stmt_assign_type:[text]:[integer]")
        chkFnEx("function f() {}", "{ var x: text; x = f(); return 123; }", "ct_err:stmt_assign_type:[text]:[unit]")
        chkFnEx("function f() {}", "{ val x = f(); return 123; }", "ct_err:stmt_var_unit:x")
    }

    @Test fun testWrongArgs() {
        chkFnErr("function f(){}", "f(123)", "ct_err:expr:call:too_many_args:[f]:0:1")
        chkFnErr("function f(x:integer){}", "f()", "ct_err:expr:call:missing_args:[f]:[0:x]")
        chkFnErr("function f(x:integer){}", "f(123, 456)", "ct_err:expr:call:too_many_args:[f]:1:2")
        chkFnErr("function f(x:integer,y:text){}", "f()", "ct_err:expr:call:missing_args:[f]:[0:x,1:y]")
        chkFnErr("function f(x:integer,y:text){}", "f(123)", "ct_err:expr:call:missing_args:[f]:[1:y]")
        chkFnErr("function f(x:integer,y:text){}", "f(123,'Hello','World')", "ct_err:expr:call:too_many_args:[f]:2:3")

        chkFnErr("function f(x:integer){}", "f('Hello')", "ct_err:expr_call_argtype:[f]:0:x:integer:text")
        chkFnErr("function f(x:integer,y:text){}", "f('Hello','World')", "ct_err:expr_call_argtype:[f]:0:x:integer:text")
        chkFnErr("function f(x:integer,y:text){}", "f(123,456)", "ct_err:expr_call_argtype:[f]:1:y:text:integer")

        chkFnErr("function f(x:integer,y:text){}", "f('Hello',123)", """ct_err:
            [expr_call_argtype:[f]:0:x:integer:text]
            [expr_call_argtype:[f]:1:y:text:integer]
        """)
    }

    @Test fun testRecursion() {
        val fn = """
            function f(x: integer): integer {
                if (x == 0) {
                    return 1;
                } else {
                    return x * f(x - 1);
                }
            }
        """

        chkFn(fn, "f(0)", "int[1]")
        chkFn(fn, "f(1)", "int[1]")
        chkFn(fn, "f(2)", "int[2]")
        chkFn(fn, "f(3)", "int[6]")
        chkFn(fn, "f(4)", "int[24]")
        chkFn(fn, "f(5)", "int[120]")
        chkFn(fn, "f(6)", "int[720]")
        chkFn(fn, "f(7)", "int[5040]")
        chkFn(fn, "f(8)", "int[40320]")
        chkFn(fn, "f(9)", "int[362880]")
        chkFn(fn, "f(10)", "int[3628800]")
    }

    @Test fun testMutualRecursion() {
        val fn = """
            function foo(n: integer) {
                print('foo', n);
                if (n > 0) bar(n - 1);
            }
            function bar(n: integer) {
                print('bar', n);
                if (n > 0) foo(n - 1);
            }
        """

        chkFnEx(fn, "{ foo(5); return 0; }", "int[0]")
        chkOut("foo 5", "bar 4", "foo 3", "bar 2", "foo 1", "bar 0")
    }

    @Test fun testCallUnderAt() {
        tstCtx.useSql = true
        def("entity user { name: text; id: integer; }")
        chkOp("create user('Bob',123); create user('Alice',456);")

        val fn = "function foo(a: text): text = a.upper_case();"

        chkFnEx(fn, "= user @ { .name.upper_case() == foo('bob') };", "user[1]")
        chkFnEx(fn, "= user @ { .name.upper_case() == foo('alice') };", "user[2]")

        chkFnEx(fn, "= user @ { foo(.name) == 'BOB' };", "ct_err:expr_sqlnotallowed")
        chkFnEx(fn, "= user @ { .id == 123 } ( foo(.name) );", "text[BOB]")
    }

    @Test fun testShortBodyUnitType() {
        def("function foo(x: list<integer>) { x.add(123); }")
        def("function bar(x: list<integer>) = foo(x);")
        chkEx("{ val x = list<integer>(); foo(x); return x; }", "list<integer>[int[123]]")
        chkEx("{ val x = list<integer>(); bar(x); return x; }", "list<integer>[int[123]]")
    }

    @Test fun testInferReturnType() {
        chkFn("function f() = 123;", "f()", "int[123]")
        chkFn("function f() = 123;", "_type_of(f())", "text[integer]")
        chkFn("function f() = 'Hello';", "_type_of(f())", "text[text]")
        chkFn("function f(x: integer) = null;", "_type_of(f(0))", "text[null]")
    }

    @Test fun testInferReturnTypeComplexBody() {
        chkFn("function f() { return 123; }", "f()", "int[123]")
        chkFn("function f() { return 'foobar'; }", "f()", "text[foobar]")
        chkFn("function f() { return 123; }", "_type_of(f())", "text[integer]")
        chkFn("function f(x: integer) { return null; }", "_type_of(f(0))", "text[null]")

        chkFn("function f(x: integer) { if (x > 0) return 123; return 456; }", "_type_of(f(0))", "text[integer]")
        chkFn("function f(x: integer) { if (x > 0) return 123; return null; }", "_type_of(f(0))", "text[integer?]")
        chkFn("function f(x: integer) { if (x > 0) return null; return 123; }", "_type_of(f(0))", "text[integer?]")
        chkFn("function f(x: integer) { if (x > 0) return 123; return 'Hello'; }", "_type_of(f(0))", "ct_err:fn_rettype:[integer]:[text]")
        chkFn("function f(x: integer) { if (x > 0) return 'Hello'; return 123; }", "_type_of(f(0))", "ct_err:fn_rettype:[text]:[integer]")
        chkFn("function f(x: integer) { if (x > 0) return 123; return; }", "_type_of(f(0))", "ct_err:fn_rettype:[integer]:[unit]")
        chkFn("function f(x: integer) { if (x > 0) return; return 123; }", "0", "ct_err:fn_rettype:[unit]:[integer]")
        chkFn("function f(x: integer) { if (x > 0) return 123; }", "_type_of(f(0))", "ct_err:fun_noreturn:f")
        chkFn("function f(x: integer) { if (x > 0) return; }", "f(0)", "ct_err:query_exprtype_unit")
        chkFn("function f(x: integer) { }", "f(0)", "ct_err:query_exprtype_unit")
    }

    @Test fun testInferReturnTypeDefinitionOrder() {
        chkFn("function f() = 123; function g(): text = _type_of(f());", "g()", "text[integer]")
        chkFn("function f() = 123; function g(): integer = f();", "g()", "int[123]")
        chkFn("function f() = 123; function g() = _type_of(f());", "g()", "text[integer]")
        chkFn("function f() = 123; function g() = f();", "_type_of(g())", "text[integer]")
        chkFn("function f() = 123; function g() = f();", "g()", "int[123]")

        chkFn("function g(): text = _type_of(f()); function f() = 123;", "g()", "text[integer]")
        chkFn("function g(): integer = f(); function f() = 123;", "g()", "int[123]")
        chkFn("function g() = _type_of(f()); function f() = 123;", "g()", "text[integer]")
        chkFn("function g() = f(); function f() = 123;", "_type_of(g())", "text[integer]")
        chkFn("function g() = f(); function f() = 123;", "g()", "int[123]")
    }

    @Test fun testInferReturnTypeDefinitionOrderMultiModule() {
        file("lib1/f1.rell", "function f(x: integer) = g(x);")
        file("lib1/f2.rell", "import lib2; function g(x: integer) = lib2.h(x);")
        file("lib2.rell", "module; function h(x: integer) = x + 1;")
        file("lib3.rell", "module; import lib1; function z(x: integer) = lib1.f(x);")
        chkFn("import lib1;", "lib1.f(123)", "int[124]")
        chkFn("import lib1;", "_type_of(lib1.f(123))", "text[integer]")
        chkFn("import lib3;", "lib3.z(123)", "int[124]")
        chkFn("import lib3;", "_type_of(lib3.z(123))", "text[integer]")
    }

    @Test fun testInferReturnTypeDirectRecursion() {
        chkFn("function f(x: integer) = if (x > 0) f(x - 1) else 0;", "0", "ct_err:fn_type_recursion:FUNCTION:[f]")
        chkFn("function f(x: integer) = if (x > 0) f(x - 1) else 0;", "f(0)",
                "ct_err:[fn_type_recursion:FUNCTION:[f]][fn_type_recursion:FUNCTION:[f]]")
        chkFn("function f(x: integer) = if (x > 0) f(x - 1) + 1 else 0;", "f(3)",
                "ct_err:[fn_type_recursion:FUNCTION:[f]][fn_type_recursion:FUNCTION:[f]]")
        chkFn("function f(x: integer) { if (x > 0) return f(x - 1); return 0; }", "0",
            "ct_err:fn_type_recursion:FUNCTION:[f]")
        chkFn("function f(x: integer) { if (x > 0) return f(x - 1) + 1; return 0; }", "0",
            "ct_err:fn_type_recursion:FUNCTION:[f]")
    }

    @Test fun testInferReturnTypeIndirectRecursion() {
        chkFn("function g(x: integer) = f(x); function f(x: integer) = if (x > 0) g(x - 1) else 0;", "0",
                "ct_err:[fn_type_recursion:FUNCTION:[f]][fn_type_recursion:FUNCTION:[g]]")
        chkFn("function g(x: integer) = f(x); function f(x: integer) = if (x > 0) g(x - 1) else 0;", "_type_of(f(0))",
                "ct_err:[fn_type_recursion:FUNCTION:[f]][fn_type_recursion:FUNCTION:[g]][fn_type_recursion:FUNCTION:[f]]")
        chkFn("function f(x: integer) = if (x > 0) g(x - 1) else 0; function g(x: integer) = f(x);", "0",
                "ct_err:[fn_type_recursion:FUNCTION:[g]][fn_type_recursion:FUNCTION:[f]]")
        chkFn("function g(x: integer) = f(x); function f(x: integer) { if (x > 0) return g(x - 1); return 0; }", "0",
                "ct_err:[fn_type_recursion:FUNCTION:[f]][fn_type_recursion:FUNCTION:[g]]")
        chkFn("function f(x: integer) { if (x > 0) return g(x - 1); return 0; } function g(x: integer) = f(x);", "0",
                "ct_err:[fn_type_recursion:FUNCTION:[g]][fn_type_recursion:FUNCTION:[f]]")
    }

    @Test fun testInferReturnTypeIndirectRecursionMultiFile() {
        file("lib/f1.rell", "function f(x: integer) = g(x);")
        file("lib/f2.rell", "function g(x: integer) = h(x);")
        file("lib/f3.rell", "function h(x: integer) = f(x);")

        chkCompile("import lib;", """ct_err:
            [lib/f1.rell:fn_type_recursion:FUNCTION:[g]]
            [lib/f2.rell:fn_type_recursion:FUNCTION:[h]]
            [lib/f3.rell:fn_type_recursion:FUNCTION:[f]]
        """)
    }

    @Test fun testInferReturnTypeIndirectRecursionMultiModule() {
        file("lib1.rell", "module; import lib2; function f(x: integer) = lib2.g(x);")
        file("lib2.rell", "module; import lib3; function g(x: integer) = lib3.h(x);")
        file("lib3.rell", "module; import lib1; function h(x: integer) = lib1.f(x);")

        chkCompile("import lib1;", """ct_err:
            [lib1.rell:fn_type_recursion:FUNCTION:[g]]
            [lib2.rell:fn_type_recursion:FUNCTION:[h]]
            [lib3.rell:fn_type_recursion:FUNCTION:[f]]
        """)
    }

    @Test fun testInferReturnTypeStackOverflow() {
        // Current behavior is not perfect: last 100 functions should be compiled without errors. But difficult to
        // achieve that - to be improved later.

        val n = 1000 // experimental threshold for real stack overflow is 500
        for (x in 0 until n) def("function f_$x(x: integer) = f_${x+1}(x);")

        val res = tst.compileModule("function f_$n(x: integer) = x + 1;")

        assertTrue(res.startsWith("ct_err:" +
                "[fn_type_stackoverflow:FUNCTION:[f_1]][fn_type_stackoverflow:FUNCTION:[f_2]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_3]][fn_type_stackoverflow:FUNCTION:[f_4]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_5]][fn_type_stackoverflow:FUNCTION:[f_6]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_7]][fn_type_stackoverflow:FUNCTION:[f_8]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_9]][fn_type_stackoverflow:FUNCTION:[f_10]]"),
                res
        )

        assertTrue(res.endsWith(
                "[fn_type_stackoverflow:FUNCTION:[f_990]][fn_type_stackoverflow:FUNCTION:[f_991]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_992]][fn_type_stackoverflow:FUNCTION:[f_993]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_994]][fn_type_stackoverflow:FUNCTION:[f_995]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_996]][fn_type_stackoverflow:FUNCTION:[f_997]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_998]][fn_type_stackoverflow:FUNCTION:[f_999]]" +
                "[fn_type_stackoverflow:FUNCTION:[f_1000]]"),
                res
        )
    }

    @Test fun testReturnTypeCtError() {
        chkCompile("function f(): unknown_type { return 123; }", "ct_err:unknown_name:unknown_type")
        chkCompile("function f(): unknown_type {}", "ct_err:[fun_noreturn:f][unknown_name:unknown_type]")
        chkCompile("function f(): unknown_type { val x: integer = 'Hello'; return 123; }",
                "ct_err:[unknown_name:unknown_type][stmt_var_type:x:[integer]:[text]]")
    }

    @Test fun testNamedArguments() {
        val fn = "function f(x: integer, y: text, z: boolean) = x + ',' + y + ',' + z;"

        chkFn(fn, "f(123,'Hello',true)", "text[123,Hello,true]")
        chkFn(fn, "f(x = 123, y = 'Hello', z = true)", "text[123,Hello,true]")
        chkFn(fn, "f(z = true, y = 'Hello', x = 123)", "text[123,Hello,true]")
        chkFn(fn, "f(123, 'Hello', z = true)", "text[123,Hello,true]")
        chkFn(fn, "f(123, z = true, y = 'Hello')", "text[123,Hello,true]")
        chkFn(fn, "f(123, 'Hello', z = true)", "text[123,Hello,true]")

        chkFn(fn, "f(x = 123)", "ct_err:expr:call:missing_args:[f]:[1:y,2:z]")
        chkFn(fn, "f(y = 'Hello')", "ct_err:expr:call:missing_args:[f]:[0:x,2:z]")
        chkFn(fn, "f(z = true)", "ct_err:expr:call:missing_args:[f]:[0:x,1:y]")
        chkFn(fn, "f(x = 123, y = 'Hello')", "ct_err:expr:call:missing_args:[f]:[2:z]")
        chkFn(fn, "f(x = 123, z = true)", "ct_err:expr:call:missing_args:[f]:[1:y]")
        chkFn(fn, "f(x = 123, y = 'Hello', true)", "ct_err:expr:call:positional_after_named")
        chkFn(fn, "f(true, x = 123, y = 'Hello')",
                "ct_err:[expr:call:missing_args:[f]:[2:z]][expr_call_argtype:[f]:0:x:integer:boolean][expr:call:named_conflict:[f]:x]")

        chkFn(fn, "f(x = 'Bye', y = 'Hello', z = true)", "ct_err:expr_call_argtype:[f]:0:x:integer:text")
        chkFn(fn, "f(x = 123, y = 456, z = true)", "ct_err:expr_call_argtype:[f]:1:y:text:integer")
        chkFn(fn, "f(x = 123, y = 'Hello', z = 456)", "ct_err:expr_call_argtype:[f]:2:z:boolean:integer")

        chkFn(fn, "f(x = 123, y = 'Hello', z = 456, x = 789)",
                "ct_err:[expr_call_argtype:[f]:2:z:boolean:integer][expr:call:named_arg_dup:x]")
        chkFn(fn, "f(x = 123, y = 'Hello', z = true, x = 789)", "ct_err:expr:call:named_arg_dup:x")
    }

    @Test fun testDefaultParameters() {
        def("function f(x: integer = 123, y: text = 'Hello') = x + ',' + y;")
        def("function g(x: integer = 123, y: text, z: boolean = true) = x + ',' + y + ',' + z;")

        chk("f(456,'Bye')", "text[456,Bye]")
        chk("f(456)", "text[456,Hello]")
        chk("f()", "text[123,Hello]")
        chk("f('Bye')", "ct_err:expr_call_argtype:[f]:0:x:integer:text")
        chk("f(y='Bye')", "text[123,Bye]")

        chk("g(456,'Bye',false)", "text[456,Bye,false]")
        chk("g(456,'Bye')", "text[456,Bye,true]")
        chk("g(456)", "ct_err:expr:call:missing_args:[g]:[1:y]")
        chk("g('Bye')", "ct_err:[expr:call:missing_args:[g]:[1:y]][expr_call_argtype:[g]:0:x:integer:text]")
        chk("g(y='Bye')", "text[123,Bye,true]")
    }

    @Test fun testDefaultParametersErrors() {
        chkCompile("function f(x: integer, y: integer = x){}", "ct_err:unknown_name:x")
        chkCompile("function f(x = 123){}", "ct_err:unknown_name:x")
        chkCompile("function f(x: integer = 123){}", "OK")
        chkCompile("function f(x: integer = 'Hello'){}", "ct_err:def:param:type:x:integer:text")
    }

    @Test fun testDefaultParametersSideEffects() {
        def("function side(x: integer) { print('side:'+x); return x; }")
        def("function f(x: integer = side(123)) = x + 1;")

        chk("f()", "int[124]")
        chkOut("side:123")
        chk("f(456)", "int[457]")
        chkOut()
    }

    @Test fun testDefaultParametersTypePromotion() {
        def("function f(x: integer, y: decimal = 456) = x * y;")
        chk("f(123)", "dec[56088]")
        chk("f(123, 456)", "dec[56088]")
        chk("f(123, 789)", "dec[97047]")
        chk("f(123, 789.0)", "dec[97047]")
    }

    @Test fun testDefaultParametersNullable() {
        def("function f1(x: integer? = null) = x;")
        def("function f2(x: integer? = 123) = x;")
        def("function g1(x: decimal? = null) = x;")
        def("function g2(x: decimal? = 123) = x;")
        def("function g3(x: decimal? = 123.456) = x;")
        def("function g4(x: decimal? = _nullable_int(123)) = x;")
        def("function g5(x: decimal? = _nullable_int(null)) = x;")

        chk("f1()", "null")
        chk("f1(123)", "int[123]")
        chk("f2()", "int[123]")
        chk("f2(456)", "int[456]")

        chk("g1()", "null")
        chk("g1(456)", "dec[456]")
        chk("g2()", "dec[123]")
        chk("g2(456)", "dec[456]")
        chk("g3()", "dec[123.456]")
        chk("g3(456)", "dec[456]")
        chk("g4()", "dec[123]")
        chk("g4(456)", "dec[456]")
        chk("g5()", "null")
        chk("g5(456)", "dec[456]")
    }

    @Test fun testNullableIntegerToDecimalParameter() {
        def("function f(x: decimal?) = x;")
        chk("f(null)", "null")
        chk("f(123)", "dec[123]")
        chk("f(123.456)", "dec[123.456]")
        chk("f(_nullable_int(null))", "null")
        chk("f(_nullable_int(123))", "dec[123]")
    }

    @Test fun testNullableIntegerToDecimalResult() {
        def("function f1(x: integer): decimal = x;")
        def("function f2(x: integer): decimal { return x; }")

        def("function g1(x: integer): decimal? = x;")
        def("function g2(x: integer): decimal? { return x; }")
        def("function g3(x: integer): decimal? = null;")
        def("function g4(x: integer): decimal? { return null; }")

        def("function h1(x: integer?): decimal? = x;")
        def("function h2(x: integer?): decimal? { return x; }")
        def("function h3(x: integer?): decimal? = null;")
        def("function h4(x: integer?): decimal? { return null; }")

        chk("f1(123)", "dec[123]")
        chk("f2(123)", "dec[123]")

        chk("g1(123)", "dec[123]")
        chk("g2(123)", "dec[123]")
        chk("g3(123)", "null")
        chk("g4(123)", "null")

        chk("h1(123)", "dec[123]")
        chk("h2(123)", "dec[123]")
        chk("h1(null)", "null")
        chk("h2(null)", "null")
        chk("h3(123)", "null")
        chk("h4(123)", "null")
    }

    @Test fun testIntegerToDecimalResultImplicit() {
        def("function f(x: integer, y: decimal, z: boolean) = if (z) x else y;")

        chkCompile("function g(x: integer, y: decimal, z: boolean) { if (z) return x; else return y; }",
                "ct_err:fn_rettype:[integer]:[decimal]")

        chk("f(123, 456, true)", "dec[123]")
        chk("f(123, 456, false)", "dec[456]")
    }

    @Test fun testArgumentsEvaluationOrder() {
        initArgsEvalOrder()
        chkArgsEvalOrder("f()", "x0", "y0", "z0", "f(x0,y0,z0)")

        chkArgsEvalOrder("f(side('a'))", "a", "y0", "z0", "f(a,y0,z0)")
        chkArgsEvalOrder("f(side('a'), side('b'))", "a", "b", "z0", "f(a,b,z0)")
        chkArgsEvalOrder("f(side('a'), side('b'), side('c'))", "a", "b", "c", "f(a,b,c)")

        chkArgsEvalOrder("f(x = side('a'))", "a", "y0", "z0", "f(a,y0,z0)")
        chkArgsEvalOrder("f(y = side('b'))", "b", "x0", "z0", "f(x0,b,z0)")
        chkArgsEvalOrder("f(z = side('c'))", "c", "x0", "y0", "f(x0,y0,c)")

        chkArgsEvalOrder("f(x = side('a'), y = side('b'))", "a", "b", "z0", "f(a,b,z0)")
        chkArgsEvalOrder("f(y = side('b'), x = side('a'))", "b", "a", "z0", "f(a,b,z0)")
        chkArgsEvalOrder("f(y = side('b'), z = side('c'))", "b", "c", "x0", "f(x0,b,c)")
        chkArgsEvalOrder("f(z = side('c'), y = side('b'))", "c", "b", "x0", "f(x0,b,c)")

        chkArgsEvalOrder("f(x = side('a'), y = side('b'), z = side('c'))", "a", "b", "c", "f(a,b,c)")
        chkArgsEvalOrder("f(y = side('b'), x = side('a'), z = side('c'))", "b", "a", "c", "f(a,b,c)")
        chkArgsEvalOrder("f(z = side('c'), y = side('b'), x = side('a'))", "c", "b", "a", "f(a,b,c)")
    }

    @Test fun testArgumentsEvaluationOrderFunctionValue() {
        initArgsEvalOrder()

        chkArgsEvalOrder("f(*)()", "x0", "y0", "z0", "f(x0,y0,z0)")
        chkArgsEvalOrder("f(x = *, y = *, z = *)(side('a'), side('b'), side('c'))", "a", "b", "c", "f(a,b,c)")
        chkArgsEvalOrder("f(x = side('x'), y = side('y'), z = *)(side('c'))", "x", "y", "c", "f(x,y,c)")

        chkArgsEvalOrder("f(x = side('x'), z = *)(side('c'))", "x", "y0", "c", "f(x,y0,c)")
        chkArgsEvalOrder("f(z = *, x = side('x'))(side('c'))", "x", "y0", "c", "f(x,y0,c)")

        chkArgsEvalOrder("f(z = side('z'), x = *)(side('a'))", "z", "y0", "a", "f(a,y0,z)")
        chkArgsEvalOrder("f(x = *, z = side('z'))(side('a'))", "z", "y0", "a", "f(a,y0,z)")
    }

    private fun initArgsEvalOrder() {
        def("function side(s: text) { print(s); return s; }")

        def("""
            function f(x: text = side('x0'), y: text = side('y0'), z: text = side('z0')) {
                print('f(' + x + ',' + y + ',' + z + ')');
                return 0;
            }
        """)
    }

    private fun chkArgsEvalOrder(expr: String, vararg outs: String) {
        chk(expr, "int[0]")
        chkOut(*outs)
    }

    @Test fun testNamelessFunction() {
        chkCompile("function(x: integer) {}", "ct_err:fn:no_name")
        chkCompile("abstract function(x: integer) {}", "ct_err:[fn:abstract:non_abstract_module::function#0][fn:no_name]")
        chkCompile("override function(x: integer) {}", "ct_err:fn:no_name")
        chkCompile("@extendable function(x: integer) {}", "ct_err:fn:no_name")
        chkCompile("@extend(f) function(x: integer) {}", "ct_err:unknown_name:f")
    }

    @Test fun testBugOddNamedArgument() {
        chkCompile("""function foo(a: text, b: text) {} function bar() { foo(a="A", x="X", b="B"); }""",
            "ct_err:expr:call:unknown_named_arg:[foo]:x")
    }

    private fun chkFn(fnCode: String, callCode: String, expected: String) {
        chkFnEx(fnCode, "= $callCode ;", expected)
    }

    private fun chkFnErr(fnCode: String, callCode: String, expected: String) {
        chkFnEx(fnCode, "{ $callCode; return 0; }", expected)
    }

    private fun chkFnEx(fnCode: String, queryCode: String, expected: String) {
        val code = "$fnCode query q() $queryCode"
        tst.chkFull(code, "q", listOf(), expected)
    }

    private fun chkFnOp(fnCode: String, callCode: String, expected: String = "OK") {
        val code = "$fnCode operation o() { $callCode }"
        chkOpFull(code, expected)
    }
}
