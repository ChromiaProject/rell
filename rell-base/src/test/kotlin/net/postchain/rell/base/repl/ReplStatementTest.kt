/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class ReplStatementTest: BaseRellTest() {
    @Test fun testSimple() {
        repl.chk("print(123);", "OUT:123", "RES:unit")
        repl.chk("for (x in range(3)) print(x);", "OUT:0", "OUT:1", "OUT:2")
        repl.chk(";;;")
    }

    @Test fun testVal() {
        repl.chk("val x = 123; print(x);", "OUT:123", "RES:unit")

        repl.chk("val x = 123;", "CTE:<console>:block:name_conflict:x")
        repl.chk("x", "RES:int[123]")

        repl.chk("val y = 456;")
        repl.chk("y", "RES:int[456]")
        repl.chk("x", "RES:int[123]")
        repl.chk("x + y", "RES:int[579]")
    }

    @Test fun testVar() {
        repl.chk("var x = 123; print(x);", "OUT:123", "RES:unit")
        repl.chk("var y = 456;")
        repl.chk("print(y)", "OUT:456", "RES:unit")
        repl.chk("x = 789;")
        repl.chk("x", "RES:int[789]")
        repl.chk("y += x;")
        repl.chk("y", "RES:int[1245]")
    }

    @Test fun testVarRuntimeError() {
        repl.chk("var x: integer = 123 / (1 - 1);", "rt_err:expr:/:div0:123")
        repl.chk("x", "CTE:<console>:unknown_name:x")
        repl.chk("x = 123;", "CTE:<console>:unknown_name:x")
        repl.chk("var x: integer = 456;")
        repl.chk("x", "RES:int[456]")
    }

    @Test fun testNestedBlock() {
        repl.chk("var x = 123;")
        repl.chk("{ var p = 888; var q = 999; print(p + q); }", "OUT:1887")
        repl.chk("p", "CTE:<console>:unknown_name:p")
        repl.chk("x", "RES:int[123]")
        repl.chk("var y = 456;")
        repl.chk("y", "RES:int[456]")
    }

    @Test fun testForwardDefs() {
        repl.chk("print(f(123)); function f(x: integer): integer = x * x;", "OUT:15129", "RES:unit")
        repl.chk("print(rec()); struct rec { x: integer = 123; };", "OUT:rec{x=123}", "RES:unit")
    }

    @Test fun testMultiDefStmt() {
        repl.chk("""
            function f(x: integer): integer = g(x + 1);
            print(f(123));
            function g(x: integer): integer = x * 3;
            print(g(456));
        """, "OUT:372", "RES:unit", "OUT:1368", "RES:unit")
    }

    @Test fun testDisallowedStatements() {
        repl.chk("break;", "CTE:<console>:stmt_break_noloop")
        repl.chk("return;", "CTE:<console>:stmt_return_disallowed:REPL")
        repl.chk("return 123;", "CTE:<console>:stmt_return_disallowed:REPL")
    }

    @Test fun testExpressions() {
        repl.chk("2 + 2;", "RES:int[4]")
        repl.chk("integer.MAX_VALUE;", "RES:int[9223372036854775807]")
        repl.chk("print(123);", "OUT:123", "RES:unit")
        repl.chk("max(123, 456);", "RES:int[456]")
    }

    @Test fun testAssignmentComplex() {
        repl.chk("val x = [1, 2, 3];")
        repl.chk("x[abs(123)-min(123,456)+1] = 100;")
        repl.chk("x", "RES:list<integer>[int[1],int[100],int[3]]")
    }

    @Test fun testNullability() {
        repl.chk("val x: integer? = _nullable_int(123);")
        repl.chk("x + 1", "CTE:<console>:binop_operand_type:+:[integer?]:[integer]")
        repl.chk("print(x + 1);", "CTE:<console>:binop_operand_type:+:[integer?]:[integer]")
        repl.chk("x!!", "RES:int[123]")
        repl.chk("x + 1", "RES:int[124]")
        repl.chk("print(x + 1);", "OUT:124", "RES:unit")
    }

    @Test fun testNullability2() {
        repl.chk("val x: integer? = _nullable_int(123);")
        repl.chk("x + 1", "CTE:<console>:binop_operand_type:+:[integer?]:[integer]")
        repl.chk("print(x + 1);", "CTE:<console>:binop_operand_type:+:[integer?]:[integer]")
        repl.chk("print(x!!);", "OUT:123", "RES:unit")
        repl.chk("x + 1", "RES:int[124]")
        repl.chk("print(x + 1);", "OUT:124", "RES:unit")
    }

    @Test fun testNullability3() {
        repl.chk("val x: integer? = _nullable_int(123);")
        repl.chk("_type_of(x)", "RES:text[integer?]")
        repl.chk("x!!", "RES:int[123]")
        repl.chk("_type_of(x)", "RES:text[integer]")
    }

    @Test fun testInnerExpr() {
        repl.chk("struct data { mutable x: integer = 0; }")
        repl.chk("function inc(d: data): integer { return d.x++; }")
        repl.chk("val d = data();")
        repl.chk("d.x", "RES:int[0]")
        repl.chk("inc(d);", "RES:int[0]")
        repl.chk("inc(d)", "RES:int[1]")
        repl.chk("inc(d); inc(d);", "RES:int[2]", "RES:int[3]")
        repl.chk("if (true) inc(d);")
        repl.chk("for (x in range(5)) inc(d);")
        repl.chk("d", "RES:data[x=int[10]]")
    }

    @Test fun testStmtVsExprIf() {
        repl.chk("if (2 > 1) 'Yes' else 'No'", "RES:text[Yes]")
        repl.chk("if (2 < 1) 'Yes' else 'No'", "RES:text[No]")

        repl.chk("if (2 > 1) print('Yes'); else print('No');", "OUT:Yes")
        repl.chk("if (2 < 1) print('Yes'); else print('No');", "OUT:No")
        repl.chk("if (2 > 1) print('Yes') else print('No')", "CTE:<console>:expr_if_unit")
        repl.chk("if (2 < 1) print('Yes') else print('No')", "CTE:<console>:expr_if_unit")

        repl.chk("if (2 > 1) abs(123); else abs(-456);")
        repl.chk("if (2 < 1) abs(123); else abs(-456);")
        repl.chk("if (2 > 1) abs(123) else abs(-456)", "RES:int[123]")
        repl.chk("if (2 < 1) abs(123) else abs(-456)", "RES:int[456]")
    }

    @Test fun testStmtVsExprWhen() {
        repl.chk("when { 2 > 1 -> 'Yes'; else -> 'No' }", "RES:text[Yes]")
        repl.chk("when { 2 < 1 -> 'Yes'; else -> 'No' }", "RES:text[No]")

        repl.chk("when { 2 > 1 -> print('Yes'); else -> print('No'); };", "OUT:Yes")
        repl.chk("when { 2 < 1 -> print('Yes'); else -> print('No'); };", "OUT:No")

        repl.chk("when { 2 > 1 -> print('Yes'); else -> print('No') }",
                "CTE:<console>:when_exprtype_unit, CTE:<console>:when_exprtype_unit")
        repl.chk("when { 2 < 1 -> print('Yes'); else -> print('No') }",
                "CTE:<console>:when_exprtype_unit, CTE:<console>:when_exprtype_unit")

        repl.chk("when { 2 > 1 -> abs(123); else -> abs(-456); }", "")
        repl.chk("when { 2 < 1 -> abs(123); else -> abs(-456); }", "")
        repl.chk("when { 2 > 1 -> abs(123); else -> abs(-456) }", "RES:int[123]")
        repl.chk("when { 2 < 1 -> abs(123); else -> abs(-456) }", "RES:int[456]")
    }

    @Test fun testStmtVsExprIncrement() {
        repl.chk("var x: integer = 0;")
        repl.chk("x++", "RES:int[0]")
        repl.chk("x++;", "RES:int[1]")
        repl.chk("++x", "RES:int[3]")
        repl.chk("++x;", "RES:int[4]")
        repl.chk("x--", "RES:int[4]")
        repl.chk("x--;", "RES:int[3]")
        repl.chk("--x", "RES:int[1]")
        repl.chk("--x;", "RES:int[0]")
    }

    @Test fun testMixExpressions() {
        val code = """
            if (true) print(1);
            2 + 2;
            if (true) print(2);
            3 + 3;
            if (true) print(3);
            4 + 4
        """
        repl.chk(code, "OUT:1", "RES:int[4]", "OUT:2", "RES:int[6]", "OUT:3", "RES:int[8]")

        repl.chk("if (true) print(1); 2 + 2;", "OUT:1", "RES:int[4]")
    }

    @Test fun testInvalidCommand() {
        repl.chk("\\xyz", "CTE:repl:invalid_command:\\xyz")
    }
}
