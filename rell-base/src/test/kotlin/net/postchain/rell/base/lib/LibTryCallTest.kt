/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibTryCallTest: BaseRellTest(false) {
    @Test fun testOneArgUnit() {
        def("function f(x: integer) { require(x > 0); }")
        chk("_type_of(try_call(f(1, *)))", "text[boolean]")
        chk("try_call(f(1, *))", "boolean[true]")
        chk("try_call(f(-1, *))", "boolean[false]")
    }

    @Test fun testOneArgValue() {
        def("function f(x: integer) { require(x > 0); return x * x; }")
        chk("_type_of(try_call(f(1, *)))", "text[integer?]")
        chk("try_call(f(5, *))", "int[25]")
        chk("try_call(f(-5, *))", "null")
    }

    @Test fun testTwoArgsUnit() {
        def("function f(x: integer) { require(x > 0); }")
        chk("try_call(f(1, *), true)", "ct_err:expr_call_badargs:[try_call]:[()->unit,boolean]")
        chk("try_call(f(1, *), 0)", "ct_err:expr_call_badargs:[try_call]:[()->unit,integer]")
        chk("try_call(f(1, *), null)", "ct_err:fn:sys:no_res_type:[try_call]")
    }

    @Test fun testTwoArgsValue() {
        def("function f(x: integer) { require(x > 0); return x * x; }")

        chk("_type_of(try_call(f(1, *), 123))", "text[integer]")
        chk("try_call(f(5, *), 123)", "int[25]")
        chk("try_call(f(-5, *), 123)", "int[123]")

        chk("_type_of(try_call(f(1, *), null))", "text[integer?]")
        chk("try_call(f(5, *), null)", "int[25]")
        chk("try_call(f(-5, *), null)", "null")

        chk("try_call(f(5, *), 123L)", "ct_err:expr_call_badargs:[try_call]:[()->integer,big_integer]")
    }

    @Test fun testTwoArgsEvaluation() {
        def("function f(x: integer) { print('f:' + x); require(x > 0); return x * x; }")
        def("function g(x: integer) { print('g:' + x); return x; }")

        chk("try_call(f(5, *), g(123))", "int[25]")
        chkOut("f:5")

        chk("try_call(f(-5, *), g(123))", "int[123]")
        chkOut("f:-5", "g:123")
    }

    @Test fun testNullableValue() {
        def("function f(x: integer): integer? { require(x > 0); return null; }")

        chk("_type_of(try_call(f(1, *)))", "text[integer?]")
        chk("try_call(f(5, *))", "null")
        chk("try_call(f(-5, *))", "null")

        chk("_type_of(try_call(f(1, *), 123))", "text[integer?]")
        chk("try_call(f(5, *), 123)", "null")
        chk("try_call(f(-5, *), 123)", "int[123]")

        chk("_type_of(try_call(f(1, *), null))", "text[integer?]")
        chk("try_call(f(5, *), null)", "null")
        chk("try_call(f(-5, *), null)", "null")
    }

    @Test fun testWrongArity() {
        def("function f(x: integer, y: integer, z: integer): integer { require(x + y + z > 0); return x * y * z; }")
        chk("try_call(f(*))", "ct_err:expr_call_badargs:[try_call]:[(integer,integer,integer)->integer]")
        chk("try_call(f(1, *))", "ct_err:expr_call_badargs:[try_call]:[(integer,integer)->integer]")
        chk("try_call(f(1, 2, *))", "ct_err:expr_call_badargs:[try_call]:[(integer)->integer]")
        chk("try_call(f(1, 2, 3, *))", "int[6]")
        chk("try_call(f(*), -5)", "ct_err:expr_call_badargs:[try_call]:[(integer,integer,integer)->integer,integer]")
        chk("try_call(f(1, *), -5)", "ct_err:expr_call_badargs:[try_call]:[(integer,integer)->integer,integer]")
        chk("try_call(f(1, 2, *), -5)", "ct_err:expr_call_badargs:[try_call]:[(integer)->integer,integer]")
        chk("try_call(f(1, 2, 3, *), -5)", "int[6]")
    }

    @Test fun testPopularFunctions() {
        chk("_type_of(try_call(decimal.from_text('hello', *)))", "text[decimal?]")
        chk("try_call(decimal.from_text('hello', *))", "null")
        chk("try_call(decimal.from_text('12345', *))", "dec[12345]")

        chk("_type_of(try_call(gtv.from_bytes(x'', *)))", "text[gtv?]")
        chk("try_call(gtv.from_bytes(x'', *))", "null")
        chk("try_call(gtv.from_bytes(x'a30302017b', *))", "gtv[123]")

        chk("_type_of(try_call(byte_array.from_hex('xyz', *)))", "text[byte_array?]")
        chk("try_call(byte_array.from_hex('xyz', *))", "null")
        chk("try_call(byte_array.from_hex('1234', *))", "byte_array[1234]")

        chk("_type_of(try_call(integer.from_gtv(gtv.from_json('123'), *)))", "text[integer?]")
        chk("try_call(integer.from_gtv(gtv.from_json('123'), *))", "int[123]")
        chk("try_call(integer.from_gtv(gtv.from_json('[]'), *))", "null")
    }

    @Test fun testAssertFunction() {
        tst.testLib = true
        def("function f(x: integer): integer { assert_true(x > 0); return x * x; }")
        chk("try_call(f(5, *))", "int[25]")
        chk("try_call(f(-5, *))", "null")
    }

    @Test fun testComplexWhat() {
        tstCtx.useSql = true
        def("function f(x: integer) { require(x > 0); return x * x; }")
        def("entity data { value: integer; }")
        insert("c0.data", "value", "1,123")

        chk("data @ {} ( try_call(f(5, *), .value) )", "int[25]")
        chk("data @ {} ( try_call(f(-5, *), .value) )", "int[123]")
        chk("data @ {} ( try_call(f(.value, *), 456) )", "int[15129]")
        chk("data @ {} ( try_call(f(-.value, *), 456) )", "int[456]")
        chk("data @ {} ( try_call(f(.value, *), -.value) )", "int[15129]")
        chk("data @ {} ( try_call(f(-.value, *), -.value) )", "int[-123]")
    }

    @Test fun testRollbackOnSqlException() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function create_duplicate(x: integer) { create data(value = 333); create data(value = x);}")
        chkOp("create data(value = 111);")
        chkOp("""
            create data(value = 222);
            try_call(create_duplicate(111, *));
            create data(value = 444);
        """.trimIndent())
        chk("data @* {} (.value)", "list<integer>[int[111],int[222],int[444]]")
    }

    @Test fun testRollbackOnCodeException() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function always_an_exception(x: integer) { create data(value = x); require(false);}")
        chkOp("create data(value = 111);")
        chkOp("""
            create data(value = 222);
            try_call(always_an_exception(333, *));
            create data(value = 444);
        """.trimIndent())
        chk("data @* {} (.value)", "list<integer>[int[111],int[222],int[444]]")
    }

    @Test fun testReadOnlyCallSqlException() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function sql_exception() { return data@{ .value / 0 == 1 }; }")
        def("query qq() { try_call(sql_exception(*)); return 0; }")
        chkOp("create data(value = 111); qq(); create data(value = 222);")
        chk("data @* {} (.value)", "list<integer>[int[111],int[222]]")
    }

    @Test fun testWriteOperationAfterFailedSqlRead() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function sql_exception() { return data@{ .value / 0 == 1 }; }")
        def("query qq() { try_call(sql_exception(*)); return 0; }")
        chkOp("qq(); create data(value = 111);")
        chk("data @* {} (.value)", "list<integer>[int[111]]")
    }

    @Test fun testReadOperationAfterFailedSqlRead() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function sql_exception() { return data@{ .value / 0 == 1 }; }")
        insert("c0.data", "value", "1,111","2,222")
        def("query qq() { try_call(sql_exception(*)); return 0; }")
        chkEx("{print(data @* {} (.value)); qq(); return data @* {} (.value); }" , "list<integer>[int[111],int[222]]")
    }

    @Test fun testReadOnlyCallCodeException() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("function code_exception() { require(false); return 1; }")
        def("query qq() { try_call(code_exception(*)); return 0; }")
        chkOp("create data(value = 111); qq(); create data(value = 222);")
        chk("data @* {} (.value)", "list<integer>[int[111],int[222]]")
    }

    @Test fun testNoDbConnection() {
        tstCtx.useSql = false
        def("entity data { key value: integer; }")
        def("function create_data(x: integer) { print('started'); create data(value = x); print('finished'); }")
        chkOp("try_call(create_data(111, *)); print('recovered');")
        chkOut("started", "recovered")
    }

    @Test fun testRecursiveCall() {
        tstCtx.useSql = true
        def("entity data { key value: integer; }")
        def("""function f(x: integer): integer {
            if (x == 0) return 0;
            create data(value = x);
            return try_call(f(x - 1, *), 1);
        }""")
        chkOp("f(5);")
        chk("data @* {} (.value).size()", "int[5]")
    }
}
