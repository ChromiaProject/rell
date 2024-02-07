/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.RellVersions
import org.junit.Test

class QueryTest: BaseRellTest() {
    @Test fun testResultIntegerLiteral() {
        chkEx("= 12345;", "int[12345]")
    }

    @Test fun testResultStringLiteral() {
        chkEx("= 'Hello';", "text[Hello]")
    }

    @Test fun testResultParameter() {
        chkFull("query q(a: integer) = a;", listOf(Rt_IntValue.get(12345)), "int[12345]")
        chkFull("query q(a: text) = a;", listOf(Rt_TextValue.get("Hello")), "text[Hello]")
        chkFull("query q(a: integer, b: text) = a;", listOf(Rt_IntValue.get(12345), Rt_TextValue.get("Hello")),
            "int[12345]")
        chkFull("query q(a: integer, b: text) = b;", listOf(Rt_IntValue.get(12345), Rt_TextValue.get("Hello")),
            "text[Hello]")
    }

    @Test fun testReturnLiteral() {
        chkEx("{ return 12345; }", "int[12345]")
        chkEx("{ return \"Hello\"; }", "text[Hello]")
    }

    @Test fun testReturnValLiteral() {
        chkEx("{ val x = 12345; return x; }", "int[12345]")
        chkEx("{ val x = \"Hello\"; return x; }", "text[Hello]")
    }

    @Test fun testReturnSelectAllNoObjects() {
        def("entity user { name: text; }")
        insert("c0.user", "name", "11, 'Alice'")
        chkEx("= user @* { .name == 'Bob' } ;", "list<user>[]")
    }

    @Test fun testReturnSelectAllOneObject() {
        def("entity user { name: text; }")
        insert("c0.user", "name", "11,'Alice'")
        insert("c0.user", "name", "33,'Bob'")
        chkEx("= user @* { .name == \"Bob\" } ;", "list<user>[user[33]]")
    }

    @Test fun testReturnSelectAllManyObjects() {
        def("entity user { name: text; }")
        insert("c0.user", "name", "11,'Alice'")
        insert("c0.user", "name", "33,'Bob'")
        insert("c0.user", "name", "55,'James'")
        insert("c0.user", "name", "77,'Bob'")
        insert("c0.user", "name", "99,'Victor'")
        insert("c0.user", "name", "111,'Bob'")
        chkEx("= user @* { .name == 'Bob' } ;", "list<user>[user[33],user[77],user[111]]")
    }

    @Test fun testReturnErr() {
        chkEx("{ return; }", "ct_err:stmt_return_query_novalue")

        chkFull("query q(): integer = 123;", "int[123]")
        chkFull("query q(): integer = 'Hello';", "ct_err:fn_rettype:[integer]:[text]")
        chkFull("query q(): text = 123;", "ct_err:fn_rettype:[text]:[integer]")

        chkFull("query q(): integer { return 123; }", "int[123]")
        chkFull("query q(): integer { return 'Hello'; }", "ct_err:fn_rettype:[integer]:[text]")
        chkFull("query q(): text { return 123; }", "ct_err:fn_rettype:[text]:[integer]")

        chkEx("{ if (1 > 0) return 123; else return 456; }", "int[123]")
        chkEx("{ if (1 > 0) return 123; else return 'Hello'; }", "ct_err:fn_rettype:[integer]:[text]")
        chkEx("{ if (1 > 0) return 'Hello'; else return 123; }", "ct_err:fn_rettype:[text]:[integer]")
    }

    @Test fun testNoReturn() {
        chkEx("{ return 123; }", "int[123]")
        chkEx("{}", "ct_err:query_noreturn:q")
        chkEx("{ if (1 > 0) return 123; }", "ct_err:query_noreturn:q")
        chkEx("{ if (1 > 0) return 123; return 456; }", "int[123]")
        chkEx("{ if (1 > 0) {} else return 456; }", "ct_err:query_noreturn:q")
        chkEx("{ if (1 > 0) return 123; else return 456; }", "int[123]")
        chkEx("{ if (1 > 0) { return 123; } else { return 456; } }", "int[123]")
        chkEx("{ if (1 > 0) { if (2 > 3) return 100; else return 200; } else { return 456; } }", "int[200]")
        chkEx("{ while (1 < 0) return 123; }", "ct_err:query_noreturn:q")
    }

    @Test fun testWrongNumberOfArguments() {
        val code = "query q(x: integer, y: text) = x;"
        chkFull(code, listOf(Rt_IntValue.get(12345), Rt_TextValue.get("abc")), "int[12345]")
        chkFull(code, listOf(), "rt_err:fn_wrong_arg_count:q:2:0")
        chkFull(code, listOf(Rt_IntValue.get(12345)), "rt_err:fn_wrong_arg_count:q:2:1")
        chkFull(code, listOf(Rt_IntValue.get(12345), Rt_TextValue.get("abc"), Rt_BooleanValue.TRUE),
            "rt_err:fn_wrong_arg_count:q:2:3")
    }

    @Test fun testWrongArgumentType() {
        val code = "query q(x: integer) = x;"
        chkFull(code, listOf(Rt_IntValue.get(12345)), "int[12345]")
        chkFull(code, listOf(Rt_TextValue.get("Hello")), "rt_err:fn_wrong_arg_type:q:integer:text")
        chkFull(code, listOf(Rt_BooleanValue.TRUE), "rt_err:fn_wrong_arg_type:q:integer:boolean")
    }

    @Test fun testCreateUpdateDelete() {
        def("entity user { mutable name: text; }")
        chkEx("{ create user('Bob'); return 0; }", "ct_err:no_db_update:query")
        chkEx("{ update user @ {} ( name = 'Bob'); return 0; }", "ct_err:no_db_update:query")
        chkEx("{ delete user @ { .name == 'Bob' }; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ if (2 < 3) create user('Bob'); return 0; }", "ct_err:no_db_update:query")
        chkEx("{ if (2 < 3) update user @ {} ( .name = 'Bob' ); return 0; }", "ct_err:no_db_update:query")
        chkEx("{ if (2 < 3) delete user @ { .name == 'Bob' }; return 0; }", "ct_err:no_db_update:query")
    }

    @Test fun testReturnTypeExplicit() {
        tstCtx.useSql = true
        tst.chkQueryType(": integer { return null; }", "ct_err:fn_rettype:[integer]:[null]")
        tst.chkQueryType(": integer? { return null; }", "integer?")
        tst.chkQueryType(": integer? { return 123; }", "integer?")
        tst.chkQueryType(": integer { return 123; }", "integer")
        tst.chkQueryType(": integer { if (integer('0') == 0) return 123; else return null; }",
                "ct_err:fn_rettype:[integer]:[null]")
        tst.chkQueryType(": integer? { if (integer('0') == 0) return null; else return 123; }", "integer?")
        tst.chkQueryType(": integer? { if (integer('0') == 0) return 123; else return null; }", "integer?")
    }

    @Test fun testReturnTypeImplicit() {
        tstCtx.useSql = true

        tst.chkQueryType("{ return null; }", "null")
        tst.chkQueryType("{ if (integer('0') == 0) return 123; else return 456; }", "integer")
        tst.chkQueryType("{ if (integer('0') == 0) return 123; else return null; }", "integer?")
        tst.chkQueryType("{ if (integer('0') == 0) return null; else return 123; }", "integer?")

        chkEx("{ if (a == 0) return null; else return 123; }", 0, "null")
        chkEx("{ if (a == 0) return null; else return 123; }", 1, "int[123]")
        chkEx("{ if (a == 0) return 123; else return null; }", 0, "int[123]")
        chkEx("{ if (a == 0) return 123; else return null; }", 1, "null")
        chkEx("{ return null; }", "null")

        chkEx("= unit();", "ct_err:query_exprtype_unit")
        chkEx("= print('Hello');", "ct_err:query_exprtype_unit")
        chkEx("{ return unit(); }", "ct_err:stmt_return_unit")
        chkEx("{ return print('Hello'); }", "ct_err:stmt_return_unit")

        chkEx("{ if (1 > 0) return 123; else return 'Hello'; }", "ct_err:fn_rettype:[integer]:[text]")
    }

    @Test fun testGetRellVersion() {
        chkFull("", "rell.get_rell_version", listOf(), "text[${RellVersions.VERSION}]")
    }

    @Test fun testCallQuery() {
        def("query foo() = 123;")
        chk("foo()", "int[123]")
    }

    @Test fun testRecursiveTypeInference() {
        chkCompile("query foo(x: integer) = if (x <= 1) 1 else foo(x - 1);", "ct_err:fn_type_recursion:QUERY:[foo]")
        chkCompile("function foo(x: integer) = bar(x + 1); query bar(x: integer) = if (x <= 1) 1 else foo(x - 1);",
                "ct_err:[fn_type_recursion:QUERY:[bar]][fn_type_recursion:FUNCTION:[foo]]")
    }

    @Test fun testDefaultParameters() {
        def("query foo(x: integer = 123, y: text = 'Hello') = x+','+y;")
        chk("foo()", "text[123,Hello]")
        chk("foo(456)", "text[456,Hello]")
        chk("foo(456,'Bye')", "text[456,Bye]")
    }

    @Test fun testReturnTypeIntegerToDecimal() {
        chkFull("query q(x: integer): decimal = x;", 123, "dec[123]")
        chkFull("query q(x: integer): decimal { return x; }", 123, "dec[123]")

        chkFull("query q(x: integer): decimal? = x;", 123, "dec[123]")
        chkFull("query q(x: integer): decimal? { return x; }", 123, "dec[123]")
        chkFull("query q(x: integer): decimal? = null;", 123, "null")
        chkFull("query q(x: integer): decimal? { return null; }", 123, "null")

        chkFull("query q(x: integer?): decimal? = x;", 123, "dec[123]")
        chkFull("query q(x: integer?): decimal? { return x; }", 123, "dec[123]")
        chkFull("query q(x: integer?): decimal? = x;", null as Long?, "null")
        chkFull("query q(x: integer?): decimal? { return x; }", null as Long?, "null")
        chkFull("query q(x: integer?): decimal? = null;", 123, "null")
        chkFull("query q(x: integer?): decimal? { return null; }", 123, "null")
    }
}
