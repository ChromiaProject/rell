/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.futures

import net.postchain.rell.base.utils.ImmList

interface FcPromise<T> {
    fun future(): FcFuture<T>
    fun setResult(value: T)
}

sealed class FcFuture<out T> {
    abstract fun getResult(): T
}

object FcFutures {
    fun <T> value(value: T): FcFuture<T> = FcInternals.valueFuture(value)
}

class FcPair<T1, T2>(val v1: T1, val v2: T2)

@JvmName("CfPair2_component1")
operator fun <T1, T2> FcPair<T1, T2>.component1(): T1 = v1

@JvmName("CfPair2_component2")
operator fun <T1, T2> FcPair<T1, T2>.component2(): T2 = v2

interface FcFutureBuilderBase<SelfT: FcFutureBuilderBase<SelfT>> {
    fun computeOnDemand(value: Boolean): SelfT
    fun name(name: String): SelfT
    fun attachment(data: Any?): SelfT
}

interface FcFutureBuilder: FcFutureBuilderBase<FcFutureBuilder> {
    fun <B> after(f: FcFuture<B>): FcFutureBuilderN<B>
    fun <B> after(f: List<FcFuture<B>>): FcFutureBuilderN<List<B>>
    fun <K: Any, B> after(f: Map<K, FcFuture<B>>): FcFutureBuilderN<Map<K, B>>
    fun <T> compute(block: () -> T): FcFuture<T>
    fun <T> delegate(block: () -> FcFuture<T>): FcFuture<T>
}

interface FcFutureBuilderN<R>: FcFutureBuilderBase<FcFutureBuilderN<R>> {
    fun <B> after(f: FcFuture<B>): FcFutureBuilderN<FcPair<R, B>>
    fun <B> after(f: List<FcFuture<B>>): FcFutureBuilderN<FcPair<R, List<B>>>
    fun <K: Any, B> after(f: Map<K, FcFuture<B>>): FcFutureBuilderN<FcPair<R, Map<K, B>>>
    fun <T> compute(block: (R) -> T): FcFuture<T>
    fun <T> delegate(block: (R) -> FcFuture<T>): FcFuture<T>
}

abstract class FcException(val code: String, msg: String): RuntimeException(msg)
class FcBasicException(code: String, msg: String): FcException(code, msg)

class FcCycleNode(val name: String, val attachment: Any?)
class FcCycleException(code: String, msg: String, val nodes: ImmList<FcCycleNode>): FcException(code, msg)

interface FcExecutor {
    fun <T> promise(): FcPromise<T>
    fun future(): FcFutureBuilder
}

sealed class FcManager: FcExecutor {
    abstract fun execute()
    abstract fun finish()

    companion object {
        fun create(allowRecursiveExecution: Boolean = false): FcManager = FcInternals.createManager(allowRecursiveExecution)
    }
}
