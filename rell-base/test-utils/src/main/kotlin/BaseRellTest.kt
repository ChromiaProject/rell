/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value

abstract class BaseRellTest(
    useSql: Boolean = false,
    private val gtv: Boolean = false,
): BaseTesterTest(useSql) {
    private var tst0: RellCodeTester? = null

    final override val tst: RellCodeTester get() {
        var res = tst0
        if (res == null) {
            res = RellCodeTester(tstCtx, entityDefs(), objInserts(), gtv = gtv)
            tst0 = res
        }
        return res
    }

    val repl by lazy { tst.createRepl() }

    protected fun resetTst() {
        tst0 = null
    }

    open fun entityDefs(): List<String> = listOf()
    open fun objInserts(): List<String> = listOf()

    fun chk(code: String, arg: Long, expected: String) = chkEx("= $code ;", arg, expected)
    fun chk(code: String, arg1: Long, arg2: Long, expected: String) = chkEx("= $code ;", arg1, arg2, expected)

    fun chkEx(code: String, arg: Long, expected: String) = tst.chkEx(code, listOf(arg), expected)
    fun chkEx(code: String, arg1: Long, arg2: Long, expected: String) = tst.chkEx(code, listOf(arg1, arg2), expected)
    fun chkEx(code: String, arg: Boolean, expected: String) = tst.chkEx(code, listOf(arg), expected)

    fun chkArgs(header: String, code: String, tester: (QueryTester) -> Unit) {
        val fullCode = "query q($header) $code"
        val checker = QueryTester(tst, fullCode)
        tester(checker)
    }

    fun chkVirtual(type: String, expr: String, arg: Gtv, expected: String) = tst.chkVirtual(type, expr, arg, expected)
    fun chkVirtualEx(type: String, body: String, arg: Gtv, expected: String) = tst.chkVirtualEx(type, body, arg, expected)

    fun chkFull(code: String, expected: String) =  tst.chkFull(code, expected)
    fun chkFull(code: String, args: List<Rt_Value>, expected: String) = tst.chkFull(code, args, expected)
    fun chkFull(code: String, name: String, args: List<Rt_Value>, expected: String) = tst.chkFull(code, name, args, expected)
    fun chkFullGtv(code: String, args: List<Gtv>, expected: String) = tst.chkFullGtv(code, args, expected)

    fun chkFull(code: String, arg: Long?, expected: String) = chkFull(code, listOf(rtVal(arg)), expected)
    fun chkFull(code: String, arg1: Long?, arg2: Long?, expected: String) = chkFull(code, listOf(rtVal(arg1), rtVal(arg2)), expected)
    fun chkFull(code: String, arg: String?, expected: String) = chkFull(code, listOf(rtVal(arg)), expected)
    fun chkFull(code: String, arg: Boolean?, expected: String) = chkFull(code, listOf(rtVal(arg)), expected)

    fun chkFn(code: String, expected: String) = tst.chkFn(code, expected)
    fun chkFnFull(code: String, expected: String) = tst.chkFnFull(code, expected)

    fun chkOp(code: String, expected: String = "OK") = tst.chkOp(code, expected)
    fun chkOpFull(code: String, expected: String = "OK", name: String = "o") = tst.chkOpEx(code, name, expected)
    fun chkOpFullGtv(code: String, args: List<Gtv>, expected: String = "OK") = tst.chkOpExGtv(code, args, expected)

    fun chkOpOut(code: String, vararg expected: String) {
        chkOp(code, "OK")
        chkOut(*expected)
    }

    fun chkCompile(code: String, expected: String = "OK", warn: String?) = tst.chkCompile(code, expected, warn)

    fun chkWarn(vararg  expected: String) = tst.chkWarn(*expected)
    fun chkMsg(vararg  expected: String) = tst.chkMsg(*expected)
    fun chkStack(vararg expected: String) = tst.chkStack(*expected)

    fun resetSqlBuffer() = tst.resetSqlBuffer()
    fun chkSql(vararg expected: String) = tst.chkSql(*expected)
    fun chkSqlRegex(vararg expected: String) = tst.chkSqlRegex(*expected)
    fun chkSqlCtr(expected: Int) = tst.chkSqlCtr(expected)

    fun chkTests(testModule: String, expected: String) = tst.chkTests(testModule, expected)

    fun chkVerCt(code: String, version: String, expOld: String, expNew: String = "OK") {
        tst.chkVerCt(code, version, expOld, expNew)
    }

    fun chkVerCtExpr(expr: String, version: String, expOld: String, expNew: String = "OK") {
        tst.chkVerCt("query q() = $expr;", version, expOld, expNew)
    }

    private fun rtVal(v: Long?) = if (v == null) Rt_NullValue else Rt_IntValue.get(v)
    private fun rtVal(v: String?) = if (v == null) Rt_NullValue else Rt_TextValue.get(v)
    private fun rtVal(v: Boolean?) = if (v == null) Rt_NullValue else Rt_BooleanValue.get(v)
}
