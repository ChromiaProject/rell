/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class FunctionTypeTest: BaseRellTest() {
    @Test fun testType() {
        chkEx("{ var f: (integer) -> text; return _type_of(f); }", "text[(integer)->text]")
        chkEx("{ var f: (integer) -> (decimal) -> text; return _type_of(f); }", "text[(integer)->(decimal)->text]")
        chkEx("{ var f: (integer) -> ((decimal) -> text); return _type_of(f); }", "text[(integer)->(decimal)->text]")
        chkEx("{ var f: ((integer) -> (decimal)) -> text; return _type_of(f); }", "text[((integer)->decimal)->text]")
    }

    @Test fun testNullableType() {
        def("function f(x: integer): text = 'f:' + x;")
        def("function g(x: integer): text? = if (x > 0) 'g:' + x else null;")

        chk("_type_of(f(*))", "text[(integer)->text]")
        chk("_type_of(g(*))", "text[(integer)->text?]")

        chkEx("{ var t: (integer) -> text; return _type_of(t); }", "text[(integer)->text]")
        chkEx("{ var t: (integer) -> text?; return _type_of(t); }", "text[(integer)->text?]")
        chkEx("{ var t: ((integer) -> text)?; return _type_of(t); }", "text[((integer)->text)?]")
        chkEx("{ var t: ((integer) -> text?)?; return _type_of(t); }", "text[((integer)->text?)?]")

        chkEx("{ val t: (integer) -> text = f(*); return t(123); }", "text[f:123]")
        chkEx("{ val t: (integer) -> text = g(*); return t(123); }", "ct_err:stmt_var_type:t:[(integer)->text]:[(integer)->text?]")
        chkEx("{ val t: (integer) -> text = null; return 0; }", "ct_err:stmt_var_type:t:[(integer)->text]:[null]")

        chkEx("{ val t: (integer) -> text? = f(*); return t(123); }", "text[f:123]")
        chkEx("{ val t: (integer) -> text? = g(*); return t(123); }", "text[g:123]")
        chkEx("{ val t: (integer) -> text? = null; return 0; }", "ct_err:stmt_var_type:t:[(integer)->text?]:[null]")

        chkEx("{ val t: ((integer) -> text)? = f(*); return t(123); }", "text[f:123]")
        chkEx("{ val t: ((integer) -> text)? = g(*); return t(123); }", "ct_err:stmt_var_type:t:[((integer)->text)?]:[(integer)->text?]")
        chkEx("{ val t: ((integer) -> text)? = null; return 0; }", "int[0]")

        chkEx("{ val t: ((integer) -> text?)? = f(*); return t(123); }", "text[f:123]")
        chkEx("{ val t: ((integer) -> text?)? = g(*); return t(123); }", "text[g:123]")
        chkEx("{ val t: ((integer) -> text?)? = null; return 0; }", "int[0]")
    }

    @Test fun testTypeCompatibilityUnit() {
        def("function f(x: integer) { print('f:'+x); }")
        def("function g(x: integer) { print('g:'+x); return x; }")

        chkExOut("{ val p: (integer) -> unit = f(*); p(123); return 0; }", "int[0]", "f:123")
        chkExOut("{ val p: (integer) -> unit = g(*); p(123); return 0; }", "int[0]", "g:123")

        chkExOut("{ val p: (integer) -> integer = f(*); return p(123); }",
                "ct_err:stmt_var_type:p:[(integer)->integer]:[(integer)->unit]")

        chkExOut("{ val p: (integer) -> integer = g(*); return p(123); }", "int[123]", "g:123")
    }

    @Test fun testTypeCompatibilityNullable() {
        def("function f0(x: integer): integer = x;")
        def("function f1(x: integer): integer? = if (x > 0) x else null;")
        def("function f2(x: integer?): integer = x ?: 0;")
        def("function f3(x: integer?): integer? = x;")

        val err = "ct_err:stmt_var_type:p"

        chkEx("{ val p: (integer) -> integer = f0(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer) -> integer = f1(*); return p(123); }", "$err:[(integer)->integer]:[(integer)->integer?]")
        chkEx("{ val p: (integer) -> integer = f2(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer) -> integer = f3(*); return p(123); }", "$err:[(integer)->integer]:[(integer?)->integer?]")

        chkEx("{ val p: (integer) -> integer? = f0(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer) -> integer? = f1(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer) -> integer? = f2(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer) -> integer? = f3(*); return p(123); }", "int[123]")

        chkEx("{ val p: (integer?) -> integer = f0(*); return p(123); }", "$err:[(integer?)->integer]:[(integer)->integer]")
        chkEx("{ val p: (integer?) -> integer = f1(*); return p(123); }", "$err:[(integer?)->integer]:[(integer)->integer?]")
        chkEx("{ val p: (integer?) -> integer = f2(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer?) -> integer = f3(*); return p(123); }", "$err:[(integer?)->integer]:[(integer?)->integer?]")

        chkEx("{ val p: (integer?) -> integer? = f0(*); return p(123); }", "$err:[(integer?)->integer?]:[(integer)->integer]")
        chkEx("{ val p: (integer?) -> integer? = f1(*); return p(123); }", "$err:[(integer?)->integer?]:[(integer)->integer?]")
        chkEx("{ val p: (integer?) -> integer? = f2(*); return p(123); }", "int[123]")
        chkEx("{ val p: (integer?) -> integer? = f3(*); return p(123); }", "int[123]")
    }

    @Test fun testActualArguments() {
        def("""
            function lo0() = 'lo0';
            function lo1(i: integer) = 'lo1:' + i;
            function lo2(i: integer, t: text) = 'lo2:' + i + ':' + t;

            struct fns {
                f0: () -> text = lo0(*);
                f1: (integer) -> text = lo1(*);
                f2: (integer, text) -> text = lo2(*);
            }
        """)

        val errType = "expr_call_argtype:[?]"

        chk("fns().f0()", "text[lo0]")
        chk("fns().f0(123)", "ct_err:expr:call:too_many_args:[?]:0:1")
        chk("fns().f0(123, 'Hello')", "ct_err:expr:call:too_many_args:[?]:0:2")

        chk("fns().f1()", "ct_err:expr:call:missing_args:[?]:[0]")
        chk("fns().f1(123)", "text[lo1:123]")
        chk("fns().f1('Hello')", "ct_err:$errType:0:integer:text")
        chk("fns().f1(123, 'Hello')", "ct_err:expr:call:too_many_args:[?]:1:2")

        chk("fns().f2()", "ct_err:expr:call:missing_args:[?]:[0,1]")
        chk("fns().f2(123)", "ct_err:expr:call:missing_args:[?]:[1]")
        chk("fns().f2('Hello')", "ct_err:[expr:call:missing_args:[?]:[1]][$errType:0:integer:text]")
        chk("fns().f2(123, 'Hello')", "text[lo2:123:Hello]")
        chk("fns().f2('Hello', 123)", "ct_err:[$errType:0:integer:text][$errType:1:text:integer]")
        chk("fns().f2('Hello', 'World')", "ct_err:$errType:0:integer:text")
        chk("fns().f2(123, 'Hello', 456)", "ct_err:expr:call:too_many_args:[?]:2:3")
        chk("fns().f2(123, 'Hello', 'World')", "ct_err:expr:call:too_many_args:[?]:2:3")
    }

    @Test fun testArgumentByName() {
        def("function f(x: integer) = x * x;")
        chk("f(*)(x = 7)", "ct_err:expr:call:unknown_named_arg:[?]:x")
        chk("f(*)(y = 7)", "ct_err:expr:call:unknown_named_arg:[?]:y")
        chk("f(*)(7)", "int[49]")
    }

    @Test fun testNameResolutionLocalVarVsGlobalFunction() {
        def("function f(x: integer) = 'f:'+x;")
        def("function g(x: integer) = 'g:'+x;")
        def("struct s { x: integer; }")

        chkEx("{ val g = 123; return g(5); }", "text[g:5]")
        chkEx("{ val g = f(*); return g(5); }", "text[f:5]")

        chkEx("{ val s = 123; return s(5); }", "s[x=int[5]]")
        chkEx("{ val s = f(*); return s(5); }", "text[f:5]")
    }

    @Test fun testComparison() {
        def("function f(x: integer) = x * x;")
        def("function g(x: integer) = x * x * x;")
        def("function u(x: integer) = '' + x;")
        def("function v(x: text) = x.size();")

        chkComparisonEqNe("==", "!=")
        chkComparisonEqNe("===", "!==")
    }

    private fun chkComparisonEqNe(eq: String, ne: String) {
        chk("f(*) $eq f(*)", "boolean[false]")
        chk("f(*) $ne f(*)", "boolean[true]")
        chk("f(*) $eq g(*)", "boolean[false]")
        chk("f(*) $ne g(*)", "boolean[true]")

        chk("f(*) $eq u(*)", "ct_err:binop_operand_type:$eq:[(integer)->integer]:[(integer)->text]")
        chk("f(*) $ne u(*)", "ct_err:binop_operand_type:$ne:[(integer)->integer]:[(integer)->text]")
        chk("u(*) $eq f(*)", "ct_err:binop_operand_type:$eq:[(integer)->text]:[(integer)->integer]")
        chk("u(*) $ne f(*)", "ct_err:binop_operand_type:$ne:[(integer)->text]:[(integer)->integer]")

        chk("f(*) $eq v(*)", "ct_err:binop_operand_type:$eq:[(integer)->integer]:[(text)->integer]")
        chk("f(*) $ne v(*)", "ct_err:binop_operand_type:$ne:[(integer)->integer]:[(text)->integer]")
        chk("v(*) $eq f(*)", "ct_err:binop_operand_type:$eq:[(text)->integer]:[(integer)->integer]")
        chk("v(*) $ne f(*)", "ct_err:binop_operand_type:$ne:[(text)->integer]:[(integer)->integer]")

        chk("f(123, *) $eq f(123, *)", "boolean[false]")
        chk("f(123, *) $ne f(123, *)", "boolean[true]")
    }

    @Test fun testUnallowedOperators() {
        def("function f(x: integer) = x * x;")

        for (op in listOf("<", ">", "<=", ">=", "+", "-", "*", "/", "%", "and", "or")) {
            chk("f(*) $op f(*)", "ct_err:binop_operand_type:$op:[(integer)->integer]:[(integer)->integer]")
        }

        for (op in listOf("+", "-", "not")) {
            chk("$op f(*)", "ct_err:unop_operand_type:$op:[(integer)->integer]")
        }
    }

    @Test fun testToText() {
        def("function f(x: integer) = x * x;")
        def("function g(x: integer) = x * x;")

        chk("'' + f(*)", "text[f(*)]")
        chk("'' + g(*)", "text[g(*)]")
        chk("'' + f(123, *)", "text[f(*)]")
        chk("'' + g(123, *)", "text[g(*)]")
    }

    @Test fun testGtvCompatibility() {
        tst.gtv = true
        def("function g(x: integer) = '' + x;")
        chkCompile("query q(f: (integer) -> text) = 0;", "ct_err:param_nogtv:f:(integer)->text")
        chkCompile("query q() = g(*);", "ct_err:result_nogtv:q:(integer)->text")
        chkCompile("operation op(f: (integer) -> text) {}", "ct_err:param_nogtv:f:(integer)->text")
    }

    @Test fun testInvocationKinds() {
        def("function f(x: integer) = x * x;")
        def("function g() = f(*);")
        def("function h() = g(*);")
        def("struct s { r: (integer) -> integer; }")

        chkEx("{ val r = f(*); return r(5); }", "int[25]")
        chkEx("{ val t = (f(*),); return t[0](5); }", "int[25]")
        chkEx("{ val t = (r = f(*),); return t.r(5); }", "int[25]")
        chkEx("{ val t = [f(*)]; return t[0](5); }", "int[25]")
        chkEx("{ val t = [33:f(*)]; return t[33](5); }", "int[25]")
        chkEx("{ val t = s(f(*)); return t.r(5); }", "int[25]")
        chkEx("{ val t = g(); return t(5); }", "int[25]")
        chkEx("{ return g()(5); }", "int[25]")
        chkEx("{ val t = h(); return t()(5); }", "int[25]")
        chkEx("{ return h()()(5); }", "int[25]")
    }

    @Test fun testParameterDefaultValues() {
        def("function f1(x: integer = 123) = 'f1:'+x;")
        def("function f2(x: integer = 123, y: integer = 456) = 'f2:'+x+','+y;")
        def("function g2(x: integer, y: integer = 456) = 'g2:'+x+','+y;")
        def("function h2(x: integer = 123, y: integer) = 'h2:'+x+','+y;")

        chk("_type_of(f1(*))", "text[()->text]")
        chk("f1(*)", "fn[f1(int[123])]")
        chk("f1(*)()", "text[f1:123]")

        chk("_type_of(f1(456, *))", "text[()->text]")
        chk("f1(456, *)", "fn[f1(int[456])]")
        chk("f1(456, *)()", "text[f1:456]")

        chk("_type_of(f1(x = *))", "text[(integer)->text]")
        chk("f1(x = *)", "fn[f1(*)]")
        chk("f1(x = *)(456)", "text[f1:456]")

        chk("_type_of(f2(*))", "text[()->text]")
        chk("f2(*)", "fn[f2(int[123],int[456])]")
        chk("f2(*)()", "text[f2:123,456]")

        chk("_type_of(f2(777, *))", "text[()->text]")
        chk("f2(777, *)", "fn[f2(int[777],int[456])]")
        chk("f2(777, *)()", "text[f2:777,456]")

        chk("_type_of(f2(*, 888))", "text[(integer)->text]")
        chk("f2(*, 888)", "fn[f2(*,int[888])]")
        chk("f2(*, 888)(777)", "text[f2:777,888]")
        chk("_type_of(f2(777, 888, *))", "text[()->text]")
        chk("f2(777, 888, *)", "fn[f2(int[777],int[888])]")
        chk("f2(777, 888, *)()", "text[f2:777,888]")

        chk("_type_of(f2(x = *))", "text[(integer)->text]")
        chk("f2(x = *)", "fn[f2(*,int[456])]")
        chk("f2(x = *)(777)", "text[f2:777,456]")
        chk("_type_of(f2(y = *))", "text[(integer)->text]")
        chk("f2(y = *)", "fn[f2(int[123],*)]")
        chk("f2(y = *)(888)", "text[f2:123,888]")

        chk("_type_of(f2(x = *, y = *))", "text[(integer,integer)->text]")
        chk("f2(x = *, y = *)", "fn[f2(*,*)]")
        chk("f2(x = *, y = *)(777, 888)", "text[f2:777,888]")
        chk("_type_of(f2(y = *, x = *))", "text[(integer,integer)->text]")
        chk("f2(y = *, x = *)", "fn[f2(*,*)]")
        chk("f2(y = *, x = *)(888, 777)", "text[f2:777,888]")

        chk("_type_of(f2(x = 777, y = *))", "text[(integer)->text]")
        chk("f2(x = 777, y = *)", "fn[f2(int[777],*)]")
        chk("f2(x = 777, y = *)(888)", "text[f2:777,888]")
        chk("_type_of(f2(x = *, y = 888))", "text[(integer)->text]")
        chk("f2(x = *, y = 888)", "fn[f2(*,int[888])]")
        chk("f2(x = *, y = 888)(777)", "text[f2:777,888]")
    }

    @Test fun testParameterTypePromotion() {
        def("function f(x: decimal) = '' + x;")
        def("function g(x: decimal = 123) = '' + x;")

        chk("f(*)(123)", "text[123]")
        chk("f(*)(123.4)", "text[123.4]")
        chk("f(123, *)()", "text[123]")
        chk("f(123.4, *)()", "text[123.4]")

        chk("g(*)()", "text[123]")
        chk("g(456, *)()", "text[456]")
        chk("g(456.7, *)()", "text[456.7]")
        chk("g(x = 456, *)()", "text[456]")
        chk("g(x = 456.7, *)()", "text[456.7]")
        chk("g(x = *)(456)", "text[456]")
        chk("g(x = *)(456.7)", "text[456.7]")
    }

    @Test fun testMapSetKey() {
        def("function f(x: integer) = x * x;")

        chkCompile("function t(x: set<(integer)->text>) {}", "OK")
        chkCompile("function t(x: map<(integer)->text,boolean>) {}", "OK")
        chkCompile("function t(x: map<decimal,(integer)->text>) {}", "OK")

        chk("[f(*), f(*), f(*)].size()", "int[3]")
        chk("set([f(*), f(*), f(*)]).size()", "int[3]")
        chk("[f(*):1,f(*):2,f(*):3].size()", "int[3]")
    }

    @Test fun testReturnUnit() {
        def("function f(x: integer) { print('f:'+x); }")
        def("function g(x: integer) { val s = 'g:'+x; print(s); return s; }")

        chk("_type_of(f(*))", "text[(integer)->unit]")
        chk("_type_of(g(*))", "text[(integer)->text]")

        chkEx("{ val p: (integer)->unit = f(*); p(123); return 0; }", "int[0]")
        chkOut("f:123")

        chkEx("{ val p: (integer)->unit = f(*); return p(123); }", "ct_err:stmt_return_unit")
        chkEx("{ val p: (integer)->unit = g(*); return 0; }", "int[0]")
        chkEx("{ val p: (integer)->unit = g(*); return p(123); }", "ct_err:stmt_return_unit")
        chkOut()

        chkEx("{ val p: (integer)->unit = g(*); p(123); return 0; }", "int[0]")
        chkOut("g:123")
    }

    @Test fun testSafeAccessOperator() {
        def("function f(x: integer) { print('f:'+x); return x * x; }")
        def("struct s { p: (integer) -> integer = f(*); }")
        def("struct u { s = s(); }")
        def("struct v { u = u(); }")
        def("function make_s(b: boolean) = if (b) s() else null;")
        def("function make_v(b: boolean) = if (b) v() else null;")

        chk("s().p(5)", "int[25]")
        chkOut("f:5")

        chk("_type_of(s().p(0))", "text[integer]")
        chk("_type_of(make_s(false).p(0))", "ct_err:expr_mem_null:s?:p")
        chk("_type_of(make_s(false)?.p(0))", "text[integer?]")
        chkOut()

        chk("_type_of(v().u.s.p(0))", "text[integer]")
        chk("_type_of(make_v(false).u.s.p(0))", "ct_err:expr_mem_null:v?:u")
        chk("_type_of(make_v(false)?.u.s.p(0))", "ct_err:expr_mem_null:u?:s")
        chk("_type_of(make_v(false)?.u?.s.p(0))", "ct_err:expr_mem_null:s?:p")
        chk("_type_of(make_v(false)?.u?.s?.p(0))", "text[integer?]")
        chkOut()

        chkEx("{ val t: s? = make_s(false); return t.p(5); }", "ct_err:expr_mem_null:s?:p")
        chkEx("{ val t: s? = make_s(false); return t?.p(5); }", "null")
        chkOut()

        chkEx("{ val t: s? = make_s(true); return t?.p(5); }", "int[25]")
        chkOut("f:5")

        chkEx("{ val t: v? = make_v(false); return t.u.s.p(5); }", "ct_err:expr_mem_null:v?:u")
        chkEx("{ val t: v? = make_v(false); return t?.u.s.p(5); }", "ct_err:expr_mem_null:u?:s")
        chkEx("{ val t: v? = make_v(false); return t?.u?.s.p(5); }", "ct_err:expr_mem_null:s?:p")
        chkEx("{ val t: v? = make_v(false); return t?.u?.s?.p(5); }", "null")
        chkOut()

        chkEx("{ val t: v? = make_v(true); return t?.u?.s?.p(5); }", "int[25]")
        chkOut("f:5")
    }

    @Test fun testCollectionAtPartialApplication() {
        def("struct data { i: integer; t: text; }")
        def("function f(i: integer, t: text) = 'f:'+i+','+t;")
        def("function datas() = [data(11,'A'), data(22,'B')];")

        chkEx("{ val ps = datas()@*{}( f(i=.i, *)(.t, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")
        chkEx("{ val ps = datas()@*{}( f(i=.i, *)(.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val ps = datas()@*{}( f(t=.t, *)(.i, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")
        chkEx("{ val ps = datas()@*{}( f(t=.t, *)(.i, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
    }

    @Test fun testReturnFunctionType() {
        def("function f(x: decimal): text = 'f:' + x;")
        def("function g(x: integer, y: decimal) = f(x * y);")
        def("function h(x: integer): (decimal) -> text = g(x, *);")
        chkEx("{ val p: (integer) -> (decimal) -> text = h(*); return p(123)(45.6); }", "text[f:5608.8]")
    }

    @Test fun testRecursiveToString() {
        def("function f(l: list<()->text>) = 'Hello';")
        def("""
            function g(): () -> text {
                val l = list<()->text>();
                val p = f(l, *);
                l.add(p);
                return p;
            }
        """)

        chk("'' + g()", "text[f(*)]")
        chk("_strict_str(g())", "text[fn[f(list<()->text>[fn[...]])]]")
    }

    @Test fun testStructAttribute() {
        def("function g() = gtv.from_json('123');")
        def("struct s1 { to_gtv: integer = 123; }")
        def("struct s2 { to_gtv: ()->integer = integer.from_hex('7b',*); }")
        def("struct s3 { to_gtv: ()->gtv = g(*); }")

        chk("s1().to_gtv", "int[123]")
        chk("s1().to_gtv()", "gtv[[123]]")
        chk("s2().to_gtv", "fn[integer.from_hex(text[7b])]")
        chk("s2().to_gtv()", "ct_err:type_value_member:ambig:to_gtv:[attribute,function]")
        chk("s3().to_gtv", "fn[g()]")
        chk("s3().to_gtv()", "ct_err:type_value_member:ambig:to_gtv:[attribute,function]")
    }

    @Test fun testDefaultMemberFunctions() {
        chkEx("{ val f = integer.from_hex(*); return f.hash(); }", "ct_err:fn:invalid:(text)->integer:hash")
        chkEx("{ val f = integer.from_hex(*); return f.to_gtv(); }", "ct_err:fn:invalid:(text)->integer:to_gtv")
    }
}
