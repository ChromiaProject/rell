/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.formatEx
import net.postchain.rell.base.utils.immSetOf
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import java.util.regex.Pattern
import kotlin.reflect.full.createType

/**
 * Abstract base for text values. Concrete leaves choose their physical layout:
 * [Rt_JavaStringText] for the canonical Java-String-backed representation used by the tree-walk
 * interpreter and as the spill target for non-specialised paths.
 */
interface Rt_TextValue: Rt_Value {
    val value: String

    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.TEXT


    override fun strCode(showTupleFieldNames: Boolean): String {
        val esc = escape(value)
        return "text[$esc]"
    }

    override fun str(format: Rt_StrFormat): String = value

    companion object:
        Rt_GtvCompatibleValueClass<Rt_TextValue>,
        Rt_NativeCompatibleValueClass<Rt_TextValue>,
        Rt_SqlCompatibleValueClass<Rt_TextValue> {
        override val name
            get() = "text"

        override val klass = Rt_TextValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.TEXT)
        override val nativeTypes = immSetOf(String::class.createType())

        override val sqlType: DataType<String>
            get() = SQLDataType.CLOB

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> a.asString().compareTo(b.asString()) }

        val EMPTY: Rt_TextValue = Rt_JavaStringText("")

        @JvmStatic
        fun get(s: String): Rt_TextValue = if (s.isEmpty()) EMPTY else Rt_JavaStringText(s)

        override fun toGtv(value: Rt_TextValue, pretty: Boolean): Gtv = GtvString(value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_TextValue =
            get(GtvRtUtils.gtvToString(ctx, gtv, "text"))

        override fun toNative(value: Rt_TextValue): Any = value.value

        override fun fromNative(value: Any?): Rt_TextValue = get(value as String)

        override fun toSqlValue(value: Rt_TextValue): Any = value.value

        override fun toSql(value: Rt_TextValue, params: PreparedStatementParams, idx: Int) =
            params.setString(idx, value.value)

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getString(idx)
            return if (v != null) get(v) else Rt_SqlNull.check(name, nullable)
        }

        fun like(s: String, pattern: String): Boolean {
            val regex = likePatternToRegex(pattern, '_', '%')
            val m = regex.matcher(s)
            return m.matches()
        }

        fun likePatternToRegex(pattern: String, one: Char, many: Char): Pattern {
            val buf = StringBuilder()
            val raw = StringBuilder()
            var esc = false

            for (c in pattern) {
                if (esc) {
                    raw.append(c)
                    esc = false
                } else if (c == '\\') {
                    esc = true
                } else if (c == one || c == many) {
                    if (raw.isNotEmpty()) buf.append(Pattern.quote(raw.toString()))
                    raw.setLength(0)
                    buf.append(if (c == many) ".*" else ".")
                } else {
                    raw.append(c)
                }
            }

            if (raw.isNotEmpty()) buf.append(Pattern.quote(raw.toString()))
            val s = buf.toString()
            return Pattern.compile(s, Pattern.DOTALL)
        }

        private fun escape(s: String): String {
            if (s.isEmpty()) return ""

            return buildString(s.length) {
                for (c in s) {
                    when (c) {
                        '\t' -> append("\\t")
                        '\r' -> append("\\r")
                        '\n' -> append("\\n")
                        '\b' -> append("\\b")
                        '\\' -> append("\\\\")
                        in '\u0020'..<'\u0080' -> append(c)
                        else -> {
                            append("\\u")
                            append("%04x".formatEx(c.code))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Java-String-backed text leaf.
 */
@JvmRecord
data class Rt_JavaStringText(override val value: String): Rt_TextValue {
    override fun equals(other: Any?): Boolean = other === this || (other is Rt_TextValue && value == other.value)
    override fun hashCode(): Int = value.hashCode()
}
