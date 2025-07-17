/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class StatementTest: BaseRellTest() {
    @Test fun testVal() {
        chkEx("{ val x = 123; return x; }", "int[123]")
        chkEx("{ val x = 123; x = 456; return x; }", "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer = 123; return x; }", "int[123]")
        chkEx("{ val x: text = 123; return 0; }", "ct_err:stmt_var_type:x:[text]:[integer]")
        chkEx("{ val x: integer = 'Hello'; return 0; }", "ct_err:stmt_var_type:x:[integer]:[text]")
        chkEx("{ val x = unit(); return 123; }", "ct_err:stmt_var_unit:x")
        chkEx("{ val x: integer; x = 123; return x; }", "int[123]")
        chkEx("{ val x: integer; x = 123; x = 456; return x; }", "ct_err:expr_assign_val:x")
    }

    @Test fun testVar() {
        chkEx("{ var x = 123; return x; }", "int[123]")
        chkEx("{ var x = 123; x = 456; return x; }", "int[456]")
        chkEx("{ var x; return 0; }", "ct_err:unknown_name:x")
        chkEx("{ var x: integer = 123; return x; }", "int[123]")
        chkEx("{ var x: integer; x = 123; return x; }", "int[123]")
        chkEx("{ var x: integer; x = 'Hello'; return 0; }", "ct_err:stmt_assign_type:[integer]:[text]")
        chkEx("{ var x: text; x = 123; return 0; }", "ct_err:stmt_assign_type:[text]:[integer]")
        chkEx("{ var x: integer = 'Hello'; return 0; }", "ct_err:stmt_var_type:x:[integer]:[text]")
        chkEx("{ var x: text = 123; return 0; }", "ct_err:stmt_var_type:x:[text]:[integer]")
        chkEx("{ var x = 123; x = 'Hello'; return x; }", "ct_err:stmt_assign_type:[integer]:[text]")
        chkEx("{ var x = unit(); return 123; }", "ct_err:stmt_var_unit:x")
    }

    @Test fun testVarValUnderscore() {
        chkCompile("function f(){ var _; }", "ct_err:unknown_name_type:_")
        chkCompile("function f(){ val _; }", "ct_err:unknown_name_type:_")
        chkCompile("struct _ {} function f(){ var _; }", "ct_err:unknown_name_type:_")
        chkCompile("struct _ {} function f(){ val _; }", "ct_err:unknown_name_type:_")
    }

    @Test fun testNameConflict() {
        chkEx("{ val x = 123; val x = 456; return 0; }", "ct_err:block:name_conflict:x")
        chkEx("{ val x = 123; var x = 456; return 0; }", "ct_err:block:name_conflict:x")
        chkEx("{ var x = 123; val x = 456; return 0; }", "ct_err:block:name_conflict:x")
        chkEx("{ var x = 123; var x = 456; return 0; }", "ct_err:block:name_conflict:x")
        chkEx("{ val x = 123; { val x = 456; } return 789; }", "ct_err:block:name_conflict:x")
        chkEx("{ val x = 123; if (2 > 1) { val x = 456; } return 789; }", "ct_err:block:name_conflict:x")

        chkEx("{ { val x = 123; } val x = 456; return x; }", "int[456]")
        chkEx("{ { val x = 123; } return x; }", "ct_err:unknown_name:x")
    }

    @Test fun testIf() {
        chkEx("{ if (true) return 123; return 456; }", "int[123]")
        chkEx("{ if (false) return 123; return 456; }", "int[456]")

        chkEx("{ if ('Hello') return 123; return 456; }", "ct_err:stmt_if_expr_type:[boolean]:[text]")
        chkEx("{ if (555) return 123; return 456; }", "ct_err:stmt_if_expr_type:[boolean]:[integer]")

        chkEx("{ if (a == 0) return 123; return 456; }", 0, "int[123]")
        chkEx("{ if (a == 0) return 123; return 456; }", 1, "int[456]")

        // Chain of ifs
        val code = "{ if (a == 0) return 111; else if (a == 1) return 222; else if (a == 2) return 333; else return 444; }"
        chkEx(code, 0, "int[111]")
        chkEx(code, 1, "int[222]")
        chkEx(code, 2, "int[333]")
        chkEx(code, 3, "int[444]")

        // Dangling else
        chkEx("{ if (false) if (false) return 123; else return 456; return 789; }", "int[789]")
        chkEx("{ if (false) if (true) return 123; else return 456; return 789; }", "int[789]")
        chkEx("{ if (true) if (false) return 123; else return 456; return 789; }", "int[456]")
        chkEx("{ if (true) if (true) return 123; else return 456; return 789; }", "int[123]")
    }

    @Test fun testWhile() {
        val code = """{
            var s = '';
            var i = 0;
            while (i < 5) {
                s = s + i.str();
                i = i + 1;
            }
            return s;
        }"""

        chkEx(code, "text[01234]")
    }

    @Test fun testWhileBreak() {
        val code = """{
            var s = '';
            var i = 0;
            while (i < 10) {
                s = s + i.str();
                i = i + 1;
                if (i >= 5) break;
            }
            return s;
        }"""

        chkEx(code, "text[01234]")
    }

    @Test fun testWhileContinue() {
        tst.strictToString = false

        val code = """{
            val res = list<text>();

            var i = 0;
            while (i < 6) {
                res.add('i=' + i);
                val r = i % 3;
                ++i;
                if (r == 0) continue;
                res.add('r=' + r);
            }

            return res;
        }"""

        chkEx(code, "[i=0, i=1, r=1, i=2, r=2, i=3, i=4, r=1, i=5, r=2]")
    }

    @Test fun testWhileBreakComplex() {
        val code = """{
            var s = '';
            var i = 0;
            while (i < 10) {
                var j = 0;
                while (j < 10) {
                    s = s + i.str() + ',' + j.str() + ' ';
                    j = j + 1;
                    if (j >= 5) break;
                }
                i = i + 1;
                if (i >= 3) break;
            }
            return s;
        }"""

        chkEx(code, "text[0,0 0,1 0,2 0,3 0,4 1,0 1,1 1,2 1,3 1,4 2,0 2,1 2,2 2,3 2,4 ]")
    }

    @Test fun testBreakWithoutLoop() {
        chkEx("{ break; return 123; }", "ct_err:stmt_break_noloop")
        chkEx("{ if (2 > 1) break; return 123; }", "ct_err:stmt_break_noloop")
    }

    @Test fun testContinueWithoutLoop() {
        chkEx("{ continue; return 123; }", "ct_err:stmt_continue_noloop")
        chkEx("{ if (2 > 1) continue; return 123; }", "ct_err:stmt_continue_noloop")
    }

    @Test fun testFor() {
        tstCtx.useSql = true
        def("entity user { name: text; }")
        chkOp("create user('Bob'); create user('Alice'); create user('Trudy');")

        val code = """{
            var s = '';
            for (name in user @* {}.name) {
                if (s.size() > 0) s = s + ',';
                s = s + name;
            }
            return s;
        }"""

        chkEx(code, "text[Bob,Alice,Trudy]")
    }

    @Test fun testForBreak() {
        tstCtx.useSql = true
        def("entity user { name: text; }")
        chkOp("create user('Bob'); create user('Alice'); create user('Trudy');")

        val code = """{
            var s = '';
            var n = 0;
            for (name in user @* {}.name) {
                if (s.size() > 0) s = s + ',';
                s = s + name;
                n = n + 1;
                if (n >= 2) break;
            }
            return s;
        }"""

        chkEx(code, "text[Bob,Alice]")
    }

    @Test fun testForContinue() {
        tst.strictToString = false

        val code = """{
            val res = list<text>();
            for (i in range(6)) {
                res.add('i=' + i);
                val r = i % 3;
                if (r == 0) continue;
                res.add('r=' + r);
            }
            return res;
        }"""

        chkEx(code, "[i=0, i=1, r=1, i=2, r=2, i=3, i=4, r=1, i=5, r=2]")
    }

    @Test fun testForReturn() {
        tstCtx.useSql = true
        def("entity user { name: text; }")
        chkOp("create user('Bob'); create user('Alice'); create user('Trudy');")

        val code = """{
            for (i in range(10)) {
                print(i);
                if (i == 3) return 123;
            }
            return 456;
        }"""

        chkEx(code, "int[123]")
        chkOut("0", "1", "2", "3")
    }

    @Test fun testForRange() {
        chkOp("for (i in range(5)) print(i);")
        chkOut("0", "1", "2", "3", "4")

        chkOp("for (i in range(5, 0, -1)) print(i);")
        chkOut("5", "4", "3", "2", "1")

        chkOp("for (i in range(0)) print(i);")
        chkOut()

        chkOp("for (i in range(1000,1000)) print(i);")
        chkOut()

        chkOp("for (i in range(9223372036854775800,9223372036854775807,5)) print(i);")
        chkOut("9223372036854775800", "9223372036854775805")

        chkOp("for (i in range(-9223372036854775807-1,9223372036854775807,9223372036854775807)) print(i);")
        chkOut("-9223372036854775808", "-1", "9223372036854775806")

        chkOp("for (i in range(9223372036854775807,-9223372036854775807-1,-9223372036854775807)) print(i);")
        chkOut("9223372036854775807", "0", "-9223372036854775807")

        chkOp("for (i in range(9223372036854775807,-9223372036854775807-1,-9223372036854775807-1)) print(i);")
        chkOut("9223372036854775807", "-1")
    }

    @Test fun testEmptyStatement() {
        chkEx("{ ;;;;; return 123; }", "int[123]")
    }

    @Test fun testEmptyBlock() {
        chkEx("{ if (1 > 0) {} else {}; return 123; }", "int[123]")
    }

    @Test fun testAssignment() {
        chkEx("{ var x = 123; x += 456; return x; }", "int[579]")
        chkEx("{ var x = 123; x -= 456; return x; }", "int[-333]")
        chkEx("{ var x = 123; x *= 456; return x; }", "int[56088]")
        chkEx("{ var x = 123456; x /= 789; return x; }", "int[156]")
        chkEx("{ var x = 123456; x %= 789; return x; }", "int[372]")

        chkEx("{ var x = 'Hello'; x += 'World'; return x; }", "text[HelloWorld]")
        chkEx("{ var x = 'Hello'; x += 12345; return x; }", "text[Hello12345]")
        chkEx("{ var x = 'Hello'; x += true; return x; }", "text[Hellotrue]")
        chkEx("{ var x = 'Hello'; x += x'1234ABCD'; return x; }", "text[Hello0x1234abcd]")
    }

    @Test fun testAssignmentErr() {
        chkEx("{ var x = true; x += false; return x; }", "ct_err:binop_operand_type:+=:[boolean]:[boolean]")
        chkEx("{ var x = true; x += 123; return x; }", "ct_err:binop_operand_type:+=:[boolean]:[integer]")
        chkEx("{ var x = true; x += 'Hello'; return x; }", "ct_err:binop_operand_type:+=:[boolean]:[text]")
        chkEx("{ var x = 123; x += false; return x; }", "ct_err:binop_operand_type:+=:[integer]:[boolean]")
        chkEx("{ var x = 123; x += 'Hello'; return x; }", "ct_err:binop_operand_type:+=:[integer]:[text]")

        chkEx("{ var x = 123; +x = 456; return x; }", "ct_err:syntax")
        chkEx("{ var x = 123; +x += 456; return x; }", "ct_err:syntax")

        chkAssignmentErr("-=")
        chkAssignmentErr("*=")
        chkAssignmentErr("/=")
        chkAssignmentErr("%=")
    }

    private fun chkAssignmentErr(op: String) {
        chkEx("{ var x = true; x $op false; return x; }", "ct_err:binop_operand_type:$op:[boolean]:[boolean]")
        chkEx("{ var x = true; x $op 123; return x; }", "ct_err:binop_operand_type:$op:[boolean]:[integer]")
        chkEx("{ var x = true; x $op 'Hello'; return x; }", "ct_err:binop_operand_type:$op:[boolean]:[text]")
        chkEx("{ var x = 123; x $op false; return x; }", "ct_err:binop_operand_type:$op:[integer]:[boolean]")
        chkEx("{ var x = 123; x $op 'Hello'; return x; }", "ct_err:binop_operand_type:$op:[integer]:[text]")
        chkEx("{ var x = 'Hello'; x $op false; return x; }", "ct_err:binop_operand_type:$op:[text]:[boolean]")
        chkEx("{ var x = 'Hello'; x $op 123; return x; }", "ct_err:binop_operand_type:$op:[text]:[integer]")
        chkEx("{ var x = 'Hello'; x $op 'Hello'; return x; }", "ct_err:binop_operand_type:$op:[text]:[text]")
    }

    @Test fun testComplexListAssignment() {
        val code = """
            function f(x: list<integer>): list<integer> {
                for (i in range(5)) x.add(i);
                return x;
            }
        """
        chkFull("$code query q() { val p = list<integer>(); f(p)[2] = 777; return ''+p; }", listOf(), "text[[0, 1, 777, 3, 4]]")
    }

    @Test fun testComplexListAssignment2() {
        val code = """
            query q(x: integer, y: integer): text {
                val p = list<list<integer>>();
                for (i in range(3)) {
                    p.add(list<integer>());
                    for (j in range(3)) {
                        p[i].add(i * 10 + j);
                    }
                }
                p[x][y] = 777;
                return ''+p;
            }
        """

        chkFull(code, listOf(Rt_IntValue.get(0), Rt_IntValue.get(0)), "text[[[777, 1, 2], [10, 11, 12], [20, 21, 22]]]")
        chkFull(code, listOf(Rt_IntValue.get(1), Rt_IntValue.get(1)), "text[[[0, 1, 2], [10, 777, 12], [20, 21, 22]]]")
        chkFull(code, listOf(Rt_IntValue.get(2), Rt_IntValue.get(2)), "text[[[0, 1, 2], [10, 11, 12], [20, 21, 777]]]")
    }

    @Test fun testCallChain() {
        val code = """
            function f(x: integer): map<integer, text> = [x:'Bob',x*2:'Alice'];
            query q(i: integer)
        """

        chkFull("$code = f(123);", listOf(Rt_IntValue.get(0)), "map<integer,text>[int[123]=text[Bob],int[246]=text[Alice]]")
        chkFull("$code = f(123).values();", listOf(Rt_IntValue.get(0)), "list<text>[text[Bob],text[Alice]]")
        chkFull("$code = f(123).values().get(i);", listOf(Rt_IntValue.get(0)), "text[Bob]")
        chkFull("$code = f(123).values().get(i);", listOf(Rt_IntValue.get(1)), "text[Alice]")
        chkFull("$code = f(123).values().get(i).upper_case();", listOf(Rt_IntValue.get(0)), "text[BOB]")
        chkFull("$code = f(123).values().get(i).upper_case();", listOf(Rt_IntValue.get(1)), "text[ALICE]")
        chkFull("$code = f(123).values().get(i).upper_case().size();", listOf(Rt_IntValue.get(0)), "int[3]")
        chkFull("$code = f(123).values().get(i).upper_case().size();", listOf(Rt_IntValue.get(1)), "int[5]")
    }

    @Test fun testUninitializedVar() {
        chkEx("{ var x: integer; return x; }", "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; x = 123; return x; }", "int[123]")
        chkEx("{ var x: integer; x = 123; x = 456; return x; }", "int[456]")
        chkEx("{ var x: integer = 123; x = 456; return x; }", "int[456]")

        chkEx("{ var x: integer; if (a) x = 123; else x = 456; return x; }", true, "int[123]")
        chkEx("{ var x: integer; if (a) x = 123; else x = 456; return x; }", false, "int[456]")
        chkEx("{ var x: integer; if (a) x = 123; return x; }", true, "ct_err:expr_var_uninit:x")

        chkEx("{ var x: integer; if (a) { x = 123; return x; } return 456; }", true, "int[123]")
        chkEx("{ var x: integer; if (a) { x = 123; return x; } return 456; }", false, "int[456]")
        chkEx("{ var x: integer; if (a) { x = 123; } return x; }", false, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { return x; } return 456; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { x = 123; } x = 456; return x; }", true, "int[456]")
        chkEx("{ var x: integer; if (a) { x = 123; } x = 456; return x; }", false, "int[456]")
        chkEx("{ var x: integer; if (a) { return x; } x = 456; return x; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { return 123; } x = 456; return x; }", true, "int[123]")
        chkEx("{ var x: integer; if (a) { return 123; } x = 456; return x; }", false, "int[456]")

        chkEx("{ var x: integer; if (a) { return 123; } else { x = 456; } return x; }", true, "int[123]")
        chkEx("{ var x: integer; if (a) { return 123; } else { x = 456; } return x; }", false, "int[456]")
        chkEx("{ var x: integer; if (a) { x = 123; } else { return 456; } return x; }", true, "int[123]")
        chkEx("{ var x: integer; if (a) { x = 123; } else { return 456; } return x; }", false, "int[456]")

        chkEx("{ var x: integer; x += 5; return x; }", "ct_err:[expr_var_uninit:x][expr_var_uninit:x]")
        chkEx("{ var x: integer; x += 5; return 0; }", "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { x = 0; } x += 5; return 0; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { x = 0; } return x; }", true, "ct_err:expr_var_uninit:x")

        chkEx("{ var x: integer; x++; return x; }", "ct_err:[expr_var_uninit:x][expr_var_uninit:x]")
        chkEx("{ var x: integer; x++; return 0; }", "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { x = 0; } x++; return 0; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; ++x; return 0; }", "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; if (a) { x = 0; } ++x; return 0; }", true, "ct_err:expr_var_uninit:x")

        chkEx("{ var x: integer = 123; if (a) { x = 456; } return x; }", false, "int[123]")
        chkEx("{ var x: integer = 123; if (a) { x = 456; } return x; }", true, "int[456]")
        chkEx("{ var x: integer = 123; { if (a) { x = 456; } return x; } }", false, "int[123]")
        chkEx("{ var x: integer = 123; { if (a) { x = 456; } return x; } }", true, "int[456]")
    }

    @Test fun testUninitializedVarWhen() {
        run {
            val code = "{ var x: integer; when(a) { 0 -> return 123; 1 -> x = 456; 2 -> return 789; else -> x = 987; } return x; }"
            chkEx(code, 0, "int[123]")
            chkEx(code, 1, "int[456]")
            chkEx(code, 2, "int[789]")
            chkEx(code, 3, "int[987]")
        }

        run {
            val code = "{ var x: integer; when(a) { 0 -> x = 123; 1 -> x = 456; else -> return 789; } return x; }"
            chkEx(code, 0, "int[123]")
            chkEx(code, 1, "int[456]")
            chkEx(code, 2, "int[789]")
        }

        chkEx("{ var x: integer; when(a) { 0 -> x = 123; 1 -> x = 456; } return x; }", 0, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; when(a) { true -> x = 123; false -> x = 456; } return x; }", true, "int[123]")
        chkEx("{ var x: integer; when(a) { true -> x = 123; false -> x = 456; } return x; }", false, "int[456]")
        chkEx("{ var x: integer; when(a) { 0 -> print(x); 1 -> x = 456; else -> return 789; } return x; }", 0,
                "ct_err:[expr_var_uninit:x][expr_var_uninit:x]")
    }

    @Test fun testUninitializedVarLoop() {
        chkEx("{ var x: integer; for (v in range(1)) { x = 123; } return x; }", "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; for (v in range(a)) { x = 123; return x; } return 456; }", 0, "int[456]")
        chkEx("{ var x: integer; for (v in range(a)) { x = 123; return x; } return 456; }", 1, "int[123]")

        chkEx("{ var x: integer; while(a) { x = 123; } return x; }", false, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; while(a) { x = 123; return x; } return x; }", false, "ct_err:expr_var_uninit:x")
        chkEx("{ var x: integer; while(a) { x = 123; return x; } return 456; }", false, "int[456]")
        chkEx("{ var x: integer; while(a) { x = 123; return x; } return 456; }", true, "int[123]")

        chkEx("{ val x: integer; for (v in range(0)) { x = 123; } return 0; }", "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; for (v in range(1)) { x = 123; } return 0; }", "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; for (v in range(a)) { x = 123; return 123; } return 456; }", 0, "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; for (v in range(1)) { if (a) { x = 123; } } return 456; }", false, "ct_err:expr_assign_val:x")

        chkEx("{ val x: integer; while(a) { x = 123; } return 0; }", false, "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; while(a) { x = 123; return x; } return 0; }", false, "ct_err:[expr_assign_val:x][expr_var_uninit:x]")
        chkEx("{ val x: integer; while(a) { x = 123; return 0; } return 456; }", false, "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; while(a) { x = 123; return 0; } return 456; }", true, "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; while(a) { x = 123; return x; } return 456; }", true, "ct_err:[expr_assign_val:x][expr_var_uninit:x]")
        chkEx("{ val x: integer; while (false) { if (a) { x = 123; } } return 456; }", false, "ct_err:expr_assign_val:x")

        chkEx("{ var x: boolean; while(x) { x = false; } return 0; }", "ct_err:expr_var_uninit:x")
    }

    @Test fun testUninitializedVal() {
        chkEx("{ val x: integer; return x; }", "ct_err:expr_var_uninit:x")
        chkEx("{ val x: integer; x = 123; return x; }", "int[123]")
        chkEx("{ val x: integer; x = 123; x = 456; return x; }", "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer = 123; x = 456; return x; }", "ct_err:expr_assign_val:x")

        chkEx("{ val x: integer; if (a) x = 123; else x = 456; return x; }", true, "int[123]")
        chkEx("{ val x: integer; if (a) x = 123; else x = 456; return x; }", false, "int[456]")
        chkEx("{ val x: integer; if (a) x = 123; return x; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ val x: integer = 123; if (a) x = 456; else x = 789; return x; }", true, "ct_err:[expr_assign_val:x][expr_assign_val:x]")
        chkEx("{ val x: integer = 123; if (a) x = 456; return x; }", true, "ct_err:expr_assign_val:x")

        chkEx("{ val x: integer; if (a) { x = 123; return x; } return 456; }", true, "int[123]")
        chkEx("{ val x: integer; if (a) { x = 123; return x; } return 456; }", false, "int[456]")
        chkEx("{ val x: integer; if (a) { x = 123; } return x; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ val x: integer; if (a) { return x; } return 456; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ val x: integer; if (a) { x = 123; } x = 456; return 0; }", true, "ct_err:expr_assign_val:x")
        chkEx("{ val x: integer; if (a) { x = 123; return x; } x = 456; return x; }", true, "int[123]")
        chkEx("{ val x: integer; if (a) { x = 123; return x; } x = 456; return x; }", false, "int[456]")
        chkEx("{ val x: integer; if (a) { x = 123; return x; } else {} x = 456; return x; }", true, "int[123]")
        chkEx("{ val x: integer; if (a) { x = 123; return x; } else {} x = 456; return x; }", false, "int[456]")
        chkEx("{ var x: integer; if (a) { return x; } x = 456; return x; }", true, "ct_err:expr_var_uninit:x")
        chkEx("{ val x: integer; if (a) { return 123; } x = 456; return x; }", true, "int[123]")
        chkEx("{ val x: integer; if (a) { return 123; } x = 456; return x; }", false, "int[456]")
    }

    @Test fun testDeadCode() {
        chkEx("{ return 123; return 456; }", "ct_err:stmt_deadcode")
        chkEx("{ if (a) return 123; else return 456; return 789; }", true, "ct_err:stmt_deadcode")
        chkEx("{ if (a) { return 123; } else { return 456; } return 789; }", true, "ct_err:stmt_deadcode")
        chkEx("{ if (a) { return 123; print('Hello'); } return 456; }", true, "ct_err:stmt_deadcode")
        chkEx("{ return 123; return 456; return 789; return 0; }", "ct_err:stmt_deadcode")
    }

    // Make sure that parser's time complexity is not O(N^2) - there was such a bug. Execution time shall be ~3s.
    @Test fun testParserTimeComplexityTest() {
        val n = 20000
        for (i in 0 until n) def("function f_$i(): integer = $i;")
        chk("f_${n-1}()", "int[${n-1}]")
    }

    @Test fun testTypeFormsVar() {
        def("namespace ns { struct data { x: integer; } }")
        chkEx("{ var x: integer; return _type_of(x); }", "text[integer]")
        chkEx("{ var x; return _type_of(x); }", "ct_err:unknown_name:x")
        chkEx("{ var integer; return _type_of(integer); }", "text[integer]")
        chkEx("{ var x = 123; return _type_of(x); }", "text[integer]")
        chkEx("{ var ns.data; return _type_of(data); }", "text[ns.data]")
        chkEx("{ var integer = 'hello'; return _type_of(integer); }", "text[text]")
    }

    @Test fun testTypeFormsStruct() {
        def("namespace ns { struct data { x: integer; } }")
        chkTypeFormsStruct("struct s { x: integer; }", "z.x", "text[integer]")
        chkTypeFormsStruct("struct s { x; }", "z.x", "ct_err:unknown_name:x")
        chkTypeFormsStruct("struct s { integer; }", "z.integer", "text[integer]")
        chkTypeFormsStruct("struct s { x = 123; }", "z.x", "ct_err:unknown_name:x")
        chkTypeFormsStruct("struct s { ns.data; }", "z.data", "text[ns.data]")
        chkTypeFormsStruct("struct s { integer = 'hello'; }", "z.integer", "ct_err:attr_type:integer:[integer]:[text]")
    }

    private fun chkTypeFormsStruct(def: String, expr: String, expected: String) {
        chkFull("$def query q() { var z: s; return _type_of($expr); }", expected)
    }
}
