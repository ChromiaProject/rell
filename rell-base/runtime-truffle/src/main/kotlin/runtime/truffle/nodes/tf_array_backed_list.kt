/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import net.postchain.rell.base.runtime.Rt_Value

/**
 * Final, immutable, `RandomAccess` view over a pre-sized `Array<Rt_Value>` exposed as
 * `List<Rt_Value>`.
 *
 * Used by the sys-fn dispatch path ([Tf_SysCallNode], [Tf_MemberAccessNode.SysMemberFnCall]) to
 * hand `R_SysFunction.call` a `List<Rt_Value>` without allocating an `ArrayList` plus its backing
 * `Object[]`. The backing array comes straight from a `@ExplodeLoop` argument-evaluation pass,
 * so under PE every slot is a known shape and the wrapper folds away to direct array reads.
 *
 * # Why not `Arrays.asList(*args)` / `java.util.List.of(*args)`
 *
 * Both Kotlin spread-call sites (`*array`) emit a defensive `Arrays.copyOf` of the source array
 * before invoking the varargs target — the same allocation we're trying to eliminate. Calling the
 * varargs JDK methods *without* a spread (passing the array as a single `Object[]` argument)
 * works only when the static element type is `Any?` / `Object`, which leaks the unchecked-cast
 * surface into every call site. The dedicated wrapper avoids both pitfalls.
 *
 * # Why a custom class instead of `Arrays.asList(array)`
 *
 * `Arrays$ArrayList` is non-final, synchronized at `subList`/`iterator` boundaries through its
 * shared `AbstractList` machinery, and reaches into `Arrays.copyOf` on `toArray()`. A dedicated
 * final class lets PE see a single concrete shape at the sys-fn boundary and pins exactly the
 * methods we need (`size`, `get`) to direct array reads.
 */
internal class Tf_ArrayBackedList(private val array: Array<Rt_Value>) : AbstractList<Rt_Value>(), RandomAccess {
    override val size: Int
        get() = array.size

    override fun get(index: Int): Rt_Value = array[index]
}
