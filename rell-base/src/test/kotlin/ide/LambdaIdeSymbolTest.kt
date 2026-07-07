/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import kotlin.test.Test

/**
 * IDE symbols for lambda expressions. A lambda parameter is a real local definition (LOC_PARAMETER):
 * its declaration carries the def symbol and body references link back to it, so it is highlighted,
 * renameable, and findable like any local. A captured outer name occurring in the body stays UNKNOWN
 * - a local IDE link is frame-offset-scoped and cannot point across frames to the outer declaration
 * (the outer variable is still referenced through the bound argument, so no reference is lost).
 */
class LambdaIdeSymbolTest: BaseIdeSymbolTest() {
    @Test fun testParamSymbolAndBodyReference() {
        chkSymsStmt(
            "val f: (integer) -> integer = x -> x * 2; return f(1);",
            "x=LOC_PARAMETER|-|-",
            "x=LOC_PARAMETER|-|local[x:0]",
        )
    }

    @Test fun testAnnotatedParamSymbol() {
        chkSymsStmt(
            "val f = (n: integer) -> n * 2; return f(1);",
            "n=LOC_PARAMETER|-|-",
            "n=LOC_PARAMETER|-|local[n:0]",
        )
    }

    @Test fun testMultiParamSymbols() {
        chkSymsStmt(
            "val f: (integer, integer) -> integer = (a, b) -> a + b; return f(1, 2);",
            "a=LOC_PARAMETER|-|-",
            "b=LOC_PARAMETER|-|-",
            "a=LOC_PARAMETER|-|local[a:0]",
            "b=LOC_PARAMETER|-|local[b:0]",
        )
    }

    @Test fun testCapturedNameOccurrenceIsUnknown() {
        // Regression guard for the known limitation: the outer `factor` has its own symbol and the
        // param `x` is a proper LOC_PARAMETER, but the captured `factor` inside the body is UNKNOWN.
        chkSymsStmt(
            "val factor = 3; val f: (integer) -> integer = x -> x * factor; return f(1);",
            "factor=LOC_VAL|-|-",
            "x=LOC_PARAMETER|-|-",
            "x=LOC_PARAMETER|-|local[x:0]",
            "factor=UNKNOWN|-|-",
        )
    }
}
