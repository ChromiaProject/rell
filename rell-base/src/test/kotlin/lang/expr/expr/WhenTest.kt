/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class WhenTest: BaseRellTest() {
    @Test fun testExprSimple() {
        val code = "= when(a) { 0 -> 'A'; 1 -> 'B'; 2 -> 'C'; else -> '?'; };"
        chkWhen("integer", code, "0" to "text[A]", "1" to "text[B]", "2" to "text[C]", "3" to "text[?]", "4" to "text[?]")
    }

    @Test fun testExprMultiCase() {
        val code = "= when(a) { 0 -> 'A'; 1, 2 -> 'B'; 3 -> 'C'; else -> '?'; };"
        chkWhen("integer", code, "0" to "text[A]", "1" to "text[B]", "2" to "text[B]", "3" to "text[C]", "4" to "text[?]")
    }

    @Test fun testExprNonConstant() {
        chkWhen("integer", "{ val v0=0; val v1=1; return when(a){ v0->'A'; v1->'B'; else->'C'; }; }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[C]")
        chkWhen("integer", "{ val v0=0; val v1=1; return when(a){ 0->'X'; v0->'A'; v1->'B'; else->'C'; }; }",
                "0" to "text[X]", "1" to "text[B]", "2" to "text[C]")
        chkWhen("integer", "{ val v0=0; val v1=1; return when(a){ v0->'A'; 0->'X'; v1->'B'; else->'C'; }; }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[C]")
        chkWhen("integer", "{ val v0_1=0; val v0_2=0; val v1=1; return when(a){ v0_1->'A'; v0_2->'B'; v1->'B'; else->'C'; }; }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[C]")
    }

    @Test fun testExprNoParameter() {
        val code = "= when { a == 0 -> 'A'; a >= 1 and a <= 2 -> 'B'; a == 3 -> 'C'; else -> '?'; };"
        chkWhen("integer", code, "0" to "text[A]", "1" to "text[B]", "2" to "text[B]", "3" to "text[C]", "4" to "text[?]")
    }

    @Test fun testExprNoParameterMulti() {
        val code = "= when { a == 0 -> 'A'; a == 1, a == 2 -> 'B'; a == 3 -> 'C'; else -> '?'; };"
        chkWhen("integer", code, "0" to "text[A]", "1" to "text[B]", "2" to "text[B]", "3" to "text[C]", "4" to "text[?]")
    }

    @Test fun testExprNoElse() {
        val code = "= when(a) { 0 -> 'A'; 1 -> 'B'; 2 -> 'C'; };"
        val err = "ct_err:when_no_else"
        chkWhen("integer", code, "0" to err, "1" to err, "2" to err, "3" to err)
    }

    @Test fun testExprNoParameterNoElse() {
        val code = "= when { a == 0 -> 'A'; a == 1 -> 'B'; a == 2 -> 'C'; };"
        val err = "ct_err:when_no_else"
        chkWhen("integer", code, "0" to err, "1" to err, "2" to err, "3" to err)
    }

    @Test fun testExprCaseTypes() {
        chkWhen("integer", "= when(a) { 'Hello' -> 'A'; else -> 'B' };", "123" to "ct_err:when_case_type:integer:text")
        chkWhen("integer", "= when(a) { true -> 'A'; else -> 'B' };", "123" to "ct_err:when_case_type:integer:boolean")
        chkWhen("integer", "= when(a) { x'12ab' -> 'A'; else -> 'B' };", "123" to "ct_err:when_case_type:integer:byte_array")
        chkWhen("boolean", "= when(a) { 'Hello' -> 'A'; else -> 'B' };", "false" to "ct_err:when_case_type:boolean:text")

        chk("when(null) { else -> 'B' }", "ct_err:when_expr_type:null")
        chkWhen("integer", "= when(a) { null -> 'A'; else -> 'B' };", "123" to "ct_err:when_case_type:integer:null")
        chkWhen("integer?", "= when(a) { null -> 'A'; 123 -> 'B'; else -> 'C' };", "null" to "text[A]")
        chkWhen("integer?", "= when(a) { null -> 'A'; 123 -> 'B'; else -> 'C' };", "123" to "text[B]")
        chkWhen("integer?", "= when(a) { null -> 'A'; 123 -> 'B'; else -> 'C' };", "456" to "text[C]")

        chkWhen("(integer,text)", "= when(a) { null -> 'A'; else -> 'B' };",
                "(123,'Hello')" to "ct_err:when_case_type:(integer,text):null")
        chkWhen("(integer,text)", "= when(a) { ('Hello',123) -> 'A'; else -> 'B' };",
                "(123,'Hello')" to "ct_err:when_case_type:(integer,text):(text,integer)")
        chkWhen("(integer,text)", "= when(a) { (123,'Hello') -> 'A'; else -> 'B' };", "(123,'Hello')" to "text[A]")
        chkWhen("(integer,text)", "= when(a) { (123,'Hello') -> 'A'; else -> 'B' };", "(456,'Bye')" to "text[B]")
    }

    @Test fun testExprResultTypes() {
        chkWhenRet("integer", "text?", "= when(a) { 0 -> null; 1 -> 'A'; else -> '?'; };",
                "0" to "null", "1" to "text[A]", "2" to "text[?]")
        chkWhen("integer", "= when(a) { 0 -> 123; else -> 'Hello'; };", "0" to "ct_err:expr_when_incompatible_type:[integer]:[text]")
        chkWhen("integer", "= when(a) { 0 -> 'Hello'; else -> 123; };", "0" to "ct_err:expr_when_incompatible_type:[text]:[integer]")
        chkWhen("integer", "= when(a) { 0 -> true; else -> x'12EF'; };", "0" to "ct_err:expr_when_incompatible_type:[boolean]:[byte_array]")
    }

    @Test fun testExprDuplicateConstantValue() {
        chkWhen("integer", "= when(a) { 0 -> 'A'; 0 -> 'B'; else -> 'C' };", "0" to "ct_err:when_expr_dupvalue:int[0]")
        chkWhen("integer", "= when(a) { 0 -> 'A'; 0 -> 'A'; else -> 'C' };", "0" to "ct_err:when_expr_dupvalue:int[0]")
        chkWhen("integer", "= when(a) { 0 -> 'A'; 1 -> 'B'; 0 -> 'C'; else -> 'D' };", "0" to "ct_err:when_expr_dupvalue:int[0]")
        chkWhen("integer", "= when(a) { 0 -> 'A'; 1 -> 'B'; 2, 0 -> 'C'; else -> 'D' };", "0" to "ct_err:when_expr_dupvalue:int[0]")

        chkWhen("integer", "= when(a) { -1 -> 'A'; -1 -> 'B'; else -> '?'; };", "0" to "ct_err:when_expr_dupvalue:int[-1]")
        chkWhen("integer", "= when(a) { -1 -> 'A'; 5 -> 'B'; -1 -> 'C'; else -> '?'; };",
                "0" to "ct_err:when_expr_dupvalue:int[-1]")
        chkWhen("integer", "= when(a) { -1, -1 -> 'A'; else -> '?'; };", "0" to "ct_err:when_expr_dupvalue:int[-1]")

        chkWhen("boolean", "= when(a) { true -> 'A'; true -> 'B'; else -> 'C' };",
                "true" to "ct_err:when_expr_dupvalue:boolean[true]")
        chkWhen("boolean", "= when(a) { false -> 'A'; false -> 'A'; else -> 'C' };",
                "false" to "ct_err:when_expr_dupvalue:boolean[false]")

        chkWhen("text", "= when(a) { 'Hello' -> 'A'; 'Bye' -> 'B'; 'Hello' -> 'C'; else -> 'D' };",
                "'Hello'" to "ct_err:when_expr_dupvalue:text[Hello]")

        chkWhen("(integer,text)", "= when(a) { (123,'Hello') -> 'A'; (123,'Hello') -> 'B'; else -> 'C' };",
                "(123,'Hello')" to "ct_err:when_expr_dupvalue:(int[123],text[Hello])")

        chkWhen("integer?", "= when(a) { 123 -> 'A'; null -> 'B'; 456 -> 'C'; null -> 'D'; else -> 'E'; };",
                "123" to "ct_err:when_expr_dupvalue:null")

        chkWhen("integer", "{ val t = 123; return when(a) { t -> 'A'; 1 -> 'B'; 2 -> 'C'; 1 -> 'D'; else -> 'E'; }; }",
                "0" to "ct_err:when_expr_dupvalue:int[1]")
    }

    @Test fun testExprFullCoverageBoolean() {
        chkWhen("boolean", "= when(a) { false -> 'A'; true -> 'B'; else -> 'C'; };", "false" to "ct_err:when_else_allvalues:boolean")
        chkWhen("boolean", "= when(a) { false -> 'A'; true -> 'B'; };", "false" to "text[A]", "true" to "text[B]")
        chkWhen("boolean", "= when(a) { false -> 'A'; };", "true" to "ct_err:when_no_else")
        chkWhen("boolean", "= when(a) { true -> 'B'; };", "true" to "ct_err:when_no_else")
    }

    @Test fun testExprFullCoverageEnum() {
        def("enum E { A, B, C, D }")
        chkWhen("E", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; D -> 'D'; else -> '?'; };",
                "E.A" to "ct_err:when_else_allvalues:E")
        chkWhen("E", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; D -> 'D'; };",
                "E.A" to "text[A]", "E.B" to "text[B]", "E.C" to "text[C]", "E.D" to "text[D]")
        chkWhen("E", "= when(a) { B -> 'B'; C -> 'C'; D -> 'D'; };", "E.A" to "ct_err:when_no_else")
        chkWhen("E", "= when(a) { A -> 'A'; C -> 'C'; D -> 'D'; };", "E.A" to "ct_err:when_no_else")
        chkWhen("E", "= when(a) { A -> 'A'; B -> 'B'; D -> 'D'; };", "E.A" to "ct_err:when_no_else")
        chkWhen("E", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; };", "E.A" to "ct_err:when_no_else")
        chkWhen("E", "= when(a) { A -> 'A'; E.A -> 'B'; else -> '?'; };", "E.A" to "ct_err:when_expr_dupvalue:E[A]")
    }

    /**
     * RELL-255: lookup `when` over an enum used the placeholder `enumDefIndex = 0` baked
     * into the constant key at parse time, so the runtime resolved every key against the
     * first declared enum. With a smaller enum declared earlier in the module, this threw
     * `IndexOutOfBoundsException` at evaluation. The R_->RR_ resolver now patches the keys.
     */
    @Test fun testExprEnumKeyResolutionWithEarlierSmallerEnum() {
        def("enum E1 { A, B, C }")
        def("enum E2 { LT, LE, EQ, GE, GT }")
        chkWhen("E2", "= when(a) { LT -> 'lt'; LE -> 'le'; EQ -> 'eq'; GE -> 'ge'; GT -> 'gt'; };",
                "E2.LT" to "text[lt]", "E2.LE" to "text[le]", "E2.EQ" to "text[eq]",
                "E2.GE" to "text[ge]", "E2.GT" to "text[gt]")
    }

    @Test fun testFullCoverageNullableBoolean() {
        chkWhen("boolean?", "= when(a) { false -> 'A'; true -> 'B'; else -> 'C'; };",
                "false" to "text[A]", "true" to "text[B]", "null" to "text[C]")
        chkWhen("boolean?", "= when(a) { false -> 'A'; true -> 'B'; null -> 'C'; else -> 'D'; };",
                "null" to "ct_err:when_else_allvalues:boolean?")
        chkWhen("boolean?", "= when(a) { false -> 'A'; true -> 'B'; null -> 'C' };",
                "false" to "text[A]", "true" to "text[B]", "null" to "text[C]")
        chkWhen("boolean?", "= when(a) { false -> 'A'; true -> 'B'; };", "true" to "ct_err:when_no_else")
        chkWhen("boolean?", "= when(a) { true -> 'B'; null -> 'C'; };", "true" to "ct_err:when_no_else")
    }

    @Test fun testExprFullCoverageNullableEnum() {
        def("enum E { A, B, C }")
        chkWhen("E?", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; null -> '0'; else -> '?'; };",
                "E.A" to "ct_err:when_else_allvalues:E?")
        chkWhen("E?", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; null -> '0'; };",
                "E.A" to "text[A]", "E.B" to "text[B]", "E.C" to "text[C]", "null" to "text[0]")
        chkWhen("E?", "= when(a) { A -> 'A'; B -> 'B'; C -> 'C'; };", "E.A" to "ct_err:when_no_else")
        chkWhen("E?", "= when(a) { A -> 'A'; B -> 'B'; null -> '0'; };", "E.A" to "ct_err:when_no_else")
    }

    @Test fun testExprSemicolon() {
        chkWhen("integer", "= when(a) { 1 -> 'A'; else -> 'B' };", "1" to "text[A]", "2" to "text[B]")
        chkWhen("integer", "= when(a) { 1 -> 'A'; else -> 'B'; };", "1" to "text[A]", "2" to "text[B]")
        chkWhen("integer", "= when(a) { 1 -> 'A';; else -> 'B' };", "1" to "ct_err:syntax")
        chkWhen("integer", "= when(a) { 1 -> 'A'; else -> 'B';; };", "1" to "ct_err:syntax")
        chkWhen("integer", "= when(a) { 1 -> 'A';; else -> 'B';; };", "1" to "ct_err:syntax")
    }

    @Test fun testExprElseNotInEnd() {
        chkWhen("integer", "= when(a) { else -> 'A'; 1 -> 'B'; };", "1" to "ct_err:when_else_notlast")
    }

    @Test fun testExprUnit() {
        chk("when { 2 > 1 -> print(123); else -> print(456) }", "ct_err:[query_exprtype_unit][when_exprtype_unit][when_exprtype_unit]")
    }

    @Test fun testStmt() {
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> { val x = 'B'; return x; } else -> return '?'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1, 2 -> { val x = 'B'; return x; } else -> return '?'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[B]", "3" to "text[?]")
    }

    @Test fun testStmtNoParameter() {
        chkWhen("integer", "{ when { a == 0 -> return 'A'; a == 1 -> { val x = 'B'; return x; } else -> return '?'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]")
        chkWhen("integer", "{ when { a == 0 -> return 'A'; a == 1, a >= 5 -> { val x = 'B'; return x; } else -> return '?'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]", "5" to "text[B]", "1000" to "text[B]")
    }

    @Test fun testStmtNoElse() {
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; } }", "0" to "ct_err:fun_noreturn:f")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; } return '?'; }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; else -> return 'C'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[C]")
    }

    @Test fun testStmtFullCoverageBoolean() {
        chkWhen("boolean", "{ when(a) { false -> return 'A'; true -> return 'B'; } }",
                "false" to "text[A]", "true" to "text[B]")
        chkWhen("boolean", "{ when(a) { false -> return 'A'; true -> return 'B'; else -> return 'C'; } return ''; }",
                "false" to "ct_err:[when_else_allvalues:boolean][stmt_deadcode]")
        chkWhen("boolean", "{ when(a) { false -> return 'A'; } }", "false" to "ct_err:fun_noreturn:f")
        chkWhen("boolean", "{ when(a) { true -> return 'A'; } }", "false" to "ct_err:fun_noreturn:f")
    }

    @Test fun testStmtFullCoverageEnum() {
        def("enum E { A, B, C }")
        chkWhen("E", "{ when(a) { A -> return 'A'; B -> return 'B'; C -> return 'C'; else -> return '?'; } return ''; }",
                "E.A" to "ct_err:[when_else_allvalues:E][stmt_deadcode]")
        chkWhen("E", "{ when(a) { A -> return 'A'; B -> return 'B'; C -> return 'C'; } }",
                "E.A" to "text[A]", "E.B" to "text[B]", "E.C" to "text[C]")
        chkWhen("E", "{ when(a) { A -> return 'A'; B -> return 'B'; } }", "E.A" to "ct_err:fun_noreturn:f")
        chkWhen("E", "{ when(a) { A -> return 'A'; B -> return 'B'; } return '?'; }",
                "E.A" to "text[A]", "E.B" to "text[B]", "E.C" to "text[?]")
        chkWhen("E", "{ when(a) { B -> return 'B'; C -> return 'C'; } }", "E.A" to "ct_err:fun_noreturn:f")
        chkWhen("E", "{ when(a) { A -> return 'A'; C -> return 'C'; } }", "E.A" to "ct_err:fun_noreturn:f")
    }

    @Test fun testStmtSemicolon() {
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B' } return '?'; }", "0" to "ct_err:syntax")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; } return '?'; }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; else -> return '?' } }", "0" to "ct_err:syntax")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; 1 -> return 'B'; else -> return '?'; } }",
                "0" to "text[A]", "1" to "text[B]", "2" to "text[?]")

        // One extra semicolon must be supported for compatibility reasons - used in existing apps.
        chkWhen("integer", "{ when(a) { 0 -> return 'A';; } return 'B'; }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A';;; } return 'B'; }", "0" to "ct_err:syntax")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; else -> return 'B';; } }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; else -> return 'B';;; } }", "0" to "ct_err:syntax")

        chkWhen("integer", "{ when(a) { 0 -> { return 'A'; } } return 'B'; }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> { return 'A'; }; } return 'B'; }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> { return 'A'; };; } return 'B'; }", "0" to "ct_err:syntax")

        chkWhen("integer", "{ when(a) { 0 -> return 'A'; else -> { return 'B'; } } }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; else -> { return 'B'; }; } }", "0" to "text[A]", "1" to "text[B]")
        chkWhen("integer", "{ when(a) { 0 -> return 'A'; else -> { return 'B'; };; } }", "0" to "ct_err:syntax")
    }

    @Test fun testAtExpr() {
        initWhenAt("integer", "0")

        chkWhenAt("when (.x) { 1 -> .s1; 2 -> .s2; else -> '?'; }",
                "1" to "text[Yes]", "2" to "text[No]", "3" to "text[?]", "999" to "text[?]")

        chkWhenAt("when (.x) { 1 -> .s1; 2 -> .s2; }", "1" to "ct_err:when_no_else")

        chkWhenAt("when (.x) { 4, 16, 64 -> .s1; 3, 9, 27 -> .s2; else -> '?'; }",
                "1" to "text[?]", "3" to "text[No]", "4" to "text[Yes]", "9" to "text[No]", "16" to "text[Yes]",
                "27" to "text[No]", "64" to "text[Yes]")

        chkWhenAt("when (.x * .x + 1) { 2, 17, 37 -> .s1; 5, 10 -> .s2; else -> '?'; }",
                "1" to "text[Yes]", "2" to "text[No]", "3" to "text[No]", "4" to "text[Yes]", "5" to "text[?]",
                "6" to "text[Yes]")

        chkWhenAt("when (.x) { 1 -> .s1; 2 -> .s2; 1 -> .s1; else -> '?'; }", "1" to "ct_err:when_expr_dupvalue:int[1]")
    }

    @Test fun testAtExprNoParameter() {
        initWhenAt("integer", "0")
        chkWhenAt("when { .x * .x + 1 == 26, .x * 3 == 21 -> 'Magic' ; .x < 0 -> 'Negative'; else -> '?'; }",
                "5" to "text[Magic]", "7" to "text[Magic]", "-1" to "text[Negative]", "-1000" to "text[Negative]",
                "999" to "text[?]")
    }

    @Test fun testAtExprFullCoverageBoolean() {
        initWhenAt("boolean", "false")
        chkWhenAt("when(.x) { false -> 'A'; true -> 'B'; else -> 'C'; }", "false" to "ct_err:when_else_allvalues:boolean")
        chkWhenAt("when(.x) { false -> 'A'; true -> 'B'; }", "false" to "ct_err:expr_when:no_else")
        chkWhenAt("when(.x) { false -> 'A'; else -> 'B'; }", "false" to "text[A]", "true" to "text[B]")
        chkWhenAt("when(.x) { true -> 'A'; else -> 'B'; }", "false" to "text[B]", "true" to "text[A]")
        chkWhenAt("when(.x) { false -> 'A'; }", "true" to "ct_err:when_no_else")
        chkWhenAt("when(.x) { true -> 'B'; }", "true" to "ct_err:when_no_else")
    }

    @Test fun testAtExprFullCoverageEnum() {
        def("enum E { A, B, C }")
        initWhenAt("E", "0")

        chkWhenAt("when(.x) { A -> 'A'; B -> 'B'; C -> 'C'; else -> '?'; }", "E.A" to "ct_err:when_else_allvalues:E")
        chkWhenAt("when(.x) { A -> 'A'; B -> 'B'; C -> 'C'; }", "E.A" to "ct_err:expr_when:no_else")
        chkWhenAt("when(.x) { A -> 'A'; B -> 'B'; else -> 'C'; }", "E.A" to "text[A]", "E.B" to "text[B]", "E.C" to "text[C]")
        chkWhenAt("when(.x) { B -> 'B'; C -> 'C'; }", "E.A" to "ct_err:when_no_else")
        chkWhenAt("when(.x) { A -> 'A'; C -> 'C'; }", "E.A" to "ct_err:when_no_else")
        chkWhenAt("when(.x) { A -> 'A'; B -> 'B'; }", "E.A" to "ct_err:when_no_else")
        chkWhenAt("when(.x) { A -> 'A'; E.A -> 'B'; else -> '?'; }", "E.A" to "ct_err:when_expr_dupvalue:E[A]")
    }

    @Test fun testAtExprNullable() {
        tstCtx.useSql = true
        def("entity data { value: integer; }")
        def("function f(x: integer?) = x;")
        insert("c0.data", "value", "1,123")

        var from = "(d1: data, @outer d2: data @* { d2 >= d1 })"
        chk("$from @ {} ( when(d2?.value) { 123 -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { 123, 456 -> 100; else -> 200 } )", "int[100]")

        chk("$from @ {} ( when(d2?.value) { f(123) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(456) -> 100; else -> 200 } )", "int[200]")
        chk("$from @ {} ( when(d2?.value) { f(null) -> 100; else -> 200 } )", "int[200]")
        chk("$from @ {} ( when(d2?.value) { f(123), f(456) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(123), f(null) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(null), f(123) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(456), f(null) -> 100; else -> 200 } )", "int[200]")
        chk("$from @ {} ( when(d2?.value) { f(null), f(456) -> 100; else -> 200 } )", "int[200]")

        from = "(d1: data, @outer d2: data @* { d2 > d1 })"
        chk("$from @ {} ( when(d2?.value) { f(123) -> 100; else -> 200 } )", "int[200]")
        chk("$from @ {} ( when(d2?.value) { f(null) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(123), f(456) -> 100; else -> 200 } )", "int[200]")
        chk("$from @ {} ( when(d2?.value) { f(123), f(null) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(null), f(123) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(456), f(null) -> 100; else -> 200 } )", "int[100]")
        chk("$from @ {} ( when(d2?.value) { f(null), f(456) -> 100; else -> 200 } )", "int[100]")
    }

    private fun chkWhen(type: String, code: String, vararg cases: Pair<String, String>) {
        chkWhenRet(type, "text", code, *cases)
    }

    private fun chkWhenRet(type: String, retType: String, code: String, vararg cases: Pair<String, String>) {
        val fnCode = "function f(a: $type): $retType $code"
        for (case in cases) {
            val fullCode = "$fnCode query q() = f(${case.first});"
            chkFull(fullCode, case.second)
        }
    }

    private fun initWhenAt(type: String, value: String) {
        tstCtx.useSql = true
        def("entity foo { mutable x: $type; s1: text; s2: text; }")
        insert("c0.foo", "x,s1,s2", "100,$value,'Yes','No'")
    }

    private fun chkWhenAt(expr: String, vararg cases: Pair<String, String>) {
        val fullExpr = "foo @{} ( _ = $expr )"
        for ((arg, expected) in cases) {
            chkOp("update foo@{} ( x = $arg );")
            chk(fullExpr, expected)
        }
    }
}
