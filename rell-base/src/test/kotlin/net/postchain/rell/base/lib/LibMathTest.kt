/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibMathTest: BaseRellTest() {
    @Test fun testAbs() {
        chk("abs(a)", 0, "int[0]")
        chk("abs(a)", -123, "int[123]")
        chk("abs(a)", 123, "int[123]")
        chk("abs('Hello')", "ct_err:expr_call_badargs:[abs]:[text]")
        chk("abs()", "ct_err:expr_call_badargs:[abs]:[]")
        chk("abs(1, 2)", "ct_err:expr_call_badargs:[abs]:[integer,integer]")
    }

    @Test fun testMinMax() {
        chk("min(a, b)", 100, 200, "int[100]")
        chk("min(a, b)", 200, 100, "int[100]")
        chk("max(a, b)", 100, 200, "int[200]")
        chk("max(a, b)", 200, 100, "int[200]")
    }

    @Test fun testLibMinMaxPromotion() {
        chk("min(123, 456L)", "bigint[123]")
        chk("max(123, 456L)", "bigint[456]")
        chk("min(123L, 456)", "bigint[123]")
        chk("max(123L, 456)", "bigint[456]")

        chk("min(123, 456.0)", "dec[123]")
        chk("max(123, 456.0)", "dec[456]")
        chk("min(123.0, 456)", "dec[123]")
        chk("max(123.0, 456)", "dec[456]")

        chk("min(123L, 456.0)", "dec[123]")
        chk("max(123L, 456.0)", "dec[456]")
        chk("min(123.0, 456L)", "dec[123]")
        chk("max(123.0, 456L)", "dec[456]")
    }
}
