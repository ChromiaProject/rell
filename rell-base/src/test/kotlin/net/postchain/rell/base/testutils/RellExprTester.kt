/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import java.math.BigDecimal
import java.math.BigInteger

abstract class RellExprTester {
    abstract class TstVal

    abstract fun chkExpr(expr: String, expected: String, vararg args: TstVal)
    abstract fun compileExpr(expr: String, types: List<String>): String
    abstract fun rtErr(code: String): String

    abstract fun vBool(v: Boolean): TstVal
    abstract fun vInt(v: Long): TstVal
    abstract fun vBigInt(v: BigInteger): TstVal
    abstract fun vDec(v: BigDecimal): TstVal
    abstract fun vText(v: String): TstVal
    abstract fun vBytes(v: String): TstVal
    abstract fun vRowid(v: Long): TstVal
    abstract fun vJson(v: String): TstVal
    abstract fun vObj(ent: String, id: Long): TstVal
}
