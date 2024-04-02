/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.Rt_FunctionValue
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

object Lib_Type_Iterable {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("iterable", abstract = true, hidden = true, since = "0.10.6") {
            generic("T")

            function("join_to_text", "text", pure = true, since = "0.13.10") {
                comment("""
                     Creates a text from all the elements separated using `separator` and using the given `prefix` and
                     `postfix` if supplied.

                     If the iterable is large, you can specify a non-negative value of `limit`, in which case
                     only the first `limit` of elements will be appended, followed by the `truncated` text
                     (which defaults to "...").
                """)
                param("separator", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("The separator between the elements. The default is ', '")
                }
                param("prefix", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("The text to be prefixed to the resulting text. The default is an empty text")
                }
                param("postfix", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("The text to be appended to the resulting text. The default is an empty text")
                }
                param("limit", "integer?", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        The maximum number of elements to include in the result.
                        If the number of elements exceeds this `limit`,
                        the truncated text is appended. The default is `null`, which means no limit.
                    """)
                }
                param("truncated", "text", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        The text to be appended if the number of elements exceeds the specified `limit`.
                        The default is '...'
                    """)
                }
                param("transform", "(T) -> text", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        A transformation function to apply to each element before joining.
                        The default is `to_text` for all elements
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
            code toCodeMsg msg
        }
        return limit.toInt()
    }
}
