/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import org.apache.commons.lang3.time.FastDateFormat
import java.util.*

class Rt_TimeFormatValue(format: String): Rt_ValueBase() {
    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun str(format: Rt_StrFormat): String = "rell.time.format(${formatter.pattern})"
    override fun strCode(showTupleFieldNames: Boolean): String = "rell.time.format[${formatter.pattern}]"

    private val formatter: FastDateFormat = FastDateFormat.getInstance(validate(format), UTC_TIME_ZONE, Locale.US)

    fun textToMs(text: String): Long = formatter.parse(text).time
    fun msToText(ms: Long): String = formatter.format(ms)
    fun toText(): String = formatter.pattern

    companion object: Rt_ValueClass<Rt_TimeFormatValue> {
        override val name
            get() = "rell.time.format"

        override val klass = Rt_TimeFormatValue::class

        private val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")
        private const val LEGAL_UNQUOTED_ALPHA = "yMwWDdEuaHhmsS"
        private val LEGAL_QUOTED_ALPHA: Set<Char> = (('A'..'Z') + ('a'..'z')).toSet()

        private val LEGAL_NON_ALPHA: Set<Char> =
            ((' '..'@') +                       // ASCII space, punctuation, symbols and digits
                    ('['..'`') + ('{'..'~')).toSet() +  // More ASCII punctuation and symbols
                    '\t'                                // ASCII tab character

        private fun isLegalUnquoted(c: Char): Boolean = c in LEGAL_NON_ALPHA || c in LEGAL_UNQUOTED_ALPHA
        private fun isLegalQuoted(c: Char): Boolean = c in LEGAL_NON_ALPHA || c in LEGAL_QUOTED_ALPHA

        private fun validate(format: String): String {
            var index = 0
            var inQuotes = false

            while (index < format.length) {
                val current = format[index]
                val hasNext = index + 1 < format.length
                if (current == '\'' && hasNext && format[index + 1] == '\'') {
                    index += 2
                    continue
                } else if (current == '\'') {
                    inQuotes = !inQuotes
                } else if (inQuotes && !isLegalQuoted(current)) {
                    throw Rt_Exception.common(
                        "rell.time.format:format_text:illegal_character:$current",
                        "Illegal character ('$current') in time format text (within quotes).",
                    )
                } else if (!inQuotes && !isLegalUnquoted(current)) {
                    throw Rt_Exception.common(
                        "rell.time.format:format_text:illegal_format_character:$current",
                        "Illegal format character ('$current') in time format text.",
                    )
                }
                index++
            }
            if (inQuotes) {
                throw Rt_Exception.common(
                    "rell.time.format:format_text:unclosed_quoted_region",
                    "Unclosed quoted region in time format text.",
                )
            }
            return format
        }
    }
}
