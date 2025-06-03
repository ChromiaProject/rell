/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

class C_RecursionSafeContext {
    private var top = true

    fun <K, A, R> createCalculator(
            keyToResult: (K) -> C_RecursionSafeResult<A, R>,
            recursionError: (A) -> C_Error
    ): C_RecursionSafeCalculator<K, A, R> {
        return InternalRecursionSafeCalculator(keyToResult, recursionError)
    }

    private inner class InternalRecursionSafeCalculator<K, A, R>(
            private val keyToResult: (K) -> C_RecursionSafeResult<A, R>,
            recursionError: (A) -> C_Error
    ): C_RecursionSafeCalculator<K, A, R> {
        private val recursionResult = C_RecursionSafeResult.error<A, R>(recursionError)
        private val stack = mutableSetOf<K>()
        private val cache = mutableMapOf<K, C_RecursionSafeResult<A, R>>()

        override fun calculate(key: K): C_RecursionSafeResult<A, R> {
            val cachedResult = cache[key]
            if (cachedResult != null) {
                return cachedResult
            }

            if (!stack.add(key)) {
                return recursionResult
            }

            val oldTop = top
            top = false
            val result = try {
                keyToResult(key)
            } finally {
                top = oldTop
                stack.remove(key)
            }

            if (oldTop) {
                // A simple approach: not caching if not a top call, because then recursion may affect the result.
                // More efficient solution is to detect whether there actually was a "higher" recursion during the calculation.
                cache[key] = result
            }

            return result
        }
    }
}

interface C_RecursionSafeCalculator<K, A, R> {
    fun calculate(key: K): C_RecursionSafeResult<A, R>
}

class C_RecursionSafeResult<A, R> private constructor(val value: R?, val error: (A) -> C_Error) {
    constructor(value: R): this(value, { throw IllegalStateException("No errors") })

    companion object {
        fun <A, R> error(error: (A) -> C_Error): C_RecursionSafeResult<A, R> = C_RecursionSafeResult(null, error)
    }
}
