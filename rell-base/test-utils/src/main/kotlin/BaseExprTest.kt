/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals

abstract class BaseExprTest: BaseResourcefulTest() {
    protected val mode: Mode = let {
        val className = javaClass.simpleName
        val (ipTest, dbTest) = "IpTest" to "DbTest"
        when {
            className.endsWith(ipTest) || className.endsWith("InterpretedTest") -> Mode.INTERPRETED
            className.endsWith(dbTest) -> Mode.DATABASE
            else -> error("Cannot decide test mode from class name: $className; must end with $ipTest or $dbTest")
        }
    }

    private val tst: RellExprTester by lazy {
        when (mode) {
            Mode.INTERPRETED -> RellExprIpTester()
            Mode.DATABASE -> resource(RellExprDbTester())
        }
    }

    protected fun chkExpr(expr: String, expected: String, vararg args: RellExprTester.TstVal) {
        tst.chkExpr(expr, expected, *args)
    }

    protected fun chkExprErr(expr: String, types: List<String>, expected: String) {
        val actual = tst.compileExpr(expr, types)
        assertEquals(expected, actual)
    }

    protected fun rtErr(code: String): String = tst.rtErr(code)

    protected fun vBool(v: Boolean) = tst.vBool(v)
    protected fun vInt(v: Long) = tst.vInt(v)
    protected fun vBigInt(v: BigInteger) = tst.vBigInt(v)
    protected fun vDec(v: BigDecimal) = tst.vDec(v)
    protected fun vText(v: String) = tst.vText(v)
    protected fun vBytes(v: String) = tst.vBytes(v)
    protected fun vRowid(v: Long) = tst.vRowid(v)
    protected fun vJson(v: String) = tst.vJson(v)
    protected fun vObj(ent: String, id: Long) = tst.vObj(ent, id)

    protected fun vBigInt(s: String) = vBigInt(parseBigInt(s))
    protected fun vBigInt(v: Long) = vBigInt(BigInteger.valueOf(v))

    private fun parseBigInt(s: String): BigInteger {
        var v = BigInteger.ZERO
        for (p in s.split("+")) {
            val v0 = try {
                BigDecimal(p).toBigIntegerExact()
            } catch (e: ArithmeticException) {
                throw ArithmeticException("${e.message}: $p")
            }
            v += v0
        }
        return v
    }

    protected fun vDec(s: String) = vDec(parseDec(s))
    protected fun vDec(v: Long) = vDec(BigDecimal(v))

    private fun parseDec(s: String): BigDecimal {
        var v = BigDecimal.ZERO
        for (p in s.split("+")) {
            v += BigDecimal(p)
        }
        return v
    }

    protected enum class Mode {
        INTERPRETED,
        DATABASE,
    }
}
