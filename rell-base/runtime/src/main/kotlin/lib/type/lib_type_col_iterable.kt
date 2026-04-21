/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_FunctionValue
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

object Lib_Type_Iterable {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("iterable", abstract = true, hidden = true, since = "0.10.6") {
            comment("""
                A generic type for sequences that can be iterated over, such as collections, ranges and maps.
            """)
            generic("T")

            function("join_to_text", "text", pure = true, since = "0.13.10") {
                comment("""
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
                """)
                param("separator", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("the separator between the elements, defaults to `', '`")
                }
                param("prefix", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("the prefix text, default empty")
                }
                param("postfix", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("the postfix text, default empty")
                }
                param("limit", "integer?", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        The maximum number of elements to include in the result. If the number of elements exceeds
                        `limit`, the value of the `truncated` argument is appended (after the last separator occurrence
                        and before the postfix). Defaults to `null`, meaning no limit.
                    """)
                }
                param("truncated", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        the text to be appended if the number of elements is greater than `limit`, defaults to `'...'`
                    """)
                }
                param("transform", "(T) -> text", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        a transformation function to apply to each element before joining, defaults to `to_text()`
                    """)
                }
                bodyContextN { ctx, args ->
                    Rt_Utils.checkRange(args.size, 1, 7)
                    val joinedString = joinToTextCall(ctx, args[0].asIterable(), args)
                    Rt_TextValue.get(joinedString)
                }
            }
        }
    }

    private fun joinToTextCall(
        ctx: Rt_CallContext,
        self: Iterable<Rt_Value>,
        args: List<Rt_Value>
    ): String {
        val separator = args.getOrNull(1)?.asString() ?: ", "
        val prefix = args.getOrNull(2)?.asString() ?: ""
        val postfix = args.getOrNull(3)?.asString() ?: ""
        val limit = extractLimit(args.getOrNull(4))
        val truncated = args.getOrNull(5)?.asString() ?: "..."
        val fnValue = args.getOrNull(6)?.asFunction()
        val transform = if (fnValue != null) {
            rtValue -> callTransformFunction(ctx, fnValue, rtValue)
        } else {
            ::defaultTransform
        }
        return self.asIterable().joinToString(separator, prefix, postfix, limit, truncated, transform)
    }

    private fun defaultTransform(value: Rt_Value): String {
        return value.str()
    }

    private fun callTransformFunction(ctx: Rt_CallContext, transform: Rt_FunctionValue, rtValue: Rt_Value): String {
        return transform.call(ctx, listOf(rtValue)).asString()
    }

    private fun extractLimit(value: Rt_Value?): Int {
        if (value == null || value is Rt_NullValue) {
            return -1
        }

        val limit = value.asInteger()
        Rt_Utils.checkRange(limit, 0, Int.MAX_VALUE.toLong()) {
            val code = "fn:join_to_text:incorrect_limit:$limit"
            val msg = "Limit needs to be an integer between 0 and ${Int.MAX_VALUE}"
            code to msg
        }
        return limit.toInt()
    }
}
