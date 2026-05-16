/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils

object Lib_Type_Iterable {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("iterable", abstract = true, hidden = true, since = "0.10.6") {
            """
                A generic type for sequences that can be iterated over, such as collections, ranges and maps.
            """.comment()
            generic("T")

            function("join_to_text", "text", pure = true, since = "0.13.10") {
                """
                    Generate a textual representation of this iterable.

                    An optional separator, prefix and postfix can be provided. One can also provide a `limit: integer?`.
                    If there are more elements in the result than `limit`, the elements whose indices exceed `limit`
                    are omitted, and the passed `truncated: text` is included instead.

                    Examples:
                    - `[1, 2, 3].join_to_text()` returns `'1, 2, 3'`.
                    - `[1, 2, 3].join_to_text('_')` returns `'1_2_3'`.
                    - `[1, 2, 3].join_to_text('*', '(', ')')` returns `'(1*2*3)'`.
                    - `list<T>().join_to_text('!', '(', ')')` returns `'()'` (where `T` is a valid type).
                    - `range(10).join_to_text('', '', '', 5)` returns `'01234...'`.
                    - `range(10).join_to_text('', '', '', 5, 'more')` returns `'01234more'`.

                    Where the function `even` is defined:
                    ```
                    function even(x: integer): text {
                       return if (x % 2 == 0) 'EVEN' else 'ODD';
                    }
                    ```

                    Then:
                    - `range(10).join_to_text('->', '{', '}', 5, '...', even(*))` returns `{EVEN->ODD->EVEN->ODD->EVEN->...}`.

                    @return a textual representation of this iterable
                """.comment()
                val separator by paramOpt(
                    Rt_TextValue,
                    comment = "the separator between the elements, defaults to `', '`",
                )
                val prefix by paramOpt(Rt_TextValue, comment = "the prefix text, default empty")
                val postfix by paramOpt(Rt_TextValue, comment = "the postfix text, default empty")
                val limit by paramOpt(
                    "integer?",
                    cast = Rt_Value,
                    comment = "The maximum number of elements to include in the result. If the number of " +
                        "elements exceeds `limit`, the value of the `truncated` argument is appended (after the " +
                        "last separator occurrence and before the postfix). Defaults to `null`, meaning no limit.",
                )
                val truncated by paramOpt(
                    Rt_TextValue,
                    comment = "the text to be appended if the number of elements is greater than `limit`, " +
                        "defaults to `'...'`",
                )
                val transform by paramOpt(
                    "(T) -> text",
                    cast = Rt_FunctionValue,
                    comment = "a transformation function to apply to each element before joining, " +
                        "defaults to `to_text()`",
                )
                val self by self(Rt_IterableValue)
                body(Rt_TextValue) {
                    val joinedString = joinToTextCall(self, separator, prefix, postfix, limit, truncated, transform)
                    joinedString
                }
            }
        }
    }

    private fun joinToTextCall(
        self: Iterable<Rt_Value>,
        separator: Rt_TextValue?,
        prefix: Rt_TextValue?,
        postfix: Rt_TextValue?,
        limitValue: Rt_Value?,
        truncated: Rt_TextValue?,
        transformFn: Rt_FunctionValue?,
    ): String {
        val separatorStr = separator?.value ?: ", "
        val prefixStr = prefix?.value ?: ""
        val postfixStr = postfix?.value ?: ""
        val limit = extractLimit(limitValue)
        val truncatedStr = truncated?.value ?: "..."
        val transform = if (transformFn != null) {
            rtValue -> callTransformFunction(transformFn, rtValue)
        } else {
            ::defaultTransform
        }
        return self.joinToString(separatorStr, prefixStr, postfixStr, limit, truncatedStr, transform)
    }

    private fun defaultTransform(value: Rt_Value): String {
        return value.str()
    }

    private fun callTransformFunction(transform: Rt_FunctionValue, rtValue: Rt_Value): String {
        return (transform.call(listOf(rtValue)) as Rt_TextValue).value
    }

    private fun extractLimit(value: Rt_Value?): Int {
        if (value == null || value === Rt_NullValue) {
            return -1
        }

        val limit = (value as Rt_IntValue).value
        Rt_Utils.checkRange(limit, 0, Int.MAX_VALUE.toLong()) {
            val code = "fn:join_to_text:incorrect_limit:$limit"
            val msg = "Limit needs to be an integer between 0 and ${Int.MAX_VALUE}"
            code to msg
        }
        return limit.toInt()
    }
}
