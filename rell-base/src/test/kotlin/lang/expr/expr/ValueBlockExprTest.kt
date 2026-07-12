/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Negative (must-not-compile) tests for value blocks as `if`/`when` expression arms:
 * `if (c) { stmt; ...; result } else ...` and `when { c -> { stmt; ...; result } ... }`.
 * A block arm has plain-code-block semantics (it runs inline in the enclosing function's frame);
 * the positive syntax/semantic tests - which compile and run - live in
 * `dynamic-tests/ValueBlockExprTest.rell`, executed by `RellQueryFileTest` across all three
 * backends. Only compilation-error cases remain here, since they cannot live in a `.rell` file
 * that must compile.
 */
class ValueBlockExprTest: BaseRellTest() {
    @Test fun testUnitBlockArmInIfIsError() {
        // A block without a trailing expression (and that doesn't return on all paths) is
        // unit-typed, which an `if` expression arm cannot be.
        chkEx("{ return if (2 > 1) { print('Yes'); } else 2; }", "ct_err:expr_if_unit")
    }

    @Test fun testUnitBlockArmsInWhenAreErrors() {
        // Mirrors `WhenTest.testExprUnit`, with block arms instead of expression arms. A trailing
        // unit-typed expression is a unit block.
        chk("when { 2 > 1 -> { print(123); } else -> { print(456) } }",
            "ct_err:[query_exprtype_unit][when_exprtype_unit][when_exprtype_unit]")
    }

    @Test fun testIncompatibleBlockArmTypesIsError() {
        chkEx("{ return if (true) { 1 } else { 'a' }; }", "ct_err:expr_if_restype:[integer]:[text]")
    }

    @Test fun testBlockNotAllowedInOtherExpressionPositions() {
        // A value block is only an `if`/`when` arm form; anywhere else `{` does not start an
        // expression.
        chkEx("{ val x = { 1 }; return x; }", "ct_err:syntax")
        chkEx("{ return 1 + { 2 }; }", "ct_err:syntax")
    }

    @Test fun testBothArmsAlwaysReturnIsError() {
        // With every arm returning, the conditional yields no value.
        chkEx("{ val q = if (true) { return 1; } else { return 2; }; return q; }",
            "ct_err:expr_if_unit")
    }

    @Test fun testBreakOutsideLoopInBlockArmIsError() {
        // `break`/`continue` inside a block arm bind to the enclosing loop; without one they are
        // errors, exactly as in a plain block.
        chkEx("{ val q = if (true) { break; 1 } else 2; return q; }", "ct_err:stmt_break_noloop")
        chkEx("{ val q = if (true) { continue; 1 } else 2; return q; }", "ct_err:stmt_continue_noloop")
    }

    @Test fun testDeadCodeAfterReturnInBlockArm() {
        chkEx("{ val q = if (true) { return 1; 2 } else 3; return q; }", "ct_err:stmt_deadcode")
    }

    @Test fun testReturnInBlockArmInsideLambdaIsError() {
        // Inside a lambda body the enclosing-function rule applies: `return` stays prohibited,
        // also within a block arm.
        chkEx("{ val f: (integer) -> integer = x -> { if (x > 0) { return 1; } else 2 }; return 0; }",
            "ct_err:lambda:return")
    }

    @Test fun testReturnInBlockArmInsideAtExprIsError() {
        // An at-expression body is not a body statement; there is no enclosing statement for the
        // `return` to unwind to.
        chkEx("{ val r = [1, 2] @* {} ( if ($ > 1) { return 1; } else 0 ); return 0; }",
            "ct_err:stmt_return_disallowed")
    }

    @Test fun testReturnInBlockArmInDefaultParameterValueIsError() {
        chkCompile("function f(x: integer = if (true) { return 1; } else 2): integer = x;",
            "ct_err:stmt_return_disallowed")
    }

    @Test fun testBlockArmInGlobalConstantIsError() {
        // A value block's statements are invisible to the constant-expression purity validation,
        // so blocks are rejected in global constants.
        chkCompile("val c = if (true) { val v = 1; v } else 2;",
            "ct_err:def:const:bad_expr:[0::c]:value_block")
    }

    @Test fun testUnknownNameInBlockArmIsError() {
        chkEx("{ val q = if (true) { nope } else 2; return q; }", "ct_err:unknown_name:nope")
    }

    @Test fun testUninitializedOuterVarInBlockArmIsError() {
        // The outer variable resolves like in a plain block; using it uninitialized is the same
        // error a plain statement would get.
        chkEx("{ var u: integer; val q = if (true) { u } else 2; return q; }",
            "ct_err:expr_var_uninit:u")
    }

    @Test fun testWhenExpressionArmStillRequiresSemicolon() {
        // Block arms did not relax the ';' between expression arms: an expression arm is
        // ';'-terminated (except the last case), exactly as before block arms existed.
        chk("when { 1 > 2 -> 'a' else -> 'b' }", "ct_err:syntax")
        chk("when { 1 > 2 -> 'a'; 2 > 3 -> 'b' else -> 'c' }", "ct_err:syntax")
        chk("when { 1 > 2 -> 'a'; else -> 'b' }", "text[b]")
        chk("when { 1 > 2 -> 'a'; else -> 'b'; }", "text[b]")
    }

    @Test fun testWhenBlockArmTakesNoSemicolon() {
        // A block arm ends at its '}' - a ';' after it does not parse, mirroring the `when`
        // statement, where a block arm is a block statement with no ';'.
        chk("when { 1 > 2 -> { 'a' }; else -> 'b' }", "ct_err:syntax")
        chk("when { 1 > 2 -> 'a'; else -> { 'b' }; }", "ct_err:syntax")
        chk("when { 1 > 2 -> { 'a' } else -> 'b' }", "text[b]")
    }
}
