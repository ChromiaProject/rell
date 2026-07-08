/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Negative (must-not-compile) lambda tests. The positive syntax/semantic tests - which compile and
 * run - live in `dynamic-tests/LambdaExprTest.rell`, executed by `RellQueryFileTest` across all
 * three backends. Only compilation-error cases remain here, since they cannot live in a `.rell`
 * file that must compile.
 */
class LambdaExprTest: BaseRellTest() {
    @Test fun testNoExpectedTypeIsError() {
        chkEx("{ val f = x -> x * 2; return 0; }", "ct_err:lambda:no_type")
    }

    @Test fun testArityMismatchIsError() {
        chkEx("{ val f: (integer) -> integer = (x, y) -> x + y; return 0; }", "ct_err:lambda:param_count:1:2")
    }

    @Test fun testValueBlockNoReturnError() {
        chkEx("{ val f: (integer) -> integer = x -> { val y = x; }; return 0; }", "ct_err:lambda:no_return")
    }

    @Test fun testCaptureIsImmutable() {
        // A captured local is bound by value and cannot be reassigned inside the lambda.
        chkEx("{ var n = 7; val f: (integer) -> integer = x -> { n = x; n }; return f(1); }",
            "ct_err:expr_assign_val:n")
    }

    @Test fun testBodyTypeMismatchIsError() {
        chkEx("{ val f: (integer) -> text = x -> x; return 0; }", "ct_err:fn_rettype:[text]:[integer]")
    }

    @Test fun testUnknownNameInBodyIsError() {
        chkEx("{ val f: (integer) -> integer = x -> x + nope; return 0; }", "ct_err:unknown_name:nope")
    }

    @Test fun testDuplicateParamIsError() {
        chkEx("{ val f: (integer, integer) -> integer = (x, x) -> x; return 0; }", "ct_err:lambda:dup_param:x")
    }

    @Test fun testUninitializedOuterVarNotCaptured() {
        // An outer local that is not definitely initialized at the lambda site is not captured, so a
        // reference to it inside the body does not resolve to that variable.
        chkEx("{ var q: integer; val f: () -> integer = () -> q; return 0; }", "ct_err:expr_novalue:function:[q]")
    }

    @Test fun testPartialAnnotationIsError() {
        chkEx("{ val f = (x: integer, y) -> x + y; return 0; }", "ct_err:lambda:partial_param_types")
    }

    @Test fun testAnnotationConflictsExpectedTypeIsError() {
        // Annotation present AND expected type present, but they differ: structural-equality error,
        // in either the covariant or contravariant direction.
        chkEx("{ val f: (integer) -> integer = (x: text) -> 0; return 0; }",
            "ct_err:lambda:param_type:0:[integer]:[text]")
        chkEx("{ val f: (integer?) -> integer = (x: integer) -> 0; return 0; }",
            "ct_err:lambda:param_type:0:[integer?]:[integer]")
    }

    @Test fun testBareParamAnnotationIsSyntaxError() {
        // A type annotation on a bare (non-parenthesised) parameter does not parse.
        chkEx("{ val f = x: integer -> x; return 0; }", "ct_err:syntax")
    }

    @Test fun testReturnInLambdaIsError() {
        // `return` is prohibited inside a lambda - the result is the lambda's final expression, and a
        // `return` would be a non-local control flow (it cannot return from the enclosing definition).
        // Holds for a value return, a bare return, and a return nested in a conditional.
        chkEx("{ val f: (integer) -> integer = x -> { return x; }; return 0; }", "ct_err:lambda:return")
        chkEx("{ val f: () -> unit = () -> { return; }; return 0; }", "ct_err:lambda:return")
        chkEx("{ val f: (integer) -> integer = x -> { if (x > 0) return 1; x }; return 0; }", "ct_err:lambda:return")
    }

    @Test fun testLambdaInWhenArmNeedsAnnotation() {
        chkEx("{ val f: (integer) -> integer = when (1) { 1 -> x -> x + 1; else -> x -> x }; return 0; }",
            "ct_err:[lambda:no_type][lambda:no_type]")
    }
}
