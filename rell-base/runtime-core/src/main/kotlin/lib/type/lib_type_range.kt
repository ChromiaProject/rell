/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_RangeValue
import net.postchain.rell.base.runtime.Rt_Value

object Lib_Type_Range {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("range", rrType = RR_Type.Primitive(RR_PrimitiveKind.RANGE), since = "0.6.0") {
            comment("""
                A range of integer values. Ranges represent arithmetic sequences with defined start and end points, and
                a constant difference between consecutive elements. Ranges can be empty or contain any natural number of
                elements.

                Range is a subtype of `iterable<integer>`.
                @see 1. <a href="../iterable/index.html"><code>iterable</code> - Rell Standard Library</a>
            """)
            parent(type = "iterable<integer>")

            constructor(pure = true, since = "0.6.0") {
                comment("""
                    Construct a range, starting at `0` (inclusive), ending at `end` (exclusive), with a step size of `1`.

                    Examples:

                    - `list(range(0))` returns `[]`.
                    - `list(range(1))` returns `[0]`.
                    - `list(range(2))` returns `[0, 1]`.
                    - `list(range(10))` returns `[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]`.

                    Note that `range(x)` is equivalent to `range(0, x, 1)`.
                    @throws exception if `end < 0`
                """)
                param("end", "integer", comment = "end value (exclusive) for this range")
                body { a ->
                    calcRange(0, (a as Rt_IntValue).value, 1)
                }
            }

            constructor(pure = true, since = "0.6.0") {
                comment("""
                    Construct a range, starting at `start` (inclusive), ending at `end` (exclusive), with a "step size"
                    (i.e. difference between consecutive values) of `step`.

                    If `start > end`, then `step` must be negative. Conversely, if `start < end`, the `step` must be
                    positive. `step` cannot be `0`.

                    Examples:

                    - `list(range(1, 23, 3))` returns `[1, 4, 7, 10, 13, 16, 19, 22]`.
                    - `list(range(20, 1, -2))` returns `[20, 18, 16, 14, 12, 10, 8, 6, 4, 2]`.
                    - `list(range(-2, -8, -1))` returns `[-2, -3, -4, -5, -6, -7]`.
                    - `list(range(3, 3, 7))` returns `[]`. Indeed, for all `x`, and for all `y != 0`, `range(x, x, y)`
                    returns `[]`.

                    Note that `range(0, x, 1)` is equivalent to `range(x)`.
                    @throws exception when:
                    - `step == 0`
                    - `start > end` and `step > 0`
                    - `start < end` and `step < 0`
                """)
                param("start", "integer", comment = "start value for this range (inclusive)")
                param("end", "integer", comment = "end value for this range (exclusive)")
                param("step", "integer", arity = L_ParamArity.ZERO_ONE, comment = "step size for this range")
                bodyOpt2 { a, b, c ->
                    calcRange((a as Rt_IntValue).value, (b as Rt_IntValue).value, (c as? Rt_IntValue)?.value ?: 1)
                }
            }
        }
    }

    private fun calcRange(start: Long, end: Long, step: Long): Rt_Value {
        if (step == 0L || (step > 0 && start > end) || (step < 0 && start < end)) {
            throw Rt_Exception.common("fn_range_args:$start:$end:$step",
                "Invalid range: start = $start, end = $end, step = $step")
        }
        return Rt_RangeValue(start, end, step)
    }
}

