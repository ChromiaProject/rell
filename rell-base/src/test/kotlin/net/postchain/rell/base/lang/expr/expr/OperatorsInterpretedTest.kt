/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import org.junit.Test

class OperatorsInterpretedTest: OperatorsBaseTest() {
    @Test fun testPlus2() {
        chkOp("+", vBytes("0123ABCD"), vText("Hello"), "text[0x0123abcdHello]")
        chkOp("+", vText("Hello"), vBytes("0123ABCD"), "text[Hello0x0123abcd]")
        chkOp("+", vObj("user", 1000), vText("Hello"), "text[user[1000]Hello]")
        chkOp("+", vText("Hello"), vObj("user", 2000), "text[Hellouser[2000]]")
    }
}
