/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class FunctionTypePartialApplicationTest: BaseRellTest(false) {
    @Test fun testBasic() {
        def("function square(x: integer) = x * x;")
        def("function cube(x: integer) = x * x * x;")

        def("""function sum(xs: list<integer>, f: (integer) -> integer): integer {
            var s = 0;
            for (x in xs) s += f(x);
            return s;
        }""")

        chk("123", "int[123]")
        chk("sum([1,2,3], square(*))", "int[14]")
        chk("sum([1,2,3], cube(*))", "int[36]")
    }

    @Test fun testArguments() {
        def("function f0() = 'f0';")
        def("function f1(x: integer) = 'f1:'+x;")
        def("function f2(x: integer, y: integer) = 'f2:'+x+','+y;")

        chk("f0(*)", "fn[f0()]")
        chk("f0(*)()", "text[f0]")
        chk("f0(x = *)", "ct_err:expr:call:unknown_named_arg:[f0]:x")

        chk("f1(*)", "fn[f1(*)]")
        chk("f1(*)(123)", "text[f1:123]")
        chk("f1(123)", "text[f1:123]")
        chk("f1(123, *)", "fn[f1(int[123])]")
        chk("f1(*, 123)", "ct_err:expr:call:too_many_args:[f1]:1:2")
        chk("f1(x = 123)", "text[f1:123]")
        chk("f1(x = 123, *)", "fn[f1(int[123])]")
        chk("f1(x = 123, *)()", "text[f1:123]")
        chk("f1(*, x = 123)", "ct_err:expr:call:named_conflict:[f1]:x")

        chk("f2(*)", "fn[f2(*,*)]")
        chk("f2(*)(123, 456)", "text[f2:123,456]")

        chk("f2(123, *)", "fn[f2(int[123],*)]")
        chk("f2(123, *)(456)", "text[f2:123,456]")
        chk("f2(*, 456)", "fn[f2(*,int[456])]")
        chk("f2(*, 456)(123)", "text[f2:123,456]")
        chk("f2(123, 456, *)", "fn[f2(int[123],int[456])]")
        chk("f2(123, 456, *)()", "text[f2:123,456]")

        chk("f2(x = 123, *)", "fn[f2(int[123],*)]")
        chk("f2(x = 123, *)(456)", "text[f2:123,456]")
        chk("f2(y = 456, *)", "fn[f2(*,int[456])]")
        chk("f2(y = 456, *)(123)", "text[f2:123,456]")

        chk("f2(*, x = 123)", "ct_err:expr:call:named_conflict:[f2]:x")
        chk("f2(*, y = 456)", "fn[f2(*,int[456])]")
        chk("f2(*, y = 456)(123)", "text[f2:123,456]")

        chk("f2(x = 123, y = 456, *)", "fn[f2(int[123],int[456])]")
        chk("f2(x = 123, y = 456, *)()", "text[f2:123,456]")
        chk("f2(y = 456, x = 123, *)", "fn[f2(int[123],int[456])]")
        chk("f2(y = 456, x = 123, *)()", "text[f2:123,456]")
    }

    @Test fun testArgumentsLastWildcard() {
        def("function f1(x: integer) = 'f1:'+x;")
        def("function f2(x: integer, y: integer) = 'f2:'+x+','+y;")

        chk("f1(*)", "fn[f1(*)]")
        chk("f1(*, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f1(x = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f1(*, x = *)", "ct_err:expr:call:named_conflict:[f1]:x")
        chk("f1(*, x = 123)", "ct_err:expr:call:named_conflict:[f1]:x")
        chk("f1(123, *)", "fn[f1(int[123])]")
        chk("f1(123, *)()", "text[f1:123]")

        chk("f2(*)", "fn[f2(*,*)]")
        chk("f2(*, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(123, *)", "fn[f2(int[123],*)]")
        chk("f2(123, *)(456)", "text[f2:123,456]")
        chk("f2(123, *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(123, 456, *)", "fn[f2(int[123],int[456])]")
        chk("f2(123, 456, *)()", "text[f2:123,456]")
        chk("f2(123, 456, *, *)", "ct_err:[expr:call:too_many_args:[f2]:2:3][expr:call:last_wildcard_not_alone]")
        chk("f2(*, 456)", "fn[f2(*,int[456])]")
        chk("f2(*, 456)(123)", "text[f2:123,456]")
        chk("f2(*, 456, *)", "ct_err:expr:call:last_wildcard_not_alone")

        chk("f2(x = 123, *)", "fn[f2(int[123],*)]")
        chk("f2(x = 123, *)(456)", "text[f2:123,456]")
        chk("f2(y = 456, *)", "fn[f2(*,int[456])]")
        chk("f2(y = 456, *)(123)", "text[f2:123,456]")

        chk("f2(x = *)", "fn[f2(*,*)]")
        chk("f2(x = *)(123, 456)", "text[f2:123,456]")
        chk("f2(x = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(y = *)", "fn[f2(*,*)]")
        chk("f2(y = *)(456, 123)", "text[f2:123,456]")
        chk("f2(y = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
    }

    @Test fun testArgumentsLastWildcardDefaultValue() {
        def("function f2(x: integer = 123, y: integer = 456) = 'f2:'+x+','+y;")

        chk("f2(*)", "fn[f2(int[123],int[456])]")
        chk("f2(*)()", "text[f2:123,456]")

        chk("f2(x = *)", "fn[f2(*,int[456])]")
        chk("f2(x = *)(777)", "text[f2:777,456]")
        chk("f2(y = *)", "fn[f2(int[123],*)]")
        chk("f2(y = *)(888)", "text[f2:123,888]")
        chk("f2(x = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(y = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(x = *, y = *)", "fn[f2(*,*)]")
        chk("f2(x = *, y = *)(777, 888)", "text[f2:777,888]")
        chk("f2(x = *, y = *)(777, 888)", "text[f2:777,888]")
        chk("f2(x = *, y = *, *)", "ct_err:expr:call:last_wildcard_not_alone")

        chk("f2(x = 777)", "text[f2:777,456]")
        chk("f2(x = 777, *)", "fn[f2(int[777],int[456])]")
        chk("f2(x = 777, *)()", "text[f2:777,456]")
        chk("f2(x = 777, *, *)", "ct_err:[expr:call:positional_after_named][expr:call:last_wildcard_not_alone]")

        chk("f2(y = 888)", "text[f2:123,888]")
        chk("f2(y = 888, *)", "fn[f2(int[123],int[888])]")
        chk("f2(y = 888, *)()", "text[f2:123,888]")
        chk("f2(y = 888, *, *)", "ct_err:[expr:call:positional_after_named][expr:call:last_wildcard_not_alone]")

        chk("f2(x = 777, y = 888)", "text[f2:777,888]")
        chk("f2(x = 777, y = 888, *)", "fn[f2(int[777],int[888])]")
        chk("f2(x = 777, y = 888, *)()", "text[f2:777,888]")
        chk("f2(x = 777, y = 888, *, *)", "ct_err:[expr:call:positional_after_named][expr:call:last_wildcard_not_alone]")

        chk("f2(x = *, y = 888)", "fn[f2(*,int[888])]")
        chk("f2(x = *, y = 888)(777)", "text[f2:777,888]")
        chk("f2(x = *, y = 888, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(x = 777, y = *)", "fn[f2(int[777],*)]")
        chk("f2(x = 777, y = *)(888)", "text[f2:777,888]")
        chk("f2(x = 777, y = *, *)", "ct_err:expr:call:last_wildcard_not_alone")

        chk("f2(*, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(*, y = *)", "fn[f2(*,*)]")
        chk("f2(*, y = *)(777, 888)", "text[f2:777,888]")
        chk("f2(*, y = *, *)", "ct_err:expr:call:last_wildcard_not_alone")
        chk("f2(*, x = *)", "ct_err:expr:call:named_conflict:[f2]:x")

        chk("f2(777, *)", "fn[f2(int[777],int[456])]")
        chk("f2(777, *)()", "text[f2:777,456]")
        chk("f2(777, y = *)", "fn[f2(int[777],*)]")
        chk("f2(777, y = *)(888)", "text[f2:777,888]")

        chk("f2(*, 888)", "fn[f2(*,int[888])]")
        chk("f2(*, 888)(777)", "text[f2:777,888]")
        chk("f2(*, 888, *)", "ct_err:expr:call:last_wildcard_not_alone")
    }

    @Test fun testTargetKinds() {
        tst.testLib = true
        def("function f(x: integer) = x * x;")
        def("operation op(x: integer) {}")
        def("query qq(x: integer) = x * x;")
        def("struct r { x: (integer) -> integer = f(*); }")

        chk("_type_of(f(*))", "text[(integer)->integer]")
        chk("f(*)", "fn[f(*)]")
        chk("f(*)(7)", "int[49]")

        chk("_type_of(op(*))", "text[(integer)->rell.test.op]")
        chk("op(*)", "fn[op(*)]")
        chk("op(*)(123)", "op[op(123)]")

        chk("_type_of(qq(*))", "text[(integer)->integer]")
        chk("qq(*)", "fn[qq(*)]")
        chk("qq(*)(7)", "int[49]")

        chk("_type_of(r().x(*))", "text[(integer)->integer]")
        chk("r().x(*)", "fn[f(*)]")
        chk("r().x(*)(7)", "int[49]")
    }

    @Test fun testOperation() {
        def("operation op(x: integer) {}")
        chk("op(*)", "ct_err:expr:operation_call:no_test:op")
    }

    @Test fun testStruct() {
        def("struct s { x: integer; }")
        chk("s(*)", "ct_err:[attr_missing:[s]:x][expr:call:wildcard:struct]")
        chk("s(x = *)", "ct_err:expr:call:wildcard:struct")
    }

    @Test fun testCreateEntity() {
        def("entity data { x: integer; }")
        chkOp("val f = create data(*);", "ct_err:[attr_missing:[data]:x][expr:call:wildcard:create_expr]")
        chkOp("val f = create data(x = *);", "ct_err:expr:call:wildcard:create_expr")
    }

    @Test fun testSysGlobalFunction() {
        chk("integer.from_hex(*)", "fn[integer.from_hex(*)]")
        chk("integer.from_hex(s = *)", "ct_err:expr:call:unknown_named_arg:[integer.from_hex]:s")
        chk("integer.from_hex(s = 'beef', *)", "ct_err:expr:call:unknown_named_arg:[integer.from_hex]:s")

        chkSysGlobalFn("integer.from_hex(*)", "'beef'", "(text)->integer", "fn[integer.from_hex(*)]", "int[48879]")
        chkSysGlobalFn("integer.from_hex('beef', *)", "", "()->integer",
            "fn[integer.from_hex(text[beef])]", "int[48879]")
        chkSysGlobalFn("decimal.from_text(*)", "'12.34'", "(text)->decimal",
            "fn[decimal.from_text(*)]", "dec[12.34]")
        chkSysGlobalFn("decimal.from_text('12.34', *)", "", "()->decimal",
            "fn[decimal.from_text(text[12.34])]", "dec[12.34]")

        chkSysGlobalFn("byte_array.from_list(*)", "[0xbe,0xef]", "(list<integer>)->byte_array",
            "fn[byte_array.from_list(*)]", "byte_array[beef]")
        chkSysGlobalFn("byte_array.from_hex(*)", "'beef'", "(text)->byte_array",
            "fn[byte_array.from_hex(*)]", "byte_array[beef]")

        chkSysGlobalFn("json(*)", "'[0,{},1]'", "(text)->json", "fn[json(*)]", "json[[0,{},1]]")

        chkSysGlobalFn("sha256(*)", "'abc'.to_bytes()", "(byte_array)->byte_array", "fn[crypto.sha256(*)]",
                "byte_array[ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad]")
    }

    private fun chkSysGlobalFn(expr: String, args: String, type: String, ref: String, res: String) {
        chkEx("{ val f = $expr; return _type_of(f); }", "text[$type]")
        chkEx("{ val f = $expr; return f; }", ref)
        chkEx("{ val f = $expr; return f($args); }", res)
    }

    @Test fun testSysGlobalFunctionNaming() {
        def("entity data { x: integer; }")
        def("struct s { x: integer; }")

        chk("unit(*)", "fn[unit()]")
        chk("integer.from_hex(*)", "fn[integer.from_hex(*)]")
        chk("decimal.from_text(*)", "fn[decimal.from_text(*)]")
        chk("gtv.from_bytes(*)", "fn[gtv.from_bytes(*)]")
        chkFn("= op_context.is_signer(*);", "fn[op_context.is_signer(*)]")

        chk("list<integer>.from_gtv(*)", "fn[rell.gtv_ext(list<integer>).from_gtv(*)]")
        chk("set<integer>.from_gtv(*)", "fn[rell.gtv_ext(set<integer>).from_gtv(*)]")
        chk("map<integer,text>.from_gtv(*)", "fn[rell.gtv_ext(map<integer,text>).from_gtv(*)]")

        chk("s.from_gtv(*)", "fn[rell.gtv_ext(s).from_gtv(*)]")
        chk("struct<data>.from_gtv(*)", "fn[rell.gtv_ext(struct<data>).from_gtv(*)]")
        chk("struct<mutable data>.from_gtv(*)", "fn[rell.gtv_ext(struct<mutable data>).from_gtv(*)]")
    }

    @Test fun testSysGlobalFunctionNamingTestLib() {
        tst.testLib = true

        chk("assert_true(*)", "fn[rell.test.assert_true(*)]")
        chk("assert_false(*)", "fn[rell.test.assert_false(*)]")
        chk("rell.test.assert_true(*)", "fn[rell.test.assert_true(*)]")
        chk("rell.test.assert_false(*)", "fn[rell.test.assert_false(*)]")

        chk("assert_equals(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_equals]")
        chk("assert_not_equals(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_not_equals]")
        chk("assert_null(*)", "ct_err:expr:call:partial_bad_case:[rell.test.assert_null(anything):unit]")
        chk("assert_not_null(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_not_null]")

        chk("assert_lt(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_lt]")
        chk("assert_gt(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_gt]")
        chk("assert_le(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_le]")
        chk("assert_ge(*)", "ct_err:expr:call:partial_not_supported:[rell.test.assert_ge]")
    }

    @Test fun testSysGlobalFunctionOverload() {
        chkSysFnOver("abs", "(integer)->integer", "*", "123", "int[123]")
        chkSysFnOver("abs", "(integer)->integer", "*", "-123", "int[123]")
        chkSysFnOver("abs", "(decimal)->decimal", "*", "123", "dec[123]")
        chkSysFnOver("abs", "(decimal)->decimal", "*", "-123", "dec[123]")
        chkSysFnOverErr("abs", "(decimal)->integer")
        chkSysFnOverErr("abs", "(integer)->decimal")

        chkSysFnOver("min", "(integer,integer)->integer", "*,*", "123,456", "int[123]")
        chkSysFnOver("max", "(integer,integer)->integer", "*,*", "123,456", "int[456]")
        chkSysFnOver("min", "(decimal,decimal)->decimal", "*,*", "123,456", "dec[123]")
        chkSysFnOver("max", "(decimal,decimal)->decimal", "*,*", "123,456", "dec[456]")
        chkSysFnOverErr("min", "(integer,integer)->decimal")
        chkSysFnOverErr("max", "(decimal,decimal)->integer")

        chkSysFnOver("integer", "(text)->integer", "*", "'123'", "int[123]")
        chkSysFnOver("integer", "(text,integer)->integer", "*,*", "'beef',16", "int[48879]")
        chkSysFnOver("integer", "(decimal)->integer", "*", "123", "int[123]")
        chkSysFnOverErr("integer", "(text)->decimal")

        chkSysFnOver("decimal", "(text)->decimal", "*", "'123'", "dec[123]")
        chkSysFnOver("decimal", "(integer)->decimal", "*", "123", "dec[123]")
        chkSysFnOverErr("decimal", "(text)->integer")
        chkSysFnOverErr("decimal", "(decimal)->decimal")

        chkSysFnOver("range", "(integer)->range", "*", "123", "range[0,123,1]")
        chkSysFnOver("range", "(integer,integer)->range", "*,*", "123,456", "range[123,456,1]")
        chkSysFnOver("range", "(integer,integer,integer)->range", "*,*,*", "123,456,33", "range[123,456,33]")

        chkSysFnOver("gtv.from_json", "(text)->gtv", "*", "'[]'", "gtv[[]]")
        chkSysFnOver("gtv.from_json", "(json)->gtv", "*", "json('[]')", "gtv[[]]")
        chkSysFnOverErr("gtv.from_json", "(byte_array)->gtv")
    }

    private fun chkSysFnOver(fn: String, type: String, wildArgs: String, args: String, expRes: String) {
        chk("$fn(*)", "ct_err:expr:call:partial_ambiguous:[$fn]")
        chkEx("{ val f: $type = $fn(*); return _type_of(f); }", "text[$type]")
        chkEx("{ val f: $type = $fn(*); return f; }", "fn[$fn($wildArgs)]")
        chkEx("{ val f: $type = $fn(*); return f($args); }", expRes)
    }

    private fun chkSysFnOverErr(fn: String, type: String) {
        chkEx("{ val f: $type = $fn(*); return f; }", "ct_err:expr:call:partial_ambiguous:[$fn]")
    }

    @Test fun testSysGlobalFunctionOverloadContexts() {
        def("function stub(x: decimal) = x;")
        def("function f(p: (integer) -> integer) = p;")
        def("function g(p: (decimal) -> decimal) = p;")
        def("function h(p: (decimal) -> integer) = p;")
        def("function u(): (decimal) -> decimal = abs(*);")
        def("function v(): (decimal) -> decimal { return abs(*); }")
        def("struct s1 { p: (decimal) -> decimal = abs(*); }")
        def("struct s2 { p: (decimal) -> decimal; }")
        def("struct s3 { mutable p: (decimal) -> decimal = stub(*); }")

        chk("f(abs(*))", "fn[abs(*)]")
        chk("f(abs(*))(123)", "int[123]")
        chk("g(abs(*))", "fn[abs(*)]")
        chk("g(abs(*))(123)", "dec[123]")
        chk("h(abs(*))", "ct_err:expr:call:partial_ambiguous:[abs]")

        chk("u()", "fn[abs(*)]")
        chk("u()(123)", "dec[123]")
        chk("v()", "fn[abs(*)]")
        chk("v()(123)", "dec[123]")

        chkEx("{ val p: (decimal) -> decimal = abs(*); return p; }", "fn[abs(*)]")
        chkEx("{ val p: (decimal) -> decimal = abs(*); return p(123); }", "dec[123]")
        chkEx("{ var p: (decimal) -> decimal; p = abs(*); return p; }", "fn[abs(*)]")
        chkEx("{ var p: (decimal) -> decimal; p = abs(*); return p(123); }", "dec[123]")

        chk("s1().p", "fn[abs(*)]")
        chk("s1().p(123)", "dec[123]")
        chk("s2(abs(*)).p", "fn[abs(*)]")
        chk("s2(abs(*)).p(123)", "dec[123]")

        chkEx("{ val s = s3(); s.p = abs(*); return s.p; }", "fn[abs(*)]")
        chkEx("{ val s = s3(); s.p = abs(*); return s.p(123); }", "dec[123]")

        chkEx("{ val l = list<(decimal)->decimal>(); l.add(abs(*)); return l[0]; }", "fn[abs(*)]")
        chkEx("{ val l = list<(decimal)->decimal>(); l.add(abs(*)); return l[0](123); }", "dec[123]")
        chkEx("{ val l = [stub(*)]; l[0] = abs(*); return l[0]; }", "fn[abs(*)]")
        chkEx("{ val l = [stub(*)]; l[0] = abs(*); return l[0](123); }", "dec[123]")

        chkEx("{ val m = map<integer,(decimal)->decimal>(); m[7] = abs(*); return m[7]; }", "fn[abs(*)]")
        chkEx("{ val m = map<integer,(decimal)->decimal>(); m[7] = abs(*); return m[7](123); }", "dec[123]")
        chkEx("{ val m = [7:stub(*)]; m[7] = abs(*); return m[7]; }", "fn[abs(*)]")
        chkEx("{ val m = [7:stub(*)]; m[7] = abs(*); return m[7](123); }", "dec[123]")
    }

    @Test fun testSysGlobalFunctionOptionalParams() {
        val fn = "integer.from_text"

        chkEx("{ val f = $fn(*); return _type_of(f); }", "text[(text,integer)->integer]")
        chkEx("{ val f = $fn(*); return f; }", "fn[$fn(*,*)]")
        chkEx("{ val f = $fn(*); return f('beef',16); }", "int[48879]")

        chkEx("{ val f: (text)->integer = $fn(*); return _type_of(f); }", "text[(text)->integer]")
        chkEx("{ val f: (text)->integer = $fn(*); return f; }", "fn[$fn(*)]")
        chkEx("{ val f: (text)->integer = $fn(*); return f('123'); }", "int[123]")

        chkEx("{ val f: (text,integer)->integer = $fn(*); return _type_of(f); }", "text[(text,integer)->integer]")
        chkEx("{ val f: (text,integer)->integer = $fn(*); return f; }", "fn[$fn(*,*)]")
        chkEx("{ val f: (text,integer)->integer = $fn(*); return f('beef',16); }", "int[48879]")

        chkEx("{ val f: (decimal)->integer = $fn(*); return f; }",
            "ct_err:stmt_var_type:f:[(decimal)->integer]:[(text,integer)->integer]")
    }

    @Test fun testSysGlobalFunctionDeprecated() {
        chkEx("{ val f: (list<integer>)->byte_array = byte_array(*); return f; }",
                "ct_err:deprecated:CONSTRUCTOR:[rell:byte_array]:byte_array.from_list")
    }

    @Test fun testSysGlobalFunctionSpecial() {
        def("enum colors { red, green, blue }")

        chkSysGlobalFn("colors.values(*)", "", "()->list<colors>", "fn[rell.enum_ext(colors).values()]",
                "list<colors>[colors[red],colors[green],colors[blue]]")

        chk("'hello'.format(*)", "ct_err:expr:call:partial_not_supported:[text.format]")
        chkEx("{ val f: (integer)->text = 'hello'.format('%d',*); return f; }",
                "ct_err:expr:call:partial_not_supported:[text.format]")

        chk("exists(*)", "ct_err:expr:call:partial_not_supported:[exists]")
        chk("empty(*)", "ct_err:expr:call:partial_not_supported:[empty]")

        chk("print(*)", "ct_err:expr:call:partial_not_supported:[print]")
        chk("log(*)", "ct_err:expr:call:partial_not_supported:[log]")

        chk("require(*)", "ct_err:expr:call:partial_not_supported:[require]")
        chk("require_not_empty(*)", "ct_err:expr:call:partial_not_supported:[require_not_empty]")
    }

    @Test fun testSysMemberFunctionSimple() {
        chkSysMemFn("(-123).abs(*)", "", "()->integer", "fn[integer.abs()]", "int[123]")
        chkSysMemFn("(123).to_decimal(*)", "", "()->decimal", "fn[integer.to_decimal()]", "dec[123]")
        chkSysMemFn("(123).to_hex(*)", "", "()->text", "fn[integer.to_hex()]", "text[7b]")
        chkSysMemFn("(123).sign(*)", "", "()->integer", "fn[integer.sign()]", "int[1]")

        chkSysMemFn("(-123.4).abs(*)", "", "()->decimal", "fn[decimal.abs()]", "dec[123.4]")
        chkSysMemFn("(123.4).ceil(*)", "", "()->decimal", "fn[decimal.ceil()]", "dec[124]")
        chkSysMemFn("(123.4).floor(*)", "", "()->decimal", "fn[decimal.floor()]", "dec[123]")
        chkSysMemFn("(123.4).min(*)", "456.7", "(decimal)->decimal", "fn[decimal.min(*)]", "dec[123.4]")
        chkSysMemFn("(123.4).max(*)", "456.7", "(decimal)->decimal", "fn[decimal.max(*)]", "dec[456.7]")
        chkSysMemFn("(123.4).sign(*)", "", "()->integer", "fn[decimal.sign()]", "int[1]")
        chkSysMemFn("(123.4).to_integer(*)", "", "()->integer", "fn[decimal.to_integer()]", "int[123]")

        chkSysMemFn("gtv.from_json('{}').to_bytes(*)", "", "()->byte_array",
            "fn[gtv.to_bytes()]", "byte_array[a4023000]")
        chkSysMemFn("gtv.from_json('{}').to_json(*)", "", "()->json", "fn[gtv.to_json()]", "json[{}]")

        chkSysMemFn("'hello'.empty(*)", "", "()->boolean", "fn[text.empty()]", "boolean[false]")
        chkSysMemFn("'hello'.size(*)", "", "()->integer", "fn[text.size()]", "int[5]")
        chkSysMemFn("'hello'.upper_case(*)", "", "()->text", "fn[text.upper_case()]", "text[HELLO]")
        chkSysMemFn("'hello'.contains(*)", "'hell'", "(text)->boolean", "fn[text.contains(*)]", "boolean[true]")
        chkSysMemFn("'hello'.ends_with(*)", "'lo'", "(text)->boolean", "fn[text.ends_with(*)]", "boolean[true]")
        chkSysMemFn("'hello'.replace(*)", "'l','L'", "(text,text)->text", "fn[text.replace(*,*)]", "text[heLLo]")

        chkSysMemFn("x'beef'.empty(*)", "", "()->boolean", "fn[byte_array.empty()]", "boolean[false]")
        chkSysMemFn("x'beef'.size(*)", "", "()->integer", "fn[byte_array.size()]", "int[2]")
        chkSysMemFn("x'beef'.to_hex(*)", "", "()->text", "fn[byte_array.to_hex()]", "text[beef]")

        chkSysMemFn("[1,2,3].empty(*)", "", "()->boolean", "fn[list<integer>.empty()]", "boolean[false]")
        chkSysMemFn("[1,2,3].size(*)", "", "()->integer", "fn[list<integer>.size()]", "int[3]")
        chkSysMemFn("[1,2,3].get(*)", "1", "(integer)->integer", "fn[list<integer>.get(*)]", "int[2]")
        chkSysMemFn("[1,2,3].index_of(*)", "3", "(integer)->integer", "fn[list<integer>.index_of(*)]", "int[2]")
    }

    private fun chkSysMemFn(expr: String, args: String, type: String, ref: String, res: String) {
        chkEx("{ val f = $expr; return _type_of(f); }", "text[$type]")
        chkEx("{ val f = $expr; return f; }", ref)
        chkEx("{ val f = $expr; return f($args); }", res)
    }

    @Test fun testSysMemberFunctionOverload() {
        chkSysMemFnOver("integer", "min", "(123)", "(integer)->integer", "*", "456", "int[123]")
        chkSysMemFnOver("integer", "min", "(123)", "(decimal)->decimal", "*", "456", "dec[123]")
        chkSysMemFnOver("integer", "max", "(123)", "(integer)->integer", "*", "456", "int[456]")
        chkSysMemFnOver("integer", "max", "(123)", "(decimal)->decimal", "*", "456", "dec[456]")
        chkSysMemFnOver("integer", "to_text", "(123)", "()->text", "", "", "text[123]")
        chkSysMemFnOver("integer", "to_text", "(123)", "(integer)->text", "*", "16", "text[7b]")

        chkSysMemFnOver("decimal", "round", "(123.456)", "()->decimal", "", "", "dec[123]")
        chkSysMemFnOver("decimal", "round", "(123.456)", "(integer)->decimal", "*", "1", "dec[123.5]")
        chkSysMemFnOver("decimal", "to_text", "(123.456)", "()->text", "", "", "text[123.456]")
        chkSysMemFnOver("decimal", "to_text", "(123.456)", "(boolean)->text", "*", "true", "text[123.456]")

        chkSysMemFnOver("text", "sub", "'hello'", "(integer)->text", "*", "2", "text[llo]")
        chkSysMemFnOver("text", "sub", "'hello'", "(integer,integer)->text", "*,*", "2,4", "text[ll]")

        chkSysMemFnOver("list<integer>", "sub", "[1,2,3,4,5]", "(integer)->list<integer>", "*", "2",
                "list<integer>[int[3],int[4],int[5]]")
        chkSysMemFnOver("list<integer>", "sub", "[1,2,3,4,5]", "(integer,integer)->list<integer>", "*,*", "1,4",
                "list<integer>[int[2],int[3],int[4]]")
    }

    private fun chkSysMemFnOver(
        baseType: String,
        fn: String,
        baseExpr: String,
        type: String,
        wildArgs: String,
        args: String,
        expRes: String,
    ) {
        chk("$baseExpr.$fn(*)", "ct_err:expr:call:partial_ambiguous:[$baseType.$fn]")
        chkEx("{ val f: $type = $baseExpr.$fn(*); return _type_of(f); }", "text[$type]")
        chkEx("{ val f: $type = $baseExpr.$fn(*); return f; }", "fn[$baseType.$fn($wildArgs)]")
        chkEx("{ val f: $type = $baseExpr.$fn(*); return f($args); }", expRes)
        chkFull("function f(): $type = $baseExpr.$fn(*); query q() = _type_of(f());", "text[$type]")
        chkFull("function f(): $type = $baseExpr.$fn(*); query q() = f();", "fn[$baseType.$fn($wildArgs)]")
        chkFull("function f(): $type = $baseExpr.$fn(*); query q() = f()($args);", expRes)
    }

    @Test fun testSysMemberFunctionPartial() {
        chkSysMemFn("'hello'.replace('l',*)", "'L'", "(text)->text", "fn[text.replace(text[l],*)]", "text[heLLo]")
        chkSysMemFn("'hello'.replace(*,'X')", "'l'", "(text)->text", "fn[text.replace(*,text[X])]", "text[heXXo]")
    }

    @Test fun testSysMemberFunctionStateChange() {
        chkEx("{ val l = [1,2]; val f = l.size(*); return f(); }", "int[2]")
        chkEx("{ val l = [1,2]; val f = l.size(*); l.add(3); return f(); }", "int[3]")
        chkEx("{ val l = [1,2]; val f = l.size(*); l.clear(); return f(); }", "int[0]")
        chkEx("{ var l = [1,2]; val f = l.size(*); return f(); }", "int[2]")
        chkEx("{ var l = [1,2]; val f = l.size(*); l = [1,2,3]; return f(); }", "int[2]")
    }

    @Test fun testSysMemberFunctionMirrorStruct() {
        def("entity data { x: integer; }")

        chkSysMemFn("struct<data>(123).to_mutable(*)", "", "()->struct<mutable data>", "fn[struct<data>.to_mutable()]",
                "struct<mutable data>[x=int[123]]")

        chkSysMemFn("struct<mutable data>(123).to_immutable(*)", "", "()->struct<data>", "fn[struct<mutable data>.to_immutable()]",
                "struct<data>[x=int[123]]")
    }

    @Test fun testSysMemberFunctionEntityToStruct() {
        tstCtx.useSql = true
        def("entity data { x: integer; }")
        chk("(data@{}).to_struct(*)", "ct_err:expr:call:partial_not_supported:[rell.entity_ext(data).to_struct]")
        chk("(data@{}).to_mutable_struct(*)",
            "ct_err:expr:call:partial_not_supported:[rell.entity_ext(data).to_mutable_struct]")
    }

    @Test fun testAbstractFunction() {
        file("face.rell", """
            abstract module;
            abstract function f(x: integer): integer;
            function ptr() = f(*);
        """)
        file("cube.rell", "module; import face; override function face.f(x: integer) = x * x * x;")

        def("import face; import cube;")
        chk("face.f(*)(4)", "int[64]")
        chk("face.f(*)(5)", "int[125]")
        chk("face.ptr()(4)", "int[64]")
        chk("face.ptr()(5)", "int[125]")
    }

    @Test fun testLocalVsGlobal() {
        def("function f(x: integer) = x * x;")
        def("function g(x: integer) = x * x * x;")
        chkEx("{ val g = 123; return g(*)(5); }", "int[125]")
        chkEx("{ val g = f(*); return g(*)(5); }", "int[25]")
    }

    @Test fun testFunctionType() {
        def("function f(x: integer = 111, y: integer = 222, z: integer = 333) = 'f:'+x+','+y+','+z;")

        chk("f(*)", "fn[f(int[111],int[222],int[333])]")
        chk("f(*)()", "text[f:111,222,333]")
        chk("f(*)(*)", "fn[f(int[111],int[222],int[333])]")
        chk("f(*)(*)()", "text[f:111,222,333]")
        chk("f(*)(*)(*)", "fn[f(int[111],int[222],int[333])]")
        chk("f(*)(*)(*)()", "text[f:111,222,333]")

        chk("f(x = *, y = *, z = *)", "fn[f(*,*,*)]")
        chk("f(x = *, y = *, z = *)(444, 555, 666)", "text[f:444,555,666]")
        chk("f(x = *, y = *, z = *)(*)", "fn[f(*,*,*)]")
        chk("f(x = *, y = *, z = *)(*)(444, 555, 666)", "text[f:444,555,666]")
        chk("f(x = *, y = *, z = *)(*)(*)", "fn[f(*,*,*)]")
        chk("f(x = *, y = *, z = *)(*)(*)(444, 555, 666)", "text[f:444,555,666]")

        chk("f(x = *, y = 555, z = *)", "fn[f(*,int[555],*)]")
        chk("f(x = *, y = 555, z = *)(444, 666)", "text[f:444,555,666]")
        chk("f(x = *, y = 555, z = *)(*)", "fn[f(*,int[555],*)]")
        chk("f(x = *, y = 555, z = *)(*)(444, 666)", "text[f:444,555,666]")
        chk("f(x = *, y = 555, z = *)(*)(*)", "fn[f(*,int[555],*)]")
        chk("f(x = *, y = 555, z = *)(*)(*)(444, 666)", "text[f:444,555,666]")

        chk("f(x = *, y = *, z = *)(444, *)", "fn[f(int[444],*,*)]")
        chk("f(x = *, y = *, z = *)(444, *)(555, 666)", "text[f:444,555,666]")
        chk("f(x = *, y = *, z = *)(444, *)(555, *)", "fn[f(int[444],int[555],*)]")
        chk("f(x = *, y = *, z = *)(444, *)(555, *)(666)", "text[f:444,555,666]")
        chk("f(x = *, y = *, z = *)(444, *)(555, *)(666, *)", "fn[f(int[444],int[555],int[666])]")
        chk("f(x = *, y = *, z = *)(444, *)(555, *)(666, *)()", "text[f:444,555,666]")
    }

    @Test fun testFunctionTypeComplex() {
        def("function f(a: integer, b: integer, c: integer, d: integer) = 'f:'+a+','+b+','+c+','+d;")

        chk("f(c=*,a=*,d=*,b=*)", "fn[f(*,*,*,*)]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,4)", "text[f:2,4,1,3]")

        chk("f(c=*,a=*,d=*,b=*)(1,*)", "fn[f(*,*,int[1],*)]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,*)", "fn[f(int[2],*,int[1],*)]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,*)(3,4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,*)", "fn[f(int[2],*,int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,*)(4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,4,*)()", "text[f:2,4,1,3]")

        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)", "fn[f(int[2],*,int[1],*)]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,*)", "fn[f(int[2],*,int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,*)(4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,4,*)()", "text[f:2,4,1,3]")

        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,*)", "fn[f(int[2],*,int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,*)(4)", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,4,*)()", "text[f:2,4,1,3]")

        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,*)(4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,*)(3,*)(4,*)()", "text[f:2,4,1,3]")

        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,*)(2,3,4,*)()", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,*)(3,4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,*)(3,4,*)()", "text[f:2,4,1,3]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,*)(4,*)", "fn[f(int[2],int[4],int[1],int[3])]")
        chk("f(c=*,a=*,d=*,b=*)(1,2,3,*)(4,*)()", "text[f:2,4,1,3]")
    }

    @Test fun testFunctionTypeComplex2() {
        def("function f(a: integer, b: integer, c: integer, d: integer, e: integer) = 'f:'+a+','+b+','+c+','+d+','+e;")

        chk("f(c=*,e=*,d=*,a=*,b=*)", "fn[f(*,*,*,*,*)]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,4,5)", "text[f:4,5,1,3,2]")

        chk("f(c=*,e=*,d=*,a=*,b=*)(*)(1,2,3,4,5,*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(*)(1,2,3,4,5,*)()", "text[f:4,5,1,3,2]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,*)(2,3,4,5,*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,*)(2,3,4,5,*)()", "text[f:4,5,1,3,2]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,*)(3,4,5,*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,*)(3,4,5,*)()", "text[f:4,5,1,3,2]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,*)(4,5,*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,*)(4,5,*)()", "text[f:4,5,1,3,2]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,4,*)(5,*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,4,*)(5,*)()", "text[f:4,5,1,3,2]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,4,5,*)(*)", "fn[f(int[4],int[5],int[1],int[3],int[2])]")
        chk("f(c=*,e=*,d=*,a=*,b=*)(1,2,3,4,5,*)(*)()", "text[f:4,5,1,3,2]")

        chk("f(c=*,e=1,d=*,a=*,b=2)", "fn[f(*,int[2],*,*,int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,5)", "text[f:5,2,3,4,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(*)", "fn[f(*,int[2],*,*,int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*)(3,4,5)", "text[f:5,2,3,4,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*)(3,4,5,*)", "fn[f(int[5],int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*)(3,4,5,*)()", "text[f:5,2,3,4,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(3,*)", "fn[f(*,int[2],int[3],*,int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,*)(4,5)", "text[f:5,2,3,4,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,*)(4,5,*)", "fn[f(int[5],int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,*)(4,5,*)()", "text[f:5,2,3,4,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,*)", "fn[f(*,int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,*)(5)", "text[f:5,2,3,4,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,*)(5,*)", "fn[f(int[5],int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,*)(5,*)()", "text[f:5,2,3,4,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,5,*)", "fn[f(int[5],int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,5,*)()", "text[f:5,2,3,4,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,5,*)(*)", "fn[f(int[5],int[2],int[3],int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(3,4,5,*)(*)()", "text[f:5,2,3,4,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(*,3)", "fn[f(*,int[2],*,int[3],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,3)(4,5)", "text[f:5,2,4,3,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,3,4)", "fn[f(int[4],int[2],*,int[3],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,3,4)(5)", "text[f:4,2,5,3,1]")

        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)", "fn[f(int[3],int[2],*,*,int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)(4,5)", "text[f:3,2,4,5,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)(4,*)", "fn[f(int[3],int[2],int[4],*,int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)(4,*)(5)", "text[f:3,2,4,5,1]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)(*,4)", "fn[f(int[3],int[2],*,int[4],int[1])]")
        chk("f(c=*,e=1,d=*,a=*,b=2)(*,*,3)(*,4)(5)", "text[f:3,2,5,4,1]")
    }

    @Test fun testSafeAccessSysMemberFunction() {
        def("function f(i: integer) = 'f:'+i;")
        def("function g(i: integer) = if (i % 2 == 0) null else 'g:'+i;")
        def("function si(i: integer) { print('i:'+i); return i; }")
        def("function st(t: text) { print('t:'+t); return t; }")

        chk("_type_of(f(123).replace('f',*))", "text[(text)->text]")
        chk("f(123).replace('f',*)", "fn[text.replace(text[f],*)]")
        chk("f(123).replace('f',*)('A')", "text[A:123]")

        chk("_type_of(g(123).replace('g',*))", "ct_err:expr_mem_null:text?:replace")
        chk("_type_of(g(123)?.replace('g',*))", "text[((text)->text)?]")
        chk("g(123)?.replace('g',*)", "fn[text.replace(text[g],*)]")
        chk("g(456)?.replace('g',*)", "null")

        chkOut()
        chk("g(si(123))?.replace(st('g'),*)", "fn[text.replace(text[g],*)]")
        chkOut("i:123", "t:g")

        chk("g(si(456))?.replace(st('g'),*)", "null")
        chkOut("i:456")
    }
}
