/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.futures

import net.postchain.rell.base.utils.*

private class FcCodeMsg(val code: String, val msg: String)

private infix fun String.toFcCodeMsg(that: String): FcCodeMsg = FcCodeMsg(this, that)

internal object FcInternals {
    fun <T> valueFuture(value: T): FcFuture<T> {
        return FcFuture_Value(value)
    }

    fun createManager(allowRecursiveExecution: Boolean): FcManager {
        return FcManagerImpl(FcManagerCore(allowRecursiveExecution))
    }
}

private abstract class FcBefore<R> {
    abstract fun futures(): Iterable<FcFuture<*>>
    abstract fun result(): R
}

private class FcBefore_Value<R>(private val future: FcFuture<R>): FcBefore<R>() {
    override fun futures() = immListOf(future)

    override fun result(): R {
        return future.getResult()
    }
}

private class FcBefore_List<R>(private val futures: ImmList<FcFuture<R>>): FcBefore<List<R>>() {
    override fun futures() = futures

    override fun result(): List<R> {
        return futures.map { it.getResult() }
    }
}

private class FcBefore_Map<K, R>(private val futures: ImmMap<K, FcFuture<R>>): FcBefore<Map<K, R>>() {
    override fun futures() = futures.values

    override fun result(): Map<K, R> {
        return futures.mapValues { it.value.getResult() }
    }
}

private class FcBefore_Pair<A, B>(
    private val a: FcBefore<A>,
    private val b: FcBefore<B>,
): FcBefore<FcPair<A, B>>() {
    override fun futures(): Iterable<FcFuture<*>> = a.futures() + b.futures()

    override fun result(): FcPair<A, B> {
        val aRes = a.result()
        val bRes = b.result()
        return FcPair(aRes, bRes)
    }
}

private class FcFutureSettings(
    val lazyName: Lazy<String>?,
    val attachment: Any?,
    val computeOnDemand: Boolean,
) {
    fun copy(
        lazyName: Lazy<String>? = this.lazyName,
        attachment: Any? = this.attachment,
        computeOnDemand: Boolean = this.computeOnDemand,
    ): FcFutureSettings {
        return if (
            lazyName === this.lazyName
            && attachment === this.attachment
            && computeOnDemand == this.computeOnDemand
        ) this else FcFutureSettings(
            lazyName = lazyName,
            attachment = attachment,
            computeOnDemand = computeOnDemand,
        )
    }

    companion object {
        val DEFAULT = FcFutureSettings(
            lazyName = null,
            attachment = null,
            computeOnDemand = false,
        )
    }
}

private abstract class FcFutureBuilderBase_Impl<SelfT: FcFutureBuilderBase<SelfT>>(
    protected val core: FcManagerCore,
    protected val settings: FcFutureSettings,
): FcFutureBuilderBase<SelfT> {
    protected abstract fun updateSettings(settings2: FcFutureSettings): SelfT

    final override fun name(name: String): SelfT {
        checkNull(settings.lazyName)
        val settings2 = settings.copy(lazyName = lazyOf(name))
        return updateSettings(settings2)
    }

    final override fun attachment(data: Any?): SelfT {
        checkNull(settings.attachment)
        val settings2 = settings.copy(attachment = data)
        return updateSettings(settings2)
    }

    final override fun computeOnDemand(value: Boolean): SelfT {
        val settings2 = settings.copy(computeOnDemand = value)
        return updateSettings(settings2)
    }
}

private class FcFutureBuilder_Impl(
    core: FcManagerCore,
    settings: FcFutureSettings,
): FcFutureBuilderBase_Impl<FcFutureBuilder>(core, settings), FcFutureBuilder {
    override fun <B> after(f: FcFuture<B>) = after0(FcBefore_Value(f))
    override fun <B> after(f: List<FcFuture<B>>) = after0(FcBefore_List(f.toImmList()))
    override fun <K: Any, B> after(f: Map<K, FcFuture<B>>) = after0(FcBefore_Map(f.toImmMap()))

    private fun <B> after0(before: FcBefore<B>): FcFutureBuilderN<B> {
        return FcFutureBuilderN_Impl(core, settings, before)
    }

    override fun updateSettings(settings2: FcFutureSettings): FcFutureBuilder {
        return if (settings2 === settings) this else FcFutureBuilder_Impl(core, settings2)
    }

    override fun <T> compute(block: () -> T): FcFuture<T> {
        return delegate {
            val value = block()
            FcFutures.value(value)
        }
    }

    override fun <T> delegate(block: () -> FcFuture<T>): FcFuture<T> {
        return core.future(immListOf(), settings, block)
    }
}

private class FcFutureBuilderN_Impl<R>(
    core: FcManagerCore,
    settings: FcFutureSettings,
    private val before: FcBefore<R>,
): FcFutureBuilderBase_Impl<FcFutureBuilderN<R>>(core, settings), FcFutureBuilderN<R> {
    override fun <B> after(f: FcFuture<B>) = after0(FcBefore_Value(f))
    override fun <B> after(f: List<FcFuture<B>>) = after0(FcBefore_List(f.toImmList()))
    override fun <K: Any, B> after(f: Map<K, FcFuture<B>>) = after0(FcBefore_Map(f.toImmMap()))

    private fun <B> after0(before2: FcBefore<B>): FcFutureBuilderN<FcPair<R, B>> {
        return FcFutureBuilderN_Impl(core, settings, FcBefore_Pair(before, before2))
    }

    override fun updateSettings(settings2: FcFutureSettings): FcFutureBuilderN<R> {
        return if (settings2 === settings) this else FcFutureBuilderN_Impl(core, settings2, before)
    }

    override fun <T> compute(block: (R) -> T): FcFuture<T> {
        return delegate { arg ->
            val value = block(arg)
            FcFutures.value(value)
        }
    }

    override fun <T> delegate(block: (R) -> FcFuture<T>): FcFuture<T> {
        return core.future(before.futures(), settings) {
            val arg = before.result()
            block(arg)
        }
    }
}

private class FcManagerImpl(private val core: FcManagerCore): FcManager() {
    override fun <T> promise(): FcPromise<T> {
        return core.promise()
    }

    override fun future(): FcFutureBuilder {
        return FcFutureBuilder_Impl(core, FcFutureSettings.DEFAULT)
    }

    // Can be called repeatedly to process all newly added futures.
    override fun execute() {
        core.execute()
    }

    override fun finish() {
        core.finish()
    }
}

private class FcManagerCore(
    private val allowRecursiveExecution: Boolean,
) {
    private val activeFutures = mutableSetOf<FcFuture_Computable<*>>()
    private val blockedFutures = mutableSetOf<FcFuture_Computable<*>>()
    private val computingFutures = UniqueStack<FcFuture_Later<*>>()
    private var futureNameCounter = 0L
    private var finished = false
    private var executing = false

    fun <T> promise(): FcPromise<T> {
        val name = generateName("promise")
        return FcPromise_Impl(this, name)
    }

    fun <T> future(
        before: Iterable<FcFuture<*>>,
        settings: FcFutureSettings,
        block: () -> FcFuture<T>,
    ): FcFuture<T> {
        val inputs = prepareInputs(before)
        val name = settings.lazyName ?: generateName("future")
        val future = FcFuture_Computable(this, name, settings.attachment, settings.computeOnDemand, inputs, block)
        if (inputs == null) {
            activeFutures.add(future)
        } else {
            blockedFutures.add(future)
        }
        return future
    }

    private fun generateName(kind: String): Lazy<String> {
        val id = futureNameCounter++
        return lazy { "$kind-$id" }
    }

    private fun prepareInputs(before: Iterable<FcFuture<*>>): MutableSet<FcFuture_Later<*>>? {
        return before
            .mapNotNull {
                when (it) {
                    is FcFuture_Value<*> -> null
                    is FcFuture_Later<*> -> {
                        check(it.core === this)
                        if (it.isCompleted()) null else it
                    }
                }
            }
            .toMutableSet()
            .let {
                if (it.isEmpty()) null else it
            }
    }

    fun execute() {
        check(!finished)
        execute0()
    }

    fun finish() {
        check(!finished)
        finished = true
        execute0()
    }

    private fun execute0() {
        val oldExecuting = executing
        check(!oldExecuting || allowRecursiveExecution)
        check(computingFutures.isEmpty() || allowRecursiveExecution)

        executing = true
        try {
            while (activeFutures.isNotEmpty()) {
                val first = activeFutures.first()
                executeFuture0(first)
            }
        } finally {
            executing = oldExecuting
        }

        if (!oldExecuting) {
            processBlockedFutures()
        }
    }

    private fun processBlockedFutures() {
        if (blockedFutures.isEmpty()) {
            return
        }

        for (future in blockedFutures) {
            processBlockedFuture(future)
        }

        val codeMsg = FcPrivate.futuresCodeMsg(blockedFutures)
        throw FcBasicException("blocked:${codeMsg.code}", "Blocked futures: ${codeMsg.msg}")
    }

    private fun processBlockedFuture(future: FcFuture_Later<*>) {
        val set = mutableSetOf(future)
        var last = future

        while (true) {
            check(!last.isCompleted())
            val input = last.getFirstInput()
            when (last) {
                is FcFuture_Promise<*> -> {
                    checkNull(input)
                    val codeMsg = FcPrivate.futuresCodeMsg(set)
                    val msg = "Future(s) blocked by a promise: ${codeMsg.msg}"
                    throw FcBasicException("blocked_promise:${codeMsg.code}", msg)
                }
                is FcFuture_Computable<*> -> {
                    checkNotNull(input)
                    if (set.add(input)) {
                        last = input
                    } else {
                        FcPrivate.cycleError(set, input)
                    }
                }
            }
        }
    }

    fun addActiveFuture(future: FcFuture_Computable<*>) {
        check(!future.isCompleted())
        check(future.core === this)
        check(blockedFutures.remove(future))
        check(activeFutures.add(future))
    }

    fun addBlockedFuture(future: FcFuture_Computable<*>) {
        check(!future.isCompleted())
        check(future.core === this)
        check(future !in activeFutures)
        check(blockedFutures.add(future))
    }

    fun executeOnDemand(future: FcFuture_Computable<*>) {
        executeFuture0(future)
    }

    fun computingStart(future: FcFuture_Later<*>) {
        check(!future.isCompleted())
        check(future.core === this)

        if (!computingFutures.push(future)) {
            val list = computingFutures.toList()
            FcPrivate.cycleError(list, future)
        }
    }

    fun computingEnd(future: FcFuture_Later<*>) {
        computingFutures.pop(future)
    }

    private fun executeFuture0(future: FcFuture_Computable<*>) {
        check(!future.isCompleted())
        check(future.core === this)

        computingStart(future)
        try {
            check(activeFutures.remove(future))
            future.execute()
        } finally {
            computingEnd(future)
        }
    }
}

private sealed class FcFuture_Base<T>: FcFuture<T>() {
    abstract fun getName(): String
    abstract fun getCycleNode(): FcCycleNode
}

private class FcFuture_Value<T>(val value: T): FcFuture_Base<T>() {
    override fun getResult() = value
    override fun getName() = "<value>"
    override fun getCycleNode() = FcCycleNode("<value>", null)
}

private abstract class FcFuture_Later<T>(
    val core: FcManagerCore,
    private val lazyName: Lazy<String>,
    private val attachment: Any?,
): FcFuture_Base<T>() {
    abstract fun isCompleted(): Boolean
    abstract fun getResult0(): T
    abstract fun getFirstInput(): FcFuture_Later<*>?
    abstract fun addOutput(future: FcFuture_Computable<*>)

    final override fun getName(): String = lazyName.value

    final override fun getCycleNode(): FcCycleNode {
        val name = lazyName.value
        return FcCycleNode(name = name, attachment = attachment)
    }
}

private class FcPromise_Impl<T>(
    core: FcManagerCore,
    lazyName: Lazy<String>,
): FcPromise<T> {
    private val future = FcFuture_Promise<T>(core, lazyName)

    override fun future(): FcFuture<T> = future

    override fun setResult(value: T) {
        future.setResult(value)
    }
}

private class FcFuture_Promise<T>(
    core: FcManagerCore,
    lazyName: Lazy<String>,
): FcFuture_Later<T>(core, lazyName, null) {
    private var stateVar: State? = State(this)
    private var result: T? = null

    override fun isCompleted(): Boolean {
        return stateVar == null
    }

    fun setResult(value: T) {
        val state = checkNotNull(stateVar)
        checkNull(result)
        stateVar = null
        result = value
        state.completed()
    }

    @Suppress("UNCHECKED_CAST")
    override fun getResult(): T {
        checkNull(stateVar) { "Promise not set" }
        return result as T
    }

    override fun getResult0(): T = getResult()

    override fun getFirstInput() = null

    override fun addOutput(future: FcFuture_Computable<*>) {
        check(future.core === core)
        val state = checkNotNull(stateVar)
        state.addOutput(future)
    }

    private class State(private val thisFuture: FcFuture_Promise<*>) {
        private var outputs: MutableList<FcFuture_Computable<*>>? = null

        fun addOutput(future: FcFuture_Computable<*>) {
            var outs = outputs
            if (outs == null) {
                outs = mutableListOf()
                outputs = outs
            }
            outs.add(future)
        }

        fun completed() {
            for (future in outputs.orEmpty()) {
                future.inputCompleted(thisFuture)
            }
        }
    }
}

private class FcFuture_Computable<T>(
    core: FcManagerCore,
    lazyName: Lazy<String>,
    attachment: Any?,
    computeOnDemand: Boolean,
    inputs: MutableSet<FcFuture_Later<*>>?,
    block: () -> FcFuture<T>,
): FcFuture_Later<T>(core, lazyName, attachment) {
    private var stateVar: State<T>? = State_Normal(CommonState(this, core, computeOnDemand, null), inputs, block)
    private var result: T? = null

    override fun isCompleted(): Boolean = stateVar == null

    override fun getResult(): T {
        val state = stateVar
        if (state != null) {
            state.computeResult()
            checkNull(stateVar)
        }
        @Suppress("UNCHECKED_CAST") val res = result as T
        return res
    }

    override fun getResult0(): T {
        checkNull(stateVar)
        @Suppress("UNCHECKED_CAST") val res = result as T
        return res
    }

    override fun getFirstInput(): FcFuture_Later<*>? {
        val state = checkNotNull(stateVar)
        return state.getFirstInput()
    }

    override fun addOutput(future: FcFuture_Computable<*>) {
        check(future.core === core)
        val state = checkNotNull(stateVar)
        state.addOutput(future)
    }

    fun setResult(value: T) {
        checkNotNull(stateVar)
        checkNull(result)
        stateVar = null
        result = value
    }

    private fun setState(state: State<T>) {
        checkNotNull(stateVar)
        checkNull(result)
        stateVar = state
    }

    fun inputCompleted(future: FcFuture_Later<*>) {
        val state = checkNotNull(stateVar)
        state.inputCompleted(future)
    }

    fun execute() {
        val state = checkNotNull(stateVar)
        state.execute()
    }

    private class CommonState<T>(
        val thisFuture: FcFuture_Computable<T>,
        val core: FcManagerCore,
        val computeOnDemand: Boolean,
        private var outputs: MutableList<FcFuture_Computable<*>>?,
    ) {
        fun addOutput(outputFuture: FcFuture_Computable<*>) {
            check(outputFuture.core === core)
            var outs = outputs
            if (outs == null) {
                outs = mutableListOf()
                outputs = outs
            }
            outs.add(outputFuture)
        }

        fun completeFuture(value: T) {
            thisFuture.setResult(value)
            for (outputFuture in outputs.orEmpty()) {
                outputFuture.inputCompleted(thisFuture)
            }
        }
    }

    private abstract class State<T>(
        protected val commonState: CommonState<T>,
    ) {
        protected val thisFuture: FcFuture_Computable<T> = commonState.thisFuture
        protected val core = commonState.core

        abstract fun getFirstInput(): FcFuture_Later<*>?
        abstract fun inputCompleted(inputFuture: FcFuture_Later<*>)
        abstract fun execute()

        fun addOutput(outputFuture: FcFuture_Computable<*>) {
            commonState.addOutput(outputFuture)
        }

        protected fun completeFuture(value: T) {
            commonState.completeFuture(value)
        }

        fun computeResult() {
            if (!commonState.computeOnDemand) {
                val name = thisFuture.getName()
                val msg = "Future has not been computed yet (compute-on-demand is disabled): $name"
                throw FcBasicException("no_result:$name", msg)
            }

            computeResultRecursive(thisFuture)
        }

        private fun computeResultRecursive(future: FcFuture_Computable<*>) {
            check(!future.isCompleted())
            while (!future.isCompleted()) {
                val input = future.getFirstInput()
                if (input != null) {
                    computeResultInput(future, input)
                } else {
                    core.executeOnDemand(future)
                }
            }
        }

        private fun computeResultInput(future: FcFuture_Computable<*>, input: FcFuture_Later<*>) {
            when (input) {
                is FcFuture_Promise<*> -> {
                    val name = future.getName()
                    val msg = "Future depends on an unresolved promise: $name"
                    throw FcBasicException("on_demand:promise_unset:$name", msg)
                }
                is FcFuture_Computable<*> -> {
                    core.computingStart(future)
                    try {
                        computeResultRecursive(input)
                    } finally {
                        core.computingEnd(future)
                    }
                }
            }
        }
    }

    private class State_Normal<T>(
        commonState: CommonState<T>,
        private var inputs: MutableSet<FcFuture_Later<*>>?,
        private val block: () -> FcFuture<T>,
    ): State<T>(commonState) {
        private var computing = false

        init {
            val ins = inputs
            if (ins != null) {
                check(ins.isNotEmpty())
                for (inputFuture in ins) {
                    inputFuture.addOutput(thisFuture)
                }
            }
        }

        override fun getFirstInput(): FcFuture_Later<*>? {
            val ins = inputs
            return ins?.first()
        }

        override fun inputCompleted(inputFuture: FcFuture_Later<*>) {
            val ins = checkNotNull(inputs)
            check(ins.remove(inputFuture))

            if (ins.isEmpty()) {
                core.addActiveFuture(thisFuture)
                inputs = null
            }
        }

        override fun execute() {
            checkNull(inputs) // All inputs must be resolved at this moment.
            check(!computing)
            computing = true

            //TODO handle failure in a better way - the future shall go into a specific state
            when (val resultFuture = block()) {
                is FcFuture_Value<T> -> {
                    completeFuture(resultFuture.value)
                }
                is FcFuture_Later<T> -> {
                    check(resultFuture.core === core)
                    if (resultFuture.isCompleted()) {
                        val value = resultFuture.getResult0()
                        completeFuture(value)
                    } else {
                        val newState = State_Copy(commonState, resultFuture)
                        thisFuture.setState(newState)
                        core.addBlockedFuture(thisFuture)
                    }
                }
            }
        }
    }

    private class State_Copy<T>(
        commonState: CommonState<T>,
        private val input: FcFuture_Later<T>,
    ): State<T>(commonState) {
        private var inputCompleted = false
        private var computing = false

        init {
            check(!input.isCompleted())
            input.addOutput(thisFuture)
        }

        override fun getFirstInput(): FcFuture_Later<*>? {
            return if (inputCompleted) null else input
        }

        override fun inputCompleted(inputFuture: FcFuture_Later<*>) {
            check(inputFuture === input)
            check(!inputCompleted)
            check(input.isCompleted())
            inputCompleted = true
            core.addActiveFuture(thisFuture)
        }

        override fun execute() {
            check(inputCompleted)
            check(!computing)
            computing = true

            check(input.isCompleted())
            val value = input.getResult0()
            completeFuture(value)
        }
    }
}

private object FcPrivate {
    fun cycleError(stack: Collection<FcFuture_Base<*>>, add: FcFuture_Base<*>): Nothing {
        check(add in stack)

        var futures = stack.toList()
        val i = futures.indexOf(add)
        check(i >= 0)
        futures = futures.subList(i, futures.size) + add

        val codeMsg = futuresCodeMsg(futures)
        val nodes = futures.mapToImmList { it.getCycleNode() }

        throw FcCycleException("cycle:${codeMsg.code}", "Cyclic future dependency: ${codeMsg.msg}", nodes)
    }

    fun futuresCodeMsg(futures: Collection<FcFuture_Base<*>>): FcCodeMsg {
        val names = futures.map { it.getName() }
        val listCode = names.joinToString(",")
        val listMsg = names.joinToString(", ")
        return listCode toFcCodeMsg listMsg
    }
}

/** A stack which does not allow duplicates (useful for detecting recursion). */
private class UniqueStack<T: Any> {
    private val set = mutableSetOf<T>()
    private val list = mutableListOf<T>()

    fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    fun push(value: T): Boolean {
        return set.add(value) && list.add(value)
    }

    fun pop(value: T) {
        val last = list.last()
        checkEquals(last, value)
        list.removeLast()
        set.remove(last)
    }

    fun toList(): List<T> {
        return list.toImmList()
    }
}
