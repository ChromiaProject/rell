/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

fun Long.toIntExact(): Int = Math.toIntExact(this)

fun Long.toIntExactOrNull(): Int? = if (toInt().toLong() == this) {
    toInt()
} else {
    null
}

fun checkedPow(base: Long, exp: Int): Long {
    require(exp >= 0) { "Negative exponent: $exp" }
    if (exp == 0) return 1L
    if (base == 0L) return 0L
    if (base == 1L) return 1L
    if (base == -1L) return if (exp and 1 == 0) 1L else -1L
    var b = base
    var e = exp
    var res = 1L
    while (e > 0) {
        if (e and 1 == 1) res = Math.multiplyExact(res, b)
        e = e ushr 1
        if (e > 0) b = Math.multiplyExact(b, b)
    }
    return res
}

fun saturatedAdd(a: Long, b: Long): Long {
    val r = a + b
    return if (((a xor r) and (b xor r)) < 0) {
        if (a >= 0) Long.MAX_VALUE else Long.MIN_VALUE
    } else r
}
