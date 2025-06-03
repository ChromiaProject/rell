/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import org.junit.Test

class StackTraceTest: BaseRellTest() {
    @Test fun testRequire() {
        def("function f() { require(false, 'Fail'); }")
        def("function g() { f(); }")
        def("function h() { g(); }")
        chkEx("{ h(); return 0; }", "req_err:[Fail]")
        chkStack(":f(main.rell:1)", ":g(main.rell:2)", ":h(main.rell:3)", ":q(main.rell:4)")
    }

    @Test fun testRecursionDirect() {
        file("lib.rell", """module;
            function f(x: integer): integer {
                require(x > 0);
                return x + f(x - 1);
            }
        """)

        def("import lib;")

        chk("lib.f(0)", "req_err:null")
        chkStack("lib:f(lib.rell:3)", ":q(main.rell:2)")

        chk("lib.f(1)", "req_err:null")
        chkStack("lib:f(lib.rell:3)", "lib:f(lib.rell:4)", ":q(main.rell:2)")

        chk("lib.f(3)", "req_err:null")
        chkStack("lib:f(lib.rell:3)", "lib:f(lib.rell:4)", "lib:f(lib.rell:4)", "lib:f(lib.rell:4)", ":q(main.rell:2)")
    }

    @Test fun testRecursionIndirect() {
        file("lib.rell", """module;
            function zero(x: integer): integer = f(x / 2);
            function one(x: integer): integer = f(x / 2);
            function f(x: integer): integer {
                require(x > 0);
                return if (x % 2 == 0)
                    zero(x)
                else
                    one(x)
                ;
            }
        """)

        def("import lib;")

        chk("lib.f(0)", "req_err:null")
        chkStack("lib:f(lib.rell:5)", ":q(main.rell:2)")

        chk("lib.f(1)", "req_err:null")
        chkStack("lib:f(lib.rell:5)",
                "lib:one(lib.rell:3)", "lib:f(lib.rell:9)",
                ":q(main.rell:2)"
        )

        chk("lib.f(2)", "req_err:null")
        chkStack("lib:f(lib.rell:5)",
                "lib:one(lib.rell:3)", "lib:f(lib.rell:9)",
                "lib:zero(lib.rell:2)", "lib:f(lib.rell:7)",
                ":q(main.rell:2)"
        )

        chk("lib.f(22)", "req_err:null")
        chkStack("lib:f(lib.rell:5)",
                "lib:one(lib.rell:3)", "lib:f(lib.rell:9)",
                "lib:zero(lib.rell:2)", "lib:f(lib.rell:7)",
                "lib:one(lib.rell:3)", "lib:f(lib.rell:9)",
                "lib:one(lib.rell:3)", "lib:f(lib.rell:9)",
                "lib:zero(lib.rell:2)", "lib:f(lib.rell:7)",
                ":q(main.rell:2)"
        )
    }

    @Test fun testMultiModule() {
        file("single.rell", "module; import multi; function s() { multi.g(); }")
        file("multi/a.rell", "import single; function f() { single.s(); }")
        file("multi/b.rell", "function g() { require(false); }")

        def("import multi;")
        chkEx("{ multi.f(); return 0; }", "req_err:null")
        chkStack("multi:g(multi/b.rell:1)", "single:s(single.rell:1)", "multi:f(multi/a.rell:1)", ":q(main.rell:2)")
    }

    @Test fun testAbstractFunctions() {
        file("err.rell", "module; function err() { require(false); }")
        file("lib.rell", "abstract module; import err; abstract function g() { err.err(); }")
        file("imp.rell", "module; import lib; import err; override function lib.g() { err.err(); }")

        chkFull("import lib; query q() { lib.g(); return 0; }", "req_err:null")
        chkStack("err:err(err.rell:1)", "lib:g(lib.rell:1)", ":q(main.rell:1)")

        chkFull("import lib; import imp; query q() { lib.g(); return 0; }", "req_err:null")
        chkStack("err:err(err.rell:1)", "imp:lib.g(imp.rell:1)", ":q(main.rell:1)")
    }

    @Test fun testObjectAttr() {
        tstCtx.useSql = true
        tst.wrapInit = true

        def("function f(): integer { require(false, 'forced_crash'); return 123; }")
        def("function g(): integer = f();")
        def("object state { mutable x: integer = g(); }")

        chk("state.x", "req_err:[forced_crash]")
        chkStack(":f(main.rell:1)", ":g(main.rell:2)", ":state(main.rell:3)")
    }

    @Test fun testSpecificErrorsCollections() {
        chkSpecificError("val t = [0:33, 0:55];", 3, "rt_err:expr_map_dupkey:int[0]")
        chkSpecificError("val m = [0:33];\nval t = m[1];", 4, "rt_err:fn_map_get_novalue:int[1]")
        chkSpecificError("val m = [0:33];\nm.remove(1);", 4, "rt_err:fn:map.remove:novalue:int[1]")
        chkSpecificError("val l = [1,2,3];\nval t = l[100];", 4, "rt_err:list:index:3:100")
        chkSpecificError("val l = [1,2,3];\nval t = l.sub(100);", 4, "rt_err:fn:list.sub:args:3:100:3")
        chkSpecificError("val l = [1,2,3];\nl._set(100, 3);", 4, "rt_err:fn:list.set:index:3:100")
        chkSpecificError("val l = [1,2,3];\nl.remove_at(100);", 4, "rt_err:fn:list.remove_at:index:3:100")
        chkSpecificError("val l = [1,2,3];\nl.get(100);", 4, "rt_err:fn:list.get:index:3:100")
        chkSpecificError("val l = [1,2,3];\nl.add(100, 200);", 4, "rt_err:fn:list.add:index:3:100")
    }

    @Test fun testSpecificErrorsStrings() {
        chkSpecificError("val t = ''[5];", 3, "rt_err:expr_text_subscript_index:0:5")
        chkSpecificError("val t = 'Hello'.sub(100);", 3, "rt_err:fn:text.sub:range:5:100:5")
        chkSpecificError("val t = 'Hello'.char_at(100);", 3, "rt_err:fn:text.char_at:index:5:100")

        chkSpecificError("val t = x''[5];", 3, "rt_err:expr_bytearray_subscript_index:0:5")
        chkSpecificError("val t = x''.sub(5);", 3, "rt_err:fn:byte_array.sub:range:0:5:0")
        chkSpecificError("byte_array.from_hex('hello');", 3, "rt_err:fn:byte_array.from_hex")
    }

    @Test fun testSpecificErrorsNumbers() {
        chkSpecificError("val t = 2 / 0;", 3, "rt_err:expr:/:div0:2")
        chkSpecificError("val t = 2 % 0;", 3, "rt_err:expr:%:div0:2")
        chkSpecificError("val t = 2.0 / 0;", 3, "rt_err:expr:/:div0")
        chkSpecificError("val t = 2.0 % 0;", 3, "rt_err:expr:%:div0")

        chkSpecificError("val t = decimal('hello');", 3, "rt_err:decimal:invalid:hello")
        chkSpecificError("val t = decimal('1E+100');\nt.to_integer();", 4, "rt_err:decimal.to_integer:overflow:1E+100")

        chkSpecificError("abs(integer.MIN_VALUE);", 3, "rt_err:abs:integer:overflow:-9223372036854775808")
        chkSpecificError("(123).to_text(1000);", 3, "rt_err:fn_int_str_radix:1000")
        chkSpecificError("integer.from_text('Hello');", 3, "rt_err:fn:integer.from_text:Hello")
        chkSpecificError("integer.from_text('123', 1000);", 3, "rt_err:fn:integer.from_text:radix:1000")
        chkSpecificError("integer.from_hex('Hello');", 3, "rt_err:fn:integer.from_hex:Hello")
    }

    @Test fun testSpecificErrorsEntity() {
        tstCtx.useSql = true
        chkSpecificError("create user ('Bob');", 3, "rt_err:no_db_update:def")
        chkSpecificError("val t = user @ { 'Bob' };", 3, "rt_err:at:wrong_count:0")
    }

    @Test fun testSpecificErrorsOther() {
        chkSpecificError("val t = _nullable_int(null);\nval p = t!!;", 4, "rt_err:null_value")

        chkSpecificError("range(100, 99, 1);", 3, "rt_err:fn_range_args:100:99:1")
        chkSpecificError("json('[{(');", 3, "rt_err:fn_json_badstr")
        chkSpecificError("val t = op_context.block_height;", 3, "rt_err:op_context:noop")

        chkSpecificError("ee.value('Hello');", 3, "rt_err:enum_badname:lib:ee:Hello")
        chkSpecificError("ee.value(123);", 3, "rt_err:enum_badvalue:lib:ee:123")

        chkSpecificError("val g = gtv.from_json('[{}]');\ninteger.from_gtv(g);", 4, "gtv_err:type:[integer]:INTEGER:ARRAY")
    }

    private fun chkSpecificError(code: String, line: Int, error: String) {
        val t = RellCodeTester(tstCtx)

        t.file("lib.rell", """module;
            function f() {
                $code
            }
            enum ee { A, B }
            entity user { name; }
        """)

        t.file("mid.rell", """module;
            import lib;
            function g() {
                lib.f();
            }
        """)

        t.def("import mid;")
        t.chkFull("query q() { mid.g(); return 0; }", "q", listOf(), error)
        t.chkStack("lib:f(lib.rell:$line)", "mid:g(mid.rell:4)", ":q(main.rell:2)")
    }

    @Test fun testDefaultValueExpr() {
        tst.testLib = true
        def("function err() { require(false); return 0; }")
        def("function f(x: integer = err()) = x * x;")
        def("operation op(x: integer = err()) {}")
        def("struct rec { x: integer = err(); }")

        chk("f(123)", "int[15129]")
        chk("f()", "req_err:null")
        chkStack(":err(main.rell:1)", ":f(main.rell:2)", ":q(main.rell:5)")

        chk("op(123)", "op[op(123)]")
        chk("op()", "req_err:null")
        chkStack(":err(main.rell:1)", ":op(main.rell:3)", ":q(main.rell:5)")

        chk("rec(123)", "rec[x=int[123]]")
        chk("rec()", "req_err:null")
        chkStack(":err(main.rell:1)", ":rec(main.rell:4)", ":q(main.rell:5)")
    }

    @Test fun testFunctionType() {
        def("""function h(x: integer) {
            require(false);
            return 0;
        }""")
        def("""function g(x: integer) {
            return h(x);
        }""")
        def("""function f(x: integer, p: (integer) -> integer) {
            return p(x);
        }""")

        chk("f(123, g(*))", "req_err:null")
        chkStack(":h(main.rell:2)", ":g(main.rell:6)", ":f(main.rell:9)", ":q(main.rell:11)")
    }
}
