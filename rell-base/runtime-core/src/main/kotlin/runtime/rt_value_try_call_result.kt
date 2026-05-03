/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

internal class Rt_TryCallResultValue(
    override val type: Rt_ValueClass<*>,
    val valueOrNull: Rt_Value?,
    val requireMessageOrNull: String?,
): Rt_ValueBase() {
    init {
        check(valueOrNull != null || requireMessageOrNull != null) { "both value and require message are null" }
    }

    val isError: Boolean
        get() = valueOrNull == null

    override val name
        get() = Companion.name

    override fun str(format: Rt_StrFormat): String = buildString {
        append(type.name)
        append('{')
        if (isError) {
            append("error=$requireMessageOrNull")
        } else {
            append("value=${valueOrNull?.str(format)}")
        }
        append('}')
    }

    override fun strCode(showTupleFieldNames: Boolean): String = buildString {
        append(type.name)
        append('[')
        if (isError) {
            append("error=$requireMessageOrNull")
        } else {
            append("value=${valueOrNull?.strCode(showTupleFieldNames)}")
        }
        append(']')
    }

    override fun strPretty(indent: Int): String = buildString {
        val indentStr = "    ".repeat(indent)
        append(type.name)
        append('{')
        append("\n$indentStr    value_or_null = ")
        append(valueOrNull?.strPretty(indent + 1))
        append("\n$indentStr    require_message_or_null = ")
        append(requireMessageOrNull)
        append("\n$indentStr}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rt_TryCallResultValue) return false
        if (valueOrNull != other.valueOrNull) return false
        if (requireMessageOrNull != other.requireMessageOrNull) return false
        return true
    }

    override fun hashCode(): Int {
        var result = valueOrNull?.hashCode() ?: 0
        result = 31 * result + (requireMessageOrNull?.hashCode() ?: 0)
        return result
    }

    companion object: Rt_ValueClass<Rt_TryCallResultValue> {
        override val name
            get() = "try_call_result"

        override val klass = Rt_TryCallResultValue::class

        private const val VALUE_TYPE = "TRY_CALL_RESULT"

        fun get(v: Rt_Value): Rt_TryCallResultValue =
            v as? Rt_TryCallResultValue ?: throw Rt_ValueTypeError.exception(VALUE_TYPE, v.name)
    }
}
