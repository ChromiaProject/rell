/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

class RecursionAwareResult<V>(val value: V, val recursion: Boolean = false, val stackOverflow: Boolean = false)

class RecursionAwareCalculator<K, V>(private val maxDepth: Int, private val errorValue: V, private val calculator: (K) -> V) {
    private val cache = mutableMapOf<K, RecursionAwareResult<V>>()
    private var stackTop: StackEntry? = null
    private val stackMap = mutableMapOf<K, StackEntry>()

    fun calculate(key: K): RecursionAwareResult<V> {
        val cached = cache[key]
        if (cached != null) {
            return cached
        }

        val rec = stackMap[key]
        if (rec != null) {
            markStack(stackTop, rec, recursion = true)
            return RecursionAwareResult(errorValue, recursion = true)
        }

        val stackEntry = StackEntry(stackTop)
        if (stackEntry.depth > maxDepth) {
            markStack(stackEntry, null, stackOverflow = true)
            return RecursionAwareResult(errorValue, stackOverflow = true)
        }

        val oldStackTop = stackTop
        stackMap[key] = stackEntry
        stackTop = stackEntry

        val calc = try {
            calculator(key)
        } catch (_: StackOverflowError) {
            markStack(stackEntry, null, stackOverflow = true)
            errorValue
        } finally {
            stackTop = oldStackTop
            stackMap.remove(key)
        }

        val resValue = if (stackEntry.recursion || stackEntry.stackOverflow) errorValue else calc
        val res = RecursionAwareResult(resValue, recursion = stackEntry.recursion, stackOverflow = stackEntry.stackOverflow)

        cache[key] = res
        return res
    }

    private fun markStack(start: StackEntry?, end: StackEntry?, recursion: Boolean = false, stackOverflow: Boolean = false) {
        var cur = start
        while (cur != null) {
            cur.recursion = cur.recursion || recursion
            cur.stackOverflow = cur.stackOverflow || stackOverflow
            if (cur === end) break
            cur = cur.prev
        }
    }

    private inner class StackEntry(val prev: StackEntry?) {
        val depth: Int = if (prev != null) prev.depth + 1 else 1
        var recursion = false
        var stackOverflow = false
    }
}
