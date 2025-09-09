package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueType
import net.postchain.rell.base.utils.RellVersions.SINCE_NOW
import org.apache.commons.lang3.time.FastDateFormat
import java.text.ParseException
import java.util.TimeZone

object Lib_RellTimeFormat {
    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.time", since = SINCE_NOW) {
            type("format", rType = R_TimeFormatType, since = SINCE_NOW) {
                comment("""
                    A time formatter type for formatting and parsing UTC dates and times. A `rell.time.format` value is
                    constructed from format pattern text. The resulting value can then be used to format a unix
                    timestamp according to the format pattern text, or the inverse, i.e. to parse a timestamp from text
                    that is formatted according to the format pattern text, returning a unix timestamp.

                    The following format specifiers are supported:

                    | Specifier | Meaning                       |
                    | --------- | ----------------------------- |
                    | `y`       | Year                          |
                    | `M`       | Month in the year             |
                    | `w`       | Week in the year              |
                    | `W`       | Week in the month             |
                    | `D`       | Day in the year               |
                    | `d`       | Day in the month              |
                    | `E`       | Day name in the week          |
                    | `u`       | Day number in the week        |
                    | `a`       | AM/PM specifier               |
                    | `H`       | Hour in the day (0-23)        |
                    | `h`       | Hour in the am/pm (1-12)      |
                    | `m`       | Minute in the hour            |
                    | `s`       | Second in the minute          |
                    | `S`       | Milliseconds in the second    |

                    Non-interpreted text may be included within `'single quotes'`. A single quote is escaped with
                    a second single quote (i.e. `''`).

                    Examples:

                    ```rell
                    rell.time.format('yyyy.MM.dd \'at\' HH:mm:ss').text_to_ms('2001.07.04 at 11:08:56') // returns 994244936000
                    rell.time.format('EEE, MMM d, \'\'yy').text_to_ms('Wed, Jul 4, \'01') // returns 994204800000
                    rell.time.format('h:mm a').text_to_ms('11:08 AM') // returns 40080000
                    rell.time.format('hh \'o\'\'clock\' a').text_to_ms('11 o\'clock AM') // returns 39600000

                    val ms: integer = 994244936235;
                    rell.time.format('hh:mm a').ms_to_text(ms) // returns '11:08 AM'
                    rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').ms_to_text(ms) // returns '02001.July.04 11:08 AM'
                    rell.time.format('yyyy-MM-dd\'T\'HH:mm:ss.SSS').ms_to_text(ms) // returns '2001-07-04T11:08:56.235'
                    rell.time.format('yyyy-\'W\'ww-u').ms_to_text(ms) // returns '2001-W27-3'
                    ```
                """)

                constructor(pure = true, since = SINCE_NOW) {
                    comment("""
                        Construct a rell.time.format value from time format pattern text.
                        @throws exception if the given format pattern text is invalid
                    """)
                    param("pattern", "text", arity = L_ParamArity.ONE, comment = "the time format pattern text")
                    body { format ->
                        val formatString = format.asString()
                        Rt_TimeFormatValue(formatString)
                    }
                }

                function("ms_to_text", "text", since = SINCE_NOW) {
                    comment("""
                        Format a unix timestamp according to this time format value.
                        @return a text representation of the given unix timestamp
                    """)
                    param("ms", "integer", arity = L_ParamArity.ONE, comment = "the unix timestamp")
                    body { self, ms ->
                        val formatValue = self.asRellTimeFormat()
                        val msValue = ms.asInteger()
                        Rt_TextValue.get(formatValue.msToText(msValue))
                    }
                }

                function("text_to_ms", "integer", since = SINCE_NOW) {
                    comment("""
                        Parse a unix timestamp from text formatted according to this time format value.
                        @return the unix timestamp represented by the given text
                        @throws exception if the given text is not formatted according to this time format value
                    """)
                    param("text", "text", arity = L_ParamArity.ONE, comment = "the time-formatted text")
                    body { self, text ->
                        val formatValue = self.asRellTimeFormat()
                        val textValue = text.asString()
                        Rt_IntValue.get(formatValue.textToMs(textValue))
                    }
                }

                function("text_to_ms_or_null", "integer?", since = SINCE_NOW) {
                    comment("""
                        Parse a unix timestamp from text formatted according to this time format value.
                        @return the unix timestamp represented by the given text, or `null` if the given text is not
                        formatted according to this time format value
                    """)
                    param("text", "text", arity = L_ParamArity.ONE, comment = "the time-formatted text")
                    body { self, text ->
                        try {
                            val formatValue = self.asRellTimeFormat()
                            val textValue = text.asString()
                            Rt_IntValue.get(formatValue.textToMs(textValue))
                        } catch (_: ParseException) {
                            Rt_NullValue
                        }
                    }
                }

                function("to_text", "text", since = SINCE_NOW) {
                    comment("Get the format text of this time format value.")
                    body { self -> Rt_TextValue.get(self.asRellTimeFormat().toText()) }
                }
            }

            function("ms_to_text", "text", since = SINCE_NOW) {
                comment("""
                    Format a unix timestamp according to the given time format text.

                    Equivalent to `rell.time.format(pattern).ms_to_text(ms)`.
                    @return a text representation of the given unix timestamp, in the specified format
                """)
                param("pattern", "text", arity = L_ParamArity.ONE, comment = "the time format pattern text")
                param("ms", "integer", arity = L_ParamArity.ONE, comment = "the unix timestamp")
                body { format, ms ->
                    val formatString = format.asString()
                    val msValue = ms.asInteger()
                    Rt_TextValue.get(Rt_TimeFormatValue(formatString).msToText(msValue))
                }
            }

            function("text_to_ms", "integer", since = SINCE_NOW) {
                comment("""
                    Parse a unix timestamp from text formatted according to the given time format pattern text.

                    Equivalent to `rell.time.format(pattern).text_to_ms(text)`.
                    @return the unix timestamp represented by the given text
                    @throws exception if the given text is not formatted according to the given time format pattern text
                """)
                param("pattern", "text", arity = L_ParamArity.ONE, comment = "the time format pattern text")
                param("text", "text", arity = L_ParamArity.ONE, comment = "the time-formatted text")
                body { format, text ->
                    val formatString = format.asString()
                    val dateString = text.asString()
                    Rt_IntValue.get(Rt_TimeFormatValue(formatString).textToMs(dateString))
                }
            }

            function("text_to_ms_or_null", "integer?", since = SINCE_NOW) {
                comment("""
                    Parse a unix timestamp from text formatted according to the given time format pattern text.

                    Equivalent to `rell.time.format(pattern).text_to_ms_or_null(text)`.
                    @return the unix timestamp represented by the given text, or `null` if the given text is not
                    formatted according to the given time format pattern text
                """)
                param("pattern", "text", arity = L_ParamArity.ONE, comment = "the time format pattern text")
                param("text", "text", arity = L_ParamArity.ONE, comment = "the time-formatted text")
                body { format, text ->
                    try {
                        val formatString = format.asString()
                        val dateString = text.asString()
                        Rt_IntValue.get(Rt_TimeFormatValue(formatString).textToMs(dateString))
                    } catch (_: ParseException) {
                        Rt_NullValue
                    }
                }
            }
        }
    }
}

internal object R_TimeFormatType: R_PrimitiveType("rell.time.format") {
    override fun getLibTypeDef(): C_LibTypeDef = Lib_Rell.RELL_TIME_FORMAT_TYPE
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
}

internal class Rt_TimeFormatValue(format: String): Rt_Value() {
    override val valueType: Rt_ValueType = Rt_CoreValueTypes.RELL_TIME_FORMAT.type()
    override fun type(): R_Type = R_TimeFormatType
    override fun str(format: StrFormat): String = "rell.time.format(${formatter.pattern})"
    override fun strCode(showTupleFieldNames: Boolean): String = "rell.time.format[${formatter.pattern}]"
    override fun asRellTimeFormat(): Rt_TimeFormatValue = this

    private val formatter: FastDateFormat = FastDateFormat.getInstance(validate(format), utcTimeZone)

    fun textToMs(text: String): Long = formatter.parse(text).time
    fun msToText(ms: Long): String = formatter.format(ms)
    fun toText(): String = formatter.pattern

    companion object {
        private val utcTimeZone: TimeZone by lazy { TimeZone.getTimeZone("UTC") }
        private val legalUnquotedAlpha = "yMwWDdEuaHhmsS"
        private val legalQuotedAlpha: Set<Char> = (('A'..'Z') + ('a'..'z')).toSet()
        private val legalNonAlpha: Set<Char> =
            ((' '..'@') +                       // ASCII space, punctuation, symbols and digits
            ('['..'`') + ('{'..'~')).toSet() +  // More ASCII punctuation and symbols
            '\t'                                // ASCII tab character
        private fun isLegalUnquoted(c: Char): Boolean = legalNonAlpha.contains(c) || legalUnquotedAlpha.contains(c)
        private fun isLegalQuoted(c: Char): Boolean = legalNonAlpha.contains(c) || legalQuotedAlpha.contains(c)

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
                    throw Rt_Exception.common("rell.time.format:format_text:illegal_character:$current",
                        "Illegal character ('$current') in time format text (within quotes).")
                } else if (!inQuotes && !isLegalUnquoted(current)) {
                    throw Rt_Exception.common("rell.time.format:format_text:illegal_format_character:$current",
                        "Illegal format character ('$current') in time format text.")
                }
                index++
            }
            if (inQuotes) {
                throw Rt_Exception.common("rell.time.format:format_text:unclosed_quoted_region",
                    "Unclosed quoted region in time format text.")
            }
            return format
        }
    }
}
