/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.testutils.BaseRellTest
import java.math.BigInteger
import kotlin.math.sign
import kotlin.test.Test

class ExpressionTest: BaseRellTest() {
    @Test fun testPrecedence() {
        chk("2 * 3 + 4", "int[10]")
        chk("4 + 2 * 3", "int[10]")

        chk("5 * 4 / 3", "int[6]")
        chk("5 * (4 / 3)", "int[5]")
        chk("555 / 13 % 5", "int[2]")
        chk("7 - 555 / 13 % 5", "int[5]")

        chk("1 == 1 and 2 == 2 and 3 == 3", "boolean[true]")
        chk("1 == 1 and 2 == 0 and 3 == 3", "boolean[false]")
        chk("1 == 2 or 3 == 4 or 5 == 6", "boolean[false]")
        chk("1 == 2 or 3 == 3 or 5 == 6", "boolean[true]")

        chk("false or false and false", "boolean[false]")
        chk("false or false and true", "boolean[false]")
        chk("false or true and false", "boolean[false]")
        chk("false or true and true", "boolean[true]")
        chk("true or false and false", "boolean[true]")
        chk("true or false and true", "boolean[true]")
        chk("true or true and false", "boolean[true]")
        chk("true or true and true", "boolean[true]")

        chk("false and false or false", "boolean[false]")
        chk("false and false or true", "boolean[true]")
        chk("false and true or false", "boolean[false]")
        chk("false and true or true", "boolean[true]")
        chk("true and false or false", "boolean[false]")
        chk("true and false or true", "boolean[true]")
        chk("true and true or false", "boolean[true]")
        chk("true and true or true", "boolean[true]")

        chk("5 > 3 and 8 == 2 or 1 < 9", "boolean[true]")
        chk("5 > 3 and 8 == 2 or 9 == 6", "boolean[false]")
        chk("3 > 5 or 0 < 8 and 5 == 5", "boolean[true]")
        chk("5 == 5 or 3 > 5 and 8 < 0", "boolean[true]")

        chk("2 + 3 * 4 == 114 % 100", "boolean[true]")

        chk("2 * 3 + 4 * 5 + 6 * 7 + 8 * 9 + 10 * 11 + 12 * 13 + 14 * 15 + 16 * 17 + 18 * 19", "int[1230]")
        chk("2 * 3 + 4 * 5 - 6 * 7 + 8 * 9 - 10 * 11 + 12 * 13 - 14 * 15 + 16 * 17 - 18 * 19", "int[-178]")
    }

    @Test fun testShortCircuitEvaluation() {
        chk("1 / 0", "rt_err:expr:/:div0:1")
        chk("a != 0 and 15 / a > 3", 0, "boolean[false]")
        chk("a != 0 and 15 / a > 3", 5, "boolean[false]")
        chk("a != 0 and 15 / a > 3", 3, "boolean[true]")
        chk("a == 0 or 15 / a > 3", 0, "boolean[true]")
        chk("a == 0 or 15 / a > 3", 5, "boolean[false]")
        chk("a == 0 or 15 / a > 3", 3, "boolean[true]")
    }

    @Test fun testFunctionVsVariable() {
        chkEx("{ val abs = a; return abs(abs); }", 123, "int[123]")
        chkEx("{ val abs = a; return abs(abs); }", -123, "int[123]")
        chkEx("{ val abs = a; return abs; }", -123, "int[-123]")
        chkEx("{ val abs = a; return abs.str(); }", -123, "text[-123]")
    }

    @Test fun testMemberFunctionVsField() {
        def("struct foo { to_gtv: integer; }")
        chkEx("{ val v = foo(123); return v.to_gtv; }", "int[123]")
        chkEx("{ val v = foo(123); return v.to_gtv.str(); }", "text[123]")
        chkEx("{ val v = foo(123); return v.to_gtv().to_json().str(); }", "text[[123]]")
    }

    @Test fun testListItems() {
        tstCtx.useSql = true
        def("entity user { name: text; }")

        chkOp("create user('Bob');")
        chkOp("create user('Alice');")
        chkOp("create user('Trudy');")

        chkEx("{ val s = user @* {}; return s[0]; }", "user[1]")
        chkEx("{ val s = user @* {}; return s[1]; }", "user[2]")
        chkEx("{ val s = user @* {}; return s[2]; }", "user[3]")

        chkEx("{ val s = user @* {}; return s[-1]; }", "rt_err:list:index:3:-1")
        chkEx("{ val s = user @* {}; return s[3]; }", "rt_err:list:index:3:3")
    }

    @Test fun testFunctionsUnderAt() {
        tstCtx.useSql = true
        def("entity user { name: text; score: integer; }")
        chkOp("create user('Bob',-5678);")

        chkEx("{ val s = 'Hello'; return user @ {} ( .name.size() + s.size() ); }", "int[8]")
        chkEx("{ val x = -1234; return user @ {} ( abs(x), abs(.score) ); }", "(int[1234],int[5678])")
    }

    @Test fun testUnitType() {
        chk("+ unit()", "ct_err:expr_operand_unit")
        chk("- unit()", "ct_err:expr_operand_unit")
        chk("not unit()", "ct_err:expr_operand_unit")
        chk("unit() + 5", "ct_err:expr_operand_unit")
        chk("abs(unit())", "ct_err:expr_arg_unit")
        chkEx("{ print(unit()); return 123; }", "ct_err:expr_arg_unit")
        chkEx("{ return unit(); }", "ct_err:stmt_return_unit")
    }

    @Test fun testEntityPathExpr() {
        tstCtx.useSql = true
        def("entity company { name: text; }")
        def("entity user { name: text; company; }")

        chkOp("""
            val facebook = create company('Facebook');
            val amazon = create company('Amazon');
            val microsoft = create company('Microsoft');
            create user('Mark', facebook);
            create user('Jeff', amazon);
            create user('Bill', microsoft);
        """)

        chkEx("{ val u = user @ { 'Mark' }; return u.company.name; }", "text[Facebook]")
        chkEx("{ val u = user @ { 'Jeff' }; return u.company.name; }", "text[Amazon]")
        chkEx("{ val u = user @ { 'Bill' }; return u.company.name; }", "text[Microsoft]")

        chk("((u: user) @ { 'Mark' } ( u )).company.name", "text[Facebook]")
        chk("((u: user) @ { 'Jeff' } ( u )).company.name", "text[Amazon]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name", "text[Microsoft]")
    }

    @Test fun testEntityPathExprMix() {
        tstCtx.useSql = true
        def("entity company { name: text; }")
        def("entity user { name: text; company; }")

        chkOp("""
            val microsoft = create company('Microsoft');
            create user('Bill', microsoft);
        """)

        chk("((u: user) @ { 'Bill' } ( u )).company.name.size()", "int[9]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.upper_case()", "text[MICROSOFT]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.replace('soft', 'hard')", "text[Microhard]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.split('o')", "list<text>[text[Micr],text[s],text[ft]]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.matches('[A-Za-z]+')", "boolean[true]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.matches('[0-9]+')", "boolean[false]")
        chk("((u: user) @ { 'Bill' } ( u )).company.name.upper_case().replace('SOFT','HARD').lower_case()", "text[microhard]")

        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.size(); }", "int[9]")
        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.upper_case(); }", "text[MICROSOFT]")
        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.replace('soft', 'hard'); }", "text[Microhard]")
        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.split('o'); }", "list<text>[text[Micr],text[s],text[ft]]")
        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.matches('[A-Za-z]+'); }", "boolean[true]")
        chkEx("{ val x = user@{'Bill'}(u=user); return x.u.company.name.matches('[0-9]+'); }", "boolean[false]")
        chkEx("{ val x = user@{'Bill'}(u=user); return text.from_bytes(x.u.company.name.to_bytes()).upper_case().replace('SOFT','HARD').lower_case(); }",
                "text[microhard]")
    }

    @Test fun testEntityPathExprComplex() {
        initEntityPathExprComplex()

        chk("((c: c4) @ { 'c4_1' } ( c )).c3.c2.c1.name", "text[c1_1]")
        chk("((c: c4) @ { 'c4_2' } ( c )).c3.c2.c1.name", "text[c1_2]")

        chk("((c: c4) @ { 'c4_1' } ( t3 = .c3, t2 = .c3.c2 )).t3.c2.c1.name", "text[c1_1]")
        chk("((c: c4) @ { 'c4_1' } ( t3 = .c3, t2 = .c3.c2 )).t2.c1.name", "text[c1_1]")
        chk("((c: c4) @ { 'c4_2' } ( t3 = .c3, t2 = .c3.c2 )).t3.c2.c1.name", "text[c1_2]")
        chk("((c: c4) @ { 'c4_2' } ( t3 = .c3, t2 = .c3.c2 )).t2.c1.name", "text[c1_2]")
    }

    @Test fun testEntityPathExprComplexNullable() {
        def("function data() = c4 @? {} limit 1;")
        initEntityPathExprComplex()

        chkEx("{ val c = data(); return c.c3; }", "ct_err:expr_mem_null:c4?:c3")
        chkEx("{ val c = data(); return c?.c3; }", "c3[5]")
        chkEx("{ val c = data(); return c?.c3.c2; }", "ct_err:expr_mem_null:c3?:c2")
        chkEx("{ val c = data(); return c?.c3?.c2; }", "c2[3]")
        chkEx("{ val c = data(); return c?.c3?.c2.c1; }", "ct_err:expr_mem_null:c2?:c1")
        chkEx("{ val c = data(); return c?.c3?.c2?.c1; }", "c1[1]")
    }

    private fun initEntityPathExprComplex() {
        tstCtx.useSql = true
        def("entity c1 { name: text; }")
        def("entity c2 { name: text; c1; }")
        def("entity c3 { name: text; c2; }")
        def("entity c4 { name: text; c3; }")

        chkOp("""
            val c1_1 = create c1('c1_1');
            val c1_2 = create c1('c1_2');
            val c2_1 = create c2('c2_1', c1_1);
            val c2_2 = create c2('c2_2', c1_2);
            val c3_1 = create c3('c3_1', c2_1);
            val c3_2 = create c3('c3_2', c2_2);
            val c4_1 = create c4('c4_1', c3_1);
            val c4_2 = create c4('c4_2', c3_2);
        """)
    }

    @Test fun testTuple() {
        chk("(123)", "int[123]")
        chk("(((123)))", "int[123]")
        chk("(123,)", "(int[123])")
        chk("(123,'Hello',false)", "(int[123],text[Hello],boolean[false])")
        chk("(123,('Hello',('World',456)),false)", "(int[123],(text[Hello],(text[World],int[456])),boolean[false])")
        chk("(123,456)", "(int[123],int[456])")
        chk("(123,456,)", "(int[123],int[456])")
        chk("(a=123)", "(a=int[123])")
        chk("(a=123,)", "(a=int[123])")
        chk("(a=123,b='Hello')", "(a=int[123],b=text[Hello])")
        chk("(a=123,b='Hello',)", "(a=int[123],b=text[Hello])")
        chk("(a=123,'Hello')", "(a=int[123],text[Hello])")
        chk("(123,b='Hello')", "(int[123],b=text[Hello])")
        chk("(a='Hello',b=(x=123,y=456))", "(a=text[Hello],b=(x=int[123],y=int[456]))")

        tst.ideDefIdConflictError = false
        chk("(a=123,a=456)", "ct_err:expr_tuple_dupname:a")
        chk("(a=123,a=123)", "ct_err:expr_tuple_dupname:a")
    }

    @Test fun testTupleAt() {
        tstCtx.useSql = true
        def("entity user { name: text; street: text; house: integer; }")
        insert("c0.user", "name,street,house", "1,'Bob','Bahnstr.',13")

        chk("user @ {} ( x = (.name,) ) ", "(x=(text[Bob]))")
        chk("user @ {} ( x = (.name, .street, .house) ) ", "(x=(text[Bob],text[Bahnstr.],int[13]))")
    }

    @Test fun testList() {
        chk("list([1,2,3,4,5])", "list<integer>[int[1],int[2],int[3],int[4],int[5]]")
        chk("list()", "ct_err:fn:sys:unresolved_type_params:[list]:T")
        chk("list<integer>()", "list<integer>[]")
        chk("list<integer>([1,2,3])", "list<integer>[int[1],int[2],int[3]]")
        chk("list<integer>(['Hello'])", "ct_err:expr_call_badargs:[list<integer>]:[list<text>]")
        chk("list<text>([12345])", "ct_err:expr_call_badargs:[list<text>]:[list<integer>]")
        chk("['Hello', 'World']", "list<text>[text[Hello],text[World]]")
        chk("['Hello', 'World', 12345]", "ct_err:expr_list_itemtype:[text]:[integer]")
        chk("[unit()]", "ct_err:expr_list_unit")
        chk("[print('Hello')]", "ct_err:expr_list_unit")
    }

    @Test fun testUnknownFunction() {
        chkEx("{ val s = 'Hello'; return s.badfunc(); }", "ct_err:unknown_member:[text]:badfunc")
    }

    @Test fun testIn() {
        chk("123 in [123, 456]", "boolean[true]")
        chk("456 in [123, 456]", "boolean[true]")
        chk("789 in [123, 456]", "boolean[false]")
        chk("123 in list<integer>()", "boolean[false]")
        chk("123 in list<text>()", "ct_err:binop_operand_type:in:[integer]:[list<text>]")
        chk("'Hello' in list<integer>()", "ct_err:binop_operand_type:in:[text]:[list<integer>]")

        chk("123 in set([123, 456])", "boolean[true]")
        chk("456 in set([123, 456])", "boolean[true]")
        chk("789 in set([123, 456])", "boolean[false]")
        chk("123 in set<integer>()", "boolean[false]")
        chk("123 in set<text>()", "ct_err:binop_operand_type:in:[integer]:[set<text>]")
        chk("'Hello' in set<integer>()", "ct_err:binop_operand_type:in:[text]:[set<integer>]")

        chk("123 in [123:'Bob',456:'Alice']", "boolean[true]")
        chk("456 in [123:'Bob',456:'Alice']", "boolean[true]")
        chk("789 in [123:'Bob',456:'Alice']", "boolean[false]")
        chk("123 in map<integer,text>()", "boolean[false]")
        chk("123 in map<text,integer>()", "ct_err:binop_operand_type:in:[integer]:[map<text,integer>]")
        chk("'Hello' in map<integer,text>()", "ct_err:binop_operand_type:in:[text]:[map<integer,text>]")

        chk("123 not in [123, 456]", "boolean[false]")
        chk("456 not in [123, 456]", "boolean[false]")
        chk("789 not in [123, 456]", "boolean[true]")
        chk("123 not in list<integer>()", "boolean[true]")
        chk("123 not in [123:'Bob',456:'Alice']", "boolean[false]")
        chk("789 not in [123:'Bob',456:'Alice']", "boolean[true]")
        chk("123 not in map<integer,text>()", "boolean[true]")
    }

    @Test fun testInNullable() {
        chk("5 in list<integer?>([5, null])", "boolean[true]")
        chk("6 in list<integer?>([5, null])", "boolean[false]")
        chk("null in list<integer?>([5, null])", "boolean[true]")
        chk("null in list<integer?>([5, 6])", "boolean[false]")

        chk("5 in set<integer?>([5, null])", "boolean[true]")
        chk("6 in set<integer?>([5, null])", "boolean[false]")
        chk("null in set<integer?>([5, null])", "boolean[true]")
        chk("null in set<integer?>([5, 6])", "boolean[false]")

        chk("5 in map<integer?,text>([5:'a', null:'b'])", "boolean[true]")
        chk("6 in map<integer?,text>([5:'a', null:'b'])", "boolean[false]")
        chk("null in map<integer?,text>([5:'a', null:'b'])", "boolean[true]")
        chk("null in map<integer?,text>([5:'a', 6:'b'])", "boolean[false]")
    }

    @Test fun testInNullableVersionControl() {
        val err = "ct_err:binop_operand_type:in"
        chkVerCtExpr("5 in list<integer?>([5, null])", "0.11.0", "$err:[integer]:[list<integer?>]")
        chkVerCtExpr("null in list<integer?>([5, null])", "0.11.0", "$err:[null]:[list<integer?>]")
        chkVerCtExpr("5 in set<integer?>([5, null])", "0.11.0", "$err:[integer]:[set<integer?>]")
        chkVerCtExpr("null in set<integer?>([5, null])", "0.11.0", "$err:[null]:[set<integer?>]")
        chkVerCtExpr("5 in map<integer?,text>([5:'a', null:'b'])", "0.11.0", "$err:[integer]:[map<integer?,text>]")
        chkVerCtExpr("null in map<integer?,text>([5:'a', null:'b'])", "0.11.0", "$err:[null]:[map<integer?,text>]")
    }

    @Test fun testInPromotion() {
        chk("5 in list<decimal>([5.0, 7.0])", "boolean[true]")
        chk("6 in list<decimal>([5.0, 7.0])", "boolean[false]")
        chk("7 in list<decimal>([5.0, 7.0])", "boolean[true]")

        chk("5 in set<decimal>([5.0, 7.0])", "boolean[true]")
        chk("6 in set<decimal>([5.0, 7.0])", "boolean[false]")
        chk("7 in set<decimal>([5.0, 7.0])", "boolean[true]")

        chk("5 in map<decimal,text>([5.0:'a', 7.0:'b'])", "boolean[true]")
        chk("6 in map<decimal,text>([5.0:'a', 7.0:'b'])", "boolean[false]")
        chk("7 in map<decimal,text>([5.0:'a', 7.0:'b'])", "boolean[true]")

        chk("5 in list<decimal?>([5.0, null])", "boolean[true]")
        chk("7 in list<decimal?>([5.0, null])", "boolean[false]")
        chk("5 in set<decimal?>([5.0, null])", "boolean[true]")
        chk("7 in set<decimal?>([5.0, null])", "boolean[false]")
        chk("5 in map<decimal?,text>([5.0:'a', null:'b'])", "boolean[true]")
        chk("7 in map<decimal?,text>([5.0:'a', null:'b'])", "boolean[false]")
    }

    @Test fun testNamespace() {
        chk("integer", "ct_err:expr_novalue:type:[integer]")
        chk("integer('123')", "int[123]")
        chk("integer.from_hex('1234')", "int[4660]")
        chk("integer.MAX_VALUE", "int[9223372036854775807]")
    }

    @Test fun testNamespaceUnderAt() {
        tstCtx.useSql = true
        def("entity user { name: text; score: integer; }")
        chkOp("create user('Bob',-5678);")

        chk("user @ { .score == integer }", "ct_err:expr_novalue:type:[integer]")
        chk("user @ { .score == integer('-5678') } ( .name )", "text[Bob]")
        chk("user @ { .score == -5678 } ( .name, integer('1234') )", "(name=text[Bob],int[1234])")
        chk("user @ { .score == -integer.from_hex('162e') } ( .name )", "text[Bob]")
        chk("user @ { .name == 'Bob' } ( .name, .score + integer.from_hex('1234') )", "(name=text[Bob],int[-1018])")

        chk("user @ { .score < integer.MAX_VALUE } ( .name )", "text[Bob]")
        chk("user @* { .score < integer.MIN_VALUE } ( .name )", "list<text>[]")
        chk("user @ { integer.MAX_VALUE + .score == 9223372036854770129 } ( .name )", "text[Bob]")
        chk("user @ {} ( .name, .score + integer.MAX_VALUE )", "(name=text[Bob],int[9223372036854770129])")
    }

    @Test fun testMoreFunctionsUnderAt() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity user { id: integer; name1: text; name2: text; v1: integer; v2: integer; }")
        chkOp("""
            create user(id = 1, name1 = 'Bill', name2 = 'Gates', v1 = 111, v2 = 222);
            create user(id = 2, name1 = 'Mark', name2 = 'Zuckerberg', v1 = 333, v2 = 444);
            create user(id = 3, name1 = 'Steve', name2 = 'Wozniak', v1 = 555, v2 = 666);
        """)

        chkEx("{ return user @* {} (.id+0, (.name1 + .name2).size()); }", "[(1,9), (2,14), (3,12)]")
        chkEx("{ return user @* {} (.id+0, (.name1 + .name2).upper_case().lower_case().size()); }", "[(1,9), (2,14), (3,12)]")
        chkEx("{ return user @* {} (.id+0, (.v1 * (.v2 + 101)).str()); }", "[(1,35853), (2,181485), (3,425685)]")
        chkEx("{ return user @* {} (.id+0, (.v1 * (.v2 + 101)).str().size()); }", "[(1,5), (2,6), (3,6)]")
        chkEx("{ return user @* {} (.id+0, (.name1 + .name2).foo); }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ return user @* {} (.id+0, (.v1 * (.v2 + 101)).foo); }", "ct_err:unknown_member:[integer]:foo")
        chkEx("{ return user @* {} (.id+0, (.name1 + .name2).foo()); }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ return user @* {} (.id+0, (.v1 * (.v2 + 101)).foo()); }", "ct_err:unknown_member:[integer]:foo")

        val c = "val str1 = 'Hello'; val k1 = 777;"
        chkEx("{ $c return user @* {} (.id+0, (str1 + .name2).size()); }", "[(1,10), (2,15), (3,12)]")
        chkEx("{ $c return user @* {} (.id+0, (str1 + .name2).upper_case().lower_case().size()); }", "[(1,10), (2,15), (3,12)]")
        chkEx("{ $c return user @* {} (.id+0, (k1 * (.v2 + 101)).str()); }", "[(1,250971), (2,423465), (3,595959)]")
        chkEx("{ $c return user @* {} (.id+0, (k1 * (.v2 + 101)).str().size()); }", "[(1,6), (2,6), (3,6)]")
        chkEx("{ $c return user @* {} (.id+0, (str1 + .name2).foo); }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ $c return user @* {} (.id+0, (k1 * (.v2 + 101)).foo); }", "ct_err:unknown_member:[integer]:foo")
        chkEx("{ $c return user @* {} (.id+0, (str1 + .name2).foo()); }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ $c return user @* {} (.id+0, (k1 * (.v2 + 101)).foo()); }", "ct_err:unknown_member:[integer]:foo")
    }

    @Test fun testPathError() {
        chkEx("{ val s = 'Hello'; return s.foo.bar; }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ val s = 'Hello'; return s.foo.bar(); }", "ct_err:unknown_member:[text]:foo")
        chkEx("{ val s = 'Hello'; return s.foo(); }", "ct_err:unknown_member:[text]:foo")
    }

    @Test fun testCallNotCallable() {
        chk("123()", "ct_err:expr_call_nofn:integer")
        chk("123(456)", "ct_err:expr_call_nofn:integer")
        chk("'Hello'()", "ct_err:expr_call_nofn:text")
        chk("'Hello'(123)", "ct_err:expr_call_nofn:text")
    }

    @Test fun testEqRef() {
        chkEx("{ val a = [1, 2, 3]; val b = [1, 2, 3]; return a == b; }", "boolean[true]")
        chkEx("{ val a = [1, 2, 3]; val b = [1, 2, 3]; return a != b; }", "boolean[false]")
        chkEx("{ val a = [1, 2, 3]; val b = [1, 2, 3]; return a === b; }", "boolean[false]")
        chkEx("{ val a = [1, 2, 3]; val b = [1, 2, 3]; return a !== b; }", "boolean[true]")
        chkEx("{ val a = [1, 2, 3]; val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = [1, 2, 3]; val b = a; return a !== b; }", "boolean[false]")

        chkEx("{ val a = set([1, 2, 3]); val b = set([1, 2, 3]); return a == b; }", "boolean[true]")
        chkEx("{ val a = set([1, 2, 3]); val b = set([1, 2, 3]); return a != b; }", "boolean[false]")
        chkEx("{ val a = set([1, 2, 3]); val b = set([1, 2, 3]); return a === b; }", "boolean[false]")
        chkEx("{ val a = set([1, 2, 3]); val b = set([1, 2, 3]); return a !== b; }", "boolean[true]")
        chkEx("{ val a = set([1, 2, 3]); val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = set([1, 2, 3]); val b = a; return a !== b; }", "boolean[false]")

        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = [1:'A',2:'B',3:'C']; return a == b; }", "boolean[true]")
        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = [1:'A',2:'B',3:'C']; return a != b; }", "boolean[false]")
        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = [1:'A',2:'B',3:'C']; return a === b; }", "boolean[false]")
        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = [1:'A',2:'B',3:'C']; return a !== b; }", "boolean[true]")
        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = [1:'A',2:'B',3:'C']; val b = a; return a !== b; }", "boolean[false]")

        chkEx("{ val a = (123, 'Hello'); val b = (123, 'Hello'); return a == b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b = (123, 'Hello'); return a != b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b = (123, 'Hello'); return a === b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b = (123, 'Hello'); return a !== b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b = a; return a !== b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b = ('Hello', 123); return a == b; }",
                "ct_err:binop_operand_type:==:[(integer,text)]:[(text,integer)]")

        chkEx("{ val a = range(123); val b = range(123); return a == b; }", "boolean[true]")
        chkEx("{ val a = range(123); val b = range(123); return a != b; }", "boolean[false]")
        chkEx("{ val a = range(123); val b = range(123); return a === b; }", "boolean[false]")
        chkEx("{ val a = range(123); val b = range(123); return a !== b; }", "boolean[true]")
        chkEx("{ val a = range(123); val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = range(123); val b = a; return a !== b; }", "boolean[false]")

        chkEx("{ return 123 === 123; }", "ct_err:binop_operand_type:===:[integer]:[integer]")
        chkEx("{ return 123 !== 123; }", "ct_err:binop_operand_type:!==:[integer]:[integer]")
        chkEx("{ return true === true; }", "ct_err:binop_operand_type:===:[boolean]:[boolean]")
        chkEx("{ return true !== true; }", "ct_err:binop_operand_type:!==:[boolean]:[boolean]")
        chkEx("{ return 'Hello' === 'Hello'; }", "ct_err:binop_operand_type:===:[text]:[text]")
        chkEx("{ return 'Hello' !== 'Hello'; }", "ct_err:binop_operand_type:!==:[text]:[text]")
        chkEx("{ return x'12AB' === x'12AB'; }", "ct_err:binop_operand_type:===:[byte_array]:[byte_array]")
        chkEx("{ return x'12AB' !== x'12AB'; }", "ct_err:binop_operand_type:!==:[byte_array]:[byte_array]")
        chkEx("{ return null === null; }", "ct_err:binop_operand_type:===:[null]:[null]")
        chkEx("{ return null !== null; }", "ct_err:binop_operand_type:!==:[null]:[null]")
    }

    @Test fun testEqRefNullable() {
        chkEx("{ val a: integer? = _nullable(123); return a === null; }", "ct_err:binop_operand_type:===:[integer?]:[null]")
        chkEx("{ val a: integer? = _nullable(123); return a !== null; }", "ct_err:binop_operand_type:!==:[integer?]:[null]")
        chkEx("{ val a: integer? = _nullable(123); return a === 123; }", "ct_err:binop_operand_type:===:[integer?]:[integer]")
        chkEx("{ val a: integer? = _nullable(123); return a !== 123; }", "ct_err:binop_operand_type:!==:[integer?]:[integer]")

        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); return a === null; }", "boolean[false]")
        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); return a !== null; }", "boolean[true]")
        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); return a === [1,2,3]; }", "boolean[false]")
        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); return a !== [1,2,3]; }", "boolean[true]")
        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); val b = a; return a === b; }", "boolean[true]")
        chkEx("{ val a: list<integer>? = _nullable([1,2,3]); val b = a; return a !== b; }", "boolean[false]")
        chkEx("{ val a: list<integer>? = null; return a === null; }", "boolean[true]")
        chkEx("{ val a: list<integer>? = null; return a !== null; }", "boolean[false]")
        chkEx("{ val a: list<integer>? = null; return a === [1,2,3]; }", "boolean[false]")
        chkEx("{ val a: list<integer>? = null; return a !== [1,2,3]; }", "boolean[true]")
    }

    @Test fun testEqTupleSubType() {
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = a; return a === b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = a; return b === a; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = a; return a !== b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = a; return b !== a; }", "boolean[false]")

        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return a === b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return b === a; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return a !== b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return b !== a; }", "boolean[true]")

        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return a == b; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return b == a; }", "boolean[true]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return a != b; }", "boolean[false]")
        chkEx("{ val a = (123, 'Hello'); val b: (integer?, text?) = (123, 'Hello'); return b != a; }", "boolean[false]")
    }

    @Test fun testIf() {
        chkEx("= if (a) 1 else 2;", true, "int[1]")
        chkEx("= if (a) 1 else 2;", false, "int[2]")
        chkEx("= if (a) 'Hello' else 123;", true, "ct_err:expr_if_restype:[text]:[integer]")
        chkEx("= if (a) 123 else null;", true, "int[123]")
        chkEx("= if (a) 123 else null;", false, "null")
        chkEx("= if (a) (null, 'Hello') else (123, null);", true, "(null,text[Hello])")
        chkEx("= if (a) (null, 'Hello') else (123, null);", false, "(int[123],null)")
        chkEx("= if (a) (null, 'Hello') else (null, 123);", true, "ct_err:expr_if_restype:[(null,text)]:[(null,integer)]")

        chk("if (123) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[integer]")
        chk("if ('Hello') 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[text]")
        chk("if (null) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[null]")
        chk("if (unit()) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[unit]")
        chkEx("{ val x: boolean? = _nullable(true); return if (x) 'A' else 'B'; }", "ct_err:expr_if_cond_type:[boolean]:[boolean?]")
    }

    @Test fun testIfShortCircuit() {
        val code = """
            function f(s: text, v: integer): integer {
               print(s);
               return v;
            }
            query q(a: boolean) = if (a) f('Yes', 123) else f('No', 456);
        """

        chkFull(code, listOf(Rt_BooleanValue.TRUE), "int[123]")
        chkOut("Yes")
        chkFull(code, listOf(Rt_BooleanValue.FALSE), "int[456]")
        chkOut("No")
    }

    @Test fun testIfPrecedence() {
        chk("if (true) 'A' else 'B' + 'C'", "text[A]")
        chk("if (false) 'A' else 'B' + 'C'", "text[BC]")
        chk("if (true) 'A' else if (true) 'B' else 'C' + 'D'", "text[A]")
        chk("if (false) 'A' else if (true) 'B' else 'C' + 'D'", "text[B]")
        chk("if (false) 'A' else if (false) 'B' else 'C' + 'D'", "text[CD]")
    }

    @Test fun testVariables() {
        chkEx("{ val x = integer; return 0; }", "ct_err:expr_novalue:type:[integer]")
        chkEx("{ val x = chain_context; return 0; }", "ct_err:expr_novalue:namespace:[chain_context]")
        chkEx("{ var x: text; x = integer; return 0; }", "ct_err:expr_novalue:type:[integer]")
        chkEx("{ var x: text; x = chain_context; return 0; }", "ct_err:expr_novalue:namespace:[chain_context]")
    }

    @Test fun testNameStructVsLocal() {
        def("struct rec {x:integer;}")
        chkEx("{ return rec(456); }", "rec[x=int[456]]")
        chkEx("{ val rec = 123; return rec(456); }", "rec[x=int[456]]")
        chkEx("{ val rec = 123; return rec; }", "int[123]")
    }

    @Test fun testNameFunctionVsLocal() {
        def("function f(x:integer): integer = x * x;")
        chkEx("{ val f = 123; return f(456); }", "int[207936]")
        chkEx("{ val f = 123; return f; }", "int[123]")
        chkEx("{ val f = 123; return f.to_hex(); }", "text[7b]")
    }

    @Test fun testIsNull() {
        chkEx("{ val x = _nullable_int(123); return x??; }", "boolean[true]")
        chkEx("{ val x = _nullable_int(null); return x??; }", "boolean[false]")
        chkEx("{ val x = 123; return x??; }", "ct_err:unop_operand_type:??:[integer]")
    }

    @Test fun testIntegerOverflow() {
        tst.strictToString = false
        def("function i(x: integer): integer = integer.from_gtv(x.to_gtv());")
        def("function t(x: text): integer = integer(x);")

        @Suppress("LocalVariableName")
        val i63_1 = bigint(2, 63, -1)

        chkIntegerOver2("+", bigint(2, 63, -1), "1")
        chkIntegerOp2("+", bigint(2, 63, -2), "1")
        chkIntegerOver2("+", bigint(2, 63, -2), "2")
        chkIntegerOp2("+", bigint(2, 62, -1), bigint(2, 62))
        chkIntegerOver2("+", bigint(2, 62), bigint(2, 62))
        chkIntegerOver2("+", bigint(-2, 63), "-1")
        chkIntegerOp2("+", bigint(-2, 63, 1), "-1")
        chk("i($i63_1) + 1 - 1", "rt_err:expr:+:overflow:9223372036854775807:1")
        chk("i($i63_1) - 1 + 1", "9223372036854775807")

        chkIntegerOp("-", bigint(-2, 63, 1), "1")
        chkIntegerOver("-", bigint(-2, 63, 1), "2")
        chkIntegerOp("-", "-1", bigint(2, 63, -1))
        chkIntegerOver("-", "-2", bigint(2, 63, -1))
        chkIntegerOp("-", bigint(-2, 62), bigint(2, 62))
        chkIntegerOver("-", bigint(-2, 62), bigint(2, 62, +1))

        chkIntegerOp2("*", bigint(2, 31), bigint(2, 31))
        chkIntegerOver2("*", bigint(2, 31), bigint(2, 32))
        chkIntegerOp2("*", bigint(2, 32, -1), bigint(2, 31))
        chkIntegerOp2("*", bigint(2, 31, -1), bigint(2, 32))
        chkIntegerOp2("*", bigint(2, 61), "2")
        chkIntegerOp2("*", bigint(2, 61), "3")
        chkIntegerOver2("*", bigint(2, 61), "4")
        chkIntegerOver2("*", bigint(2, 62), "2")
        chkIntegerOver2("*", bigint(2, 62), bigint(2, 31))
        chkIntegerOver2("*", bigint(2, 62), bigint(2, 32))
        chkIntegerOver2("*", bigint(2, 62), bigint(2, 61))
        chkIntegerOver2("*", bigint(2, 62), bigint(2, 62))
        chkIntegerOver2("*", bigint(2, 62), bigint(2, 63, -1))
        chkIntegerOver2("*", bigint(2, 63, -1), bigint(2, 63, -1))
    }

    private fun bigint(base: Int, exp: Int, add: Int = 0): String {
        return BigInteger.valueOf(base.toLong())
                .abs()
                .pow(exp)
                .multiply(BigInteger.valueOf(base.sign.toLong()))
                .add(BigInteger.valueOf(add.toLong()))
                .toString()
    }

    private fun chkIntegerOp(op: String, left: String, right: String) {
        val bLeft = BigInteger(left)
        val bRight = BigInteger(right)
        val bExpected = when (op) {
            "+" -> bLeft + bRight
            "-" -> bLeft - bRight
            "*" -> bLeft * bRight
            "/" -> bLeft / bRight
            else -> throw IllegalArgumentException(op)
        }
        val expected = bExpected.toString()
        chk("t('$left') $op t('$right')", expected)
    }

    private fun chkIntegerOp2(op: String, left: String, right: String) {
        chkIntegerOp(op, left, right)
        chkIntegerOp(op, right, left)
    }

    private fun chkIntegerOver(op: String, left: String, right: String) {
        chk("t('$left') $op t('$right')", "rt_err:expr:$op:overflow:$left:$right")
    }

    private fun chkIntegerOver2(op: String, left: String, right: String) {
        chkIntegerOver(op, left, right)
        chkIntegerOver(op, right, left)
    }

    @Test fun testTupleFieldAccess() {
        chkEx("{ val t = (123, 'Hello'); return _type_of(t[0]); }", "text[integer]")
        chkEx("{ val t = (123, 'Hello'); return _type_of(t[1]); }", "text[text]")
        chkEx("{ val t = (123, 'Hello'); return t[0]; }", "int[123]")
        chkEx("{ val t = (123, 'Hello'); return t[1]; }", "text[Hello]")

        chkEx("{ val t = (x = 123, y = 'Hello'); return _type_of(t[0]); }", "text[integer]")
        chkEx("{ val t = (x = 123, y = 'Hello'); return _type_of(t[1]); }", "text[text]")
        chkEx("{ val t = (x = 123, y = 'Hello'); return t[0]; }", "int[123]")
        chkEx("{ val t = (x = 123, y = 'Hello'); return t[1]; }", "text[Hello]")

        chkEx("{ val t = (123, 'Hello'); return t[-1]; }", "ct_err:expr_subscript:tuple:index:-1:2")
        chkEx("{ val t = (123, 'Hello'); return t[2]; }", "ct_err:expr_subscript:tuple:index:2:2")
        chkEx("{ val t = (123, 'Hello'); return t[+1]; }", "text[Hello]")
        chkEx("{ val t = (123, 'Hello'); return t[-0]; }", "int[123]")
        chkEx("{ val t = (123, 'Hello'); return t[0+1]; }", "text[Hello]")
        chkEx("{ val t = (123, 'Hello'); val i = 0; return t[i]; }", "ct_err:expr_subscript:tuple:no_const")

        chkEx("{ val t = (123, 'Hello'); return t[true]; }", "ct_err:expr_subscript_keytype:[integer]:[boolean]")
        chkEx("{ val t = (123, 'Hello'); return t['Bob']; }", "ct_err:expr_subscript_keytype:[integer]:[text]")

        chkEx("{ val t = _nullable((123, 'Hello')); return t[0]; }", "ct_err:expr_subscript_null")
        chkEx("{ val t = _nullable((123, 'Hello')); return t[1]; }", "ct_err:expr_subscript_null")
        chkEx("{ val t = _nullable((123, 'Hello')); return t!![0]; }", "int[123]")
        chkEx("{ val t = _nullable((123, 'Hello')); return t!![1]; }", "text[Hello]")
    }

    @Test fun testNamedArgumentsInSysFunctions() {
        chk("abs(-123)", "int[123]")
        chk("abs(a=-123)", "int[123]")
        chk("abs(x=-123)", "ct_err:expr_call_badargs:[abs]:[x:integer]")
        chk("'hello'.sub(3)", "text[lo]")
        chk("'hello'.sub(start=3)", "text[lo]")
        chk("'hello'.sub(foo=3)", "ct_err:expr_call_badargs:[text.sub]:[foo:integer]")
        chk("'hello'.sub(end=3)", "ct_err:expr_call_badargs:[text.sub]:[end:integer]")
    }

    @Test fun testTypeOfUnit() {
        def("function f() {}")
        chk("_type_of(print())", "text[unit]")
        chk("_type_of(f())", "text[unit]")
    }

    @Test fun testTypeOfSideEffects() {
        def("function f(i: integer) { print('f:'+i); return i; }")
        chk("f(123)", "int[123]")
        chkOut("f:123")
        chk("_type_of(f(123))", "text[integer]")
        chkOut()
    }

    @Test fun testConstantValueEvaluationError() {
        chk("(123,'hello',true)[0]", "int[123]")
        chk("(123,'hello',true)[1/0]", "ct_err:eval_fail:expr:/:div0:1")
        chk("when(0) { 0 -> 123; else -> 456 }", "int[123]")
        chk("when(0) { 1/0 -> 123; else -> 456 }", "ct_err:eval_fail:expr:/:div0:1")
    }

    @Test fun testNamespacePath() {
        def("namespace a.b.c { val x = 123; }")

        chk("a.b.c.x", "int[123]")
        chk("(a).b.c.x", "ct_err:expr_novalue:namespace:[a]")
        chk("(((a).b).c).x", "ct_err:expr_novalue:namespace:[a]")
        chk("(a.b.c).x", "ct_err:expr_novalue:namespace:[a.b.c]")
        chk("(a.b.c.x)", "int[123]")

        chk("a?.b.c.x", "ct_err:expr_novalue:namespace:[a]")
        chk("a.b.c?.x", "ct_err:expr_novalue:namespace:[a.b.c]")
        chk("a?.b?.c?.x", "ct_err:expr_novalue:namespace:[a]")
    }

    @Test fun testMemberFunctionAsValue() {
        chk("'a'.size", "ct_err:expr_novalue:function:[size]")
    }

    @Test fun testImplicitTargetAttrLocalVar() {
        def("struct s { foo: integer; bar: integer; }")
        chkEx("{ val foo = 123; return s(foo, bar = 456); }", "s[foo=int[123],bar=int[456]]")
    }

    @Test fun testImplicitTargetAttrAtEntity() {
        tstCtx.useSql = true
        def("entity data { v: integer; }")
        def("struct s { foo: data; bar: data; }")
        insert("c0.data", "v", "10,123", "11,456", "12,789")
        chk("(foo:data) @ {123} ( s(foo, bar = data@{789}) )", "s[foo=data[10],bar=data[12]]")
        chk("(bar:data) @ {123} ( s(foo = data@{789}, bar) )", "s[foo=data[12],bar=data[10]]")
    }

    @Test fun testImplicitTargetAttrAtItemAttrDbAt() {
        tstCtx.useSql = true
        def("entity data { foo: integer; bar: integer; }")
        def("struct s { foo: integer; bar: integer; }")
        insert("c0.data", "foo,bar", "10,123,456")
        chk("data @ {} ( s(.foo, .bar) )", "s[foo=int[123],bar=int[456]]")
        chk("data @ {} ( s($.foo, $.bar) )", "s[foo=int[123],bar=int[456]]")
        chk("data @ {} ( s(data.foo, data.bar) )", "s[foo=int[123],bar=int[456]]")
        chk("(x:data) @ {} ( s(x.foo, x.bar) )", "s[foo=int[123],bar=int[456]]")
    }

    @Test fun testImplicitTargetAttrAtItemAttrColAt() {
        def("struct s { foo: integer; bar: integer; }")
        def("function data() = [(foo = 123, bar = 456)];")
        chk("data() @ {} ( s(.foo, .bar) )", "s[foo=int[123],bar=int[456]]")
        chk("data() @ {} ( s($.foo, $.bar) )", "s[foo=int[123],bar=int[456]]")
        chk("(x:data()) @ {} ( s(x.foo, x.bar) )", "s[foo=int[123],bar=int[456]]")
        chkEx("{ val t = s(foo = 123, bar = 456); return s(t.foo, t.bar); }", "ct_err:attr_implic_multi:0:foo,bar")
        chkEx("{ val t = s(foo = 123, bar = 456); return s(t.foo, bar = 789); }", "ct_err:attr_implic_multi:0:foo,bar")
    }

    @Test fun testImplicitTargetAttrGlobalConstant() {
        def("val foo = 123;")
        def("namespace ns { val bar = 456; }")
        def("struct s { foo: integer; bar: integer; }")
        chk("s(foo, ns.bar)", "s[foo=int[123],bar=int[456]]")
        chk("s(foo = 789, ns.bar)", "s[foo=int[789],bar=int[456]]")
        chk("s(foo, bar = 789)", "s[foo=int[123],bar=int[789]]")
    }

    @Test fun testImplicitTargetAttrSmartNullable() {
        def("struct s { foo: integer; bar: integer; }")
        chkEx("{ val foo = _nullable_int(123); return s(foo, bar = 456); }", "ct_err:attr_bad_type:0:foo:integer:integer?")
        chkEx("{ val foo = _nullable_int(123); return if (foo == null) null else s(foo, bar = 456); }",
            "s[foo=int[123],bar=int[456]]")
        chkEx("{ val bar = _nullable_int(456); return if (bar == null) null else s(foo = 123, bar); }",
            "s[foo=int[123],bar=int[456]]")
    }
}
