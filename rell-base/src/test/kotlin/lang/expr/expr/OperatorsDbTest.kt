/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import kotlin.test.Test

class OperatorsDbTest: OperatorsBaseTest() {
    @Test fun testComplexWhat() {
        // Make sure that complex what is not enabled in this test class, so all expressions are evaluated via SQL.
        chkExpr("[#0 : #1]", "ct_err:expr_sqlnotallowed", vInt(123), vText("Hello"))
    }
}
