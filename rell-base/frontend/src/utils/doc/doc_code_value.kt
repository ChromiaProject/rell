/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.utils.formatEx
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.min

sealed class DocValue {
    abstract fun genCode(b: DocCode.Builder)

    companion object {
        val NULL: DocValue = DocValue_Keyword("null")
        val UNIT: DocValue = DocValue_Name("unit")

        private val FALSE: DocValue = DocValue_Boolean(false)
        private val TRUE: DocValue = DocValue_Boolean(true)

        fun boolean(value: Boolean): DocValue = if (value) TRUE else FALSE
        fun integer(value: Long): DocValue = DocValue_Integer(value)

        fun bigInteger(value: BigInteger): DocValue? {
            val bits = value.bitLength()
            return if (bits > 1024) null else DocValue_BigInteger(value)
        }

        fun decimal(value: BigDecimal): DocValue? {
            val value2 = value.stripTrailingZeros()
            val precision = value2.precision()
            val scale = value2.scale()
            val digs = if (scale > 0) max(precision, scale) else (precision - scale)
            return if (digs > 100) null else DocValue_Decimal(value)
        }

        fun text(value: String): DocValue = DocValue_Text(value)
        fun byteArray(value: ByteArray): DocValue = DocValue_ByteArray(value)
        fun rowid(value: Long): DocValue = DocValue_Rowid(value)
    }
}

private class DocValue_Keyword(private val kw: String): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.keyword(kw)
    }
}

private class DocValue_Name(private val name: String): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(name)
    }
}

private class DocValue_Boolean(private val value: Boolean): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        val s = if (value) "true" else "false"
        b.keyword(s)
    }
}

private class DocValue_Integer(private val value: Long): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(value.toString())
    }
}

private class DocValue_BigInteger(private val value: BigInteger): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(value.toString())
        b.raw("L")
    }
}

private class DocValue_Decimal(private val value: BigDecimal): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        // Match Lib_DecimalMath.toString: strip trailing zeros from string form
        val s = removeTrailingDecimalZeros(value.toPlainString())
        b.raw(s)
        if ('.' !in s) b.raw(".0")
    }
}

private fun removeTrailingDecimalZeros(s: String): String {
    val n = s.length
    var i = 0
    if (i < n && (s[i] == '-' || s[i] == '+')) ++i
    var fracStart = n
    var fracEnd = n
    while (i < n) {
        val c = s[i]
        if (c == '.') {
            fracStart = i
            ++i
            while (i < n && s[i] in '0'..'9') ++i
            fracEnd = i
            break
        }
        ++i
    }
    if (fracStart == n) return s
    var j = fracEnd
    while (j > fracStart && s[j - 1] == '0') --j
    if (j > fracStart && s[j - 1] == '.') --j
    return if (j == fracEnd) s
    else if (fracEnd == s.length) s.substring(0, j)
    else s.substring(0, j) + s.substring(fracEnd)
}

private class DocValue_Text(private val value: String): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        val maxLen = 100

        val buf = StringBuilder()
        buf.append("\"")
        var truncated = false

        for (c in value) {
            if (buf.length >= maxLen + 1) {
                buf.append("...")
                truncated = true
                break
            }
            if (c == '"') {
                buf.append("\\\"")
            } else if (c == '\r') {
                buf.append("\\r")
            } else if (c == '\n') {
                buf.append("\\n")
            } else if (c == '\t') {
                buf.append("\\t")
            } else if (c < '\u0020') {
                buf.append("\\u${"%04X".formatEx(c.code)}")
            } else {
                buf.append(c)
            }
        }

        if (!truncated) {
            buf.append("\"")
        }

        val text = buf.toString()
        b.raw(text)
    }
}

private class DocValue_ByteArray(private val value: ByteArray): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.raw("x\"")

        val n = value.size
        val k = min(n, 50)

        val buf = StringBuilder()
        for (i in 0 until k) {
            buf.append("%02X".formatEx(value[i]))
        }

        b.raw(buf.toString())

        if (k < n) {
            b.raw("...")
        } else {
            b.raw("\"")
        }
    }
}

private class DocValue_Rowid(private val value: Long): DocValue() {
    override fun genCode(b: DocCode.Builder) {
        b.raw("rowid(")
        b.raw(value.toString())
        b.raw(")")
    }
}
