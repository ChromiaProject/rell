/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Negative (must-not-compile) and warning tests for jump expressions: `return`/`break`/`continue`
 * used as an expression (an if/when arm, a value block's trailing expression, the right operand
 * of `?:`, ...). A jump expression's type is the non-denotable bottom type; evaluating it escapes
 * the enclosing statement, so it is legal exactly where the corresponding statement would be.
 * The positive compile-and-run tests live in `dynamic-tests/JumpExprTest.rell`.
 */
class JumpExprTest: BaseRellTest() {
    @Test fun testBreakContinueExprOutsideLoopIsError() {
        chkEx("{ val q = if (true) break else 1; return q; }", "ct_err:stmt_break_noloop")
        chkEx("{ val q = if (true) continue else 1; return q; }", "ct_err:stmt_continue_noloop")
    }

    @Test fun testReturnExprInsideLambdaIsError() {
        // The lambda rule is unchanged: a lambda's result is its final expression, `return` is
        // prohibited in either form.
        chkEx("{ val f: (integer?) -> integer = x -> if (x != null) x else return 0; return 0; }",
            "ct_err:lambda:return")
    }

    @Test fun testReturnExprInsideAtExprIsError() {
        // An at-expression body has no enclosing body statement to unwind to.
        chkEx("{ val r = [1, 2] @* {} ( if ($ > 1) return 0 else $ ); return 0; }",
            "ct_err:stmt_return_disallowed")
    }

    @Test fun testBreakExprInsideAtExprInLoopIsError() {
        // The loop context is reset at the at-expression boundary: a `break` inside the
        // at-expression body cannot bind to a loop outside it.
        chkEx("{ for (i in [1]) { val r = [1, 2] @* {} ( if ($ > 1) break else $ ); } return 0; }",
            "ct_err:stmt_break_noloop")
    }

    @Test fun testReturnExprInDefaultParameterValueIsError() {
        chkCompile("function f(x: integer = if (true) return 1 else 2): integer = x;",
            "ct_err:stmt_return_disallowed")
    }

    @Test fun testReturnExprInGlobalConstantIsError() {
        // A jump expression compiles to a value block, which is rejected in global constants.
        chkCompile("val c = if (true) return else 2;",
            "ct_err:[def:const:bad_expr:[0::c]:value_block][stmt_return_disallowed]")
    }

    @Test fun testReturnExprOperandTypeMismatchIsError() {
        chkEx("{ val q: integer? = 1; val r = q ?: return 'a'; return r; }",
            "ct_err:fn_rettype:[text]:[integer]")
    }

    @Test fun testElvisReturnSmartCastsLeftOperand() {
        // Control continues past `x ?: return ...` only when x was non-null.
        chkEx("{ val x = _nullable_int(5); val c = x ?: return 0; return x + 1; }", "int[6]")
    }

    @Test fun testUnreachableValueWarnings() {
        chkEx("{ val q = return 1; }", "int[1]")
        chkWarn("expr:unreachable")

        chkEx("{ val l = [return 1]; return 0; }", "int[1]")
        chkWarn("expr:unreachable")

        chkEx("{ val m = [return 1 : 2]; return 0; }", "int[1]")
        chkWarn("expr:unreachable")
    }

    @Test fun testJumpExprAsBinaryOperandIsTypeError() {
        // Operator resolution needs concrete operand types; a bottom-typed operand is rejected,
        // not absorbed (write the jump as the whole arm or behind `?:` instead).
        chkEx("{ return 1 + (return 2); }", "ct_err:binop_operand_type:+:[integer]:[nothing]")
    }

    @Test fun testBothArmsJumpTypesAsBottom() {
        // Both arms jumping is legal - the conditional itself never produces a value; anything
        // after it is dead code.
        chkEx("{ val q = if (true) return 1 else return 2; return 0; }",
            "ct_err:stmt_deadcode")
    }

    @Test fun testWhenArmJumpRequiresSemicolonLikeAnyExpressionArm() {
        chk("when { 1 > 2 -> 'a' else -> 'b' }", "ct_err:syntax")
        chkEx("{ val q = when { 1 > 2 -> return 'x'; else -> 'b' }; return q; }", "text[b]")
    }
}
