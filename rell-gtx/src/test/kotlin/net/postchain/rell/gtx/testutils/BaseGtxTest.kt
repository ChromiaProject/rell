/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.testutils

import net.postchain.gtv.Gtv
import net.postchain.rell.base.testutils.BaseTesterTest

abstract class BaseGtxTest(useSql: Boolean = true): BaseTesterTest(useSql) {
    final override val tst = RellGtxTester(tstCtx)

    fun chkFull(code: String, args: String, expected: String) = tst.chkQueryEx(code, args, expected)
    fun chkFull(code: String, args: Map<String, Gtv>, expected: String) = tst.chkQueryEx(code, args, expected)

    fun chkUserMistake(code: String, msg: String) = tst.chkUserMistake(code, msg)

    fun chkCallOperation(name: String, args: List<String>, expected: String = "OK") =
            tst.chkCallOperation(name, args, expected)

    fun chkCallQuery(name: String, args: String, expected: String) = tst.chkCallQuery(name, args, expected)
    fun chkCallQuery(name: String, args: Map<String, Gtv>, expected: String) = tst.chkCallQuery(name, args, expected)
}
