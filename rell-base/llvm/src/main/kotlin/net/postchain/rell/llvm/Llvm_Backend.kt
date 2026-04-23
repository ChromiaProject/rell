/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.model.rr.RR_Expr
import net.postchain.rell.base.model.rr.RR_FunctionCallTarget
import net.postchain.rell.base.model.rr.RR_FunctionDefinition
import net.postchain.rell.base.model.rr.RR_FunctionParam
import net.postchain.rell.base.model.rr.RR_GlobalConstantDefinition
import net.postchain.rell.base.model.rr.RR_OperationDefinition
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.Rt_DefinitionContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_Frame
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_InterpreterImpl
import net.postchain.rell.base.runtime.Rt_StdlibEnv
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.utils.toImmMap
import net.postchain.rell.serialization.serializeRellApp

/**
 * LLVM backend for Rell.
 *
 * Implements [Rt_Interpreter] as a peer to [Rt_InterpreterImpl] in `runtime-interpreter`. Both
 * backends consume the same [RR_App], use the same stdlib, hit the same database — they differ
 * only in dispatch.
 *
 * # Strategy
 *
 * The whole compiled application is serialised to FlatBuffers once and handed to the native
 * side. On every first call to a user function, the C++ JIT (see `jni_bridge.cpp`) walks the
 * function's `FunctionBody` straight from the FlatBuffers buffer, emits LLVM IR for the
 * narrow integer-arithmetic slice the prototype supports, JIT-compiles it through ORC, and
 * returns a raw function pointer. The pointer is cached per [RR_FunctionDefinition]; later
 * calls just marshal `Rt_IntValue` args into a `LongArray` and dispatch into the native code.
 *
 * When the C++ side reports "not compilable" (return value 0), the call falls back to the
 * wrapped [Rt_InterpreterImpl]. The `outerInterp = this` wire-up routes any user-function
 * call reached *inside* a fallback subtree back into this backend, so a JITable callee
 * invoked from interpreter code still takes the native path.
 *
 * # No GC
 *
 * Only `i64` crosses the JNI boundary today. The JVM still owns every `Rt_Value`; the native
 * side allocates nothing per call. Extending the surface to heap-allocated types will require
 * a bump-arena per call (see `llvm.md`).
 */
class Llvm_Backend(
    override val rrApp: RR_App,
    override val stdlib: Rt_StdlibEnv = Rt_StdlibEnv.global(),
) : Rt_Interpreter {

    internal val delegate: Rt_InterpreterImpl = Rt_InterpreterImpl(rrApp, stdlib).also {
        it.outerInterp = this
    }

    /** Lazily serialised once: the C++ JIT walks this buffer to find each function's body. */
    private val serializedApp: ByteArray by lazy { serializeRellApp(rrApp) }

    /** RR_FunctionDefinition → index inside the FlatBuffers `App.functions` array. */
    private val functionIndices: Map<RR_FunctionDefinition, Int> by lazy {
        rrApp.allFunctions.withIndex().associate { (i, fn) -> fn to i }
    }

    /** RR_QueryDefinition → index inside the FlatBuffers `App.queries` array (built from `allQueries`). */
    private val queryIndices: Map<RR_QueryDefinition, Int> by lazy {
        rrApp.allQueries.withIndex().associate { (i, q) -> q to i }
    }

    /** RR_GlobalConstantDefinition → index inside the FlatBuffers `App.constants` array. */
    private val constantIndices: Map<RR_GlobalConstantDefinition, Int> by lazy {
        rrApp.allConstants.withIndex().associate { (i, c) -> c to i }
    }

    /**
     * Per-function JIT cache. A `null` entry means "C++ reported not compilable; don't retry."
     * The cache is keyed by definition identity (the [RR_App] is immutable; identity is stable).
     */
    private val jitCache: MutableMap<RR_FunctionDefinition, CompiledFn?> = HashMap()

    /** Per-query JIT cache. Same `null = not compilable` convention as [jitCache]. */
    private val queryJitCache: MutableMap<RR_QueryDefinition, CompiledFn?> = HashMap()

    /** Per-constant JIT cache. Same `null = not compilable` convention as [jitCache]. */
    private val constantJitCache: MutableMap<RR_GlobalConstantDefinition, CompiledFn?> = HashMap()

    /** Counters exposed for tests to assert the JIT path actually fired (vs. silently delegating). */
    @Volatile var jitHits: Int = 0
        private set

    @Volatile var jitMisses: Int = 0
        private set

    /**
     * Total wall-clock nanoseconds spent inside the native `RellLlvmNative.compile*ByIndex` calls
     * (IR emission + ORC JIT), accumulated across every function/query/constant compiled by this
     * backend. This is the one-time JIT *compile* overhead, kept separate from steady-state
     * execution so the benchmark report can show it as a distinct bar. Only the compile call is
     * timed — never argument marshalling or native invocation.
     */
    @Volatile var jitCompileNanos: Long = 0L
        private set

    private data class CompiledFn(val paramCount: Int, val fnPtr: Long)

    override val metaGtv: Gtv get() = delegate.metaGtv

    override fun resolveType(type: RR_Type): Rt_ValueClass<*> = delegate.resolveType(type)
    override fun resolveRType(rType: R_Type): Rt_ValueClass<*> = delegate.resolveRType(rType)

    override fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean,
    ): Rt_Value {
        val compiled = jitCacheFor(fn)
        if (compiled != null && args.size == compiled.paramCount && args.all { it is Rt_IntValue }) {
            jitHits++
            return invokeNative(compiled, args)
        }
        jitMisses++
        return delegate.callFunction(fn, exeCtx, args, dbUpdateAllowed)
    }

    private fun jitCacheFor(fn: RR_FunctionDefinition): CompiledFn? {
        // `null` in the cache means "tried, not compilable" — `containsKey` distinguishes
        // that from "never tried" so we don't repeatedly retry impossible bodies.
        if (jitCache.containsKey(fn)) return jitCache[fn]
        val index = functionIndices[fn]
        val entry = if (index == null) {
            null
        } else {
            val t0 = System.nanoTime()
            val ptr = RellLlvmNative.compileFunctionByIndex(serializedApp, index)
            jitCompileNanos += System.nanoTime() - t0
            if (ptr == 0L) null else CompiledFn(fn.fnBase.params.size, ptr)
        }
        jitCache[fn] = entry
        return entry
    }

    private fun invokeNative(compiled: CompiledFn, args: List<Rt_Value>): Rt_Value {
        val raw = LongArray(compiled.paramCount) { i ->
            (args[i] as Rt_IntValue).value
        }
        val out = RellLlvmNative.callI64Function(compiled.fnPtr, raw)
        // The JIT'd integer arithmetic is overflow-CHECKED (matching Math.*Exact): on overflow it
        // records the exact Rell error code in a thread-local channel and the i64 result is junk.
        // Raise the identical Rt_Exception the tree-walker would, keeping consensus behaviour exact.
        val overflowCode = RellLlvmNative.pollIntOverflow()
        if (overflowCode != null) {
            throw Rt_Exception.common(overflowCode, "Integer overflow")
        }
        return Rt_IntValue.get(out)
    }

    override fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) =
        delegate.callOperation(op, exeCtx, args)

    override fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) =
        delegate.executeOperationGuard(op, exeCtx, args)

    override fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
        val compiled = queryJitCacheFor(query)
        // The native entry's params are all integer-typed (the JIT gate). A query is an entry point
        // that may be invoked with externally-supplied, possibly mistyped args; if any arg is not an
        // Rt_IntValue, delegate to the interpreter so it raises the proper `fn_wrong_arg_type` error
        // instead of a ClassCastException in arg marshalling.
        if (compiled != null && args.size == compiled.paramCount && args.all { it is Rt_IntValue }) {
            jitHits++
            return invokeNative(compiled, args)
        }
        jitMisses++
        return delegate.callQuery(query, exeCtx, args)
    }

    override fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value {
        val compiled = constantJitCacheFor(const)
        if (compiled != null) {
            jitHits++
            // A constant takes no arguments; the JIT'd entry ignores its (unused) i64* slot.
            return invokeNative(compiled, emptyList())
        }
        jitMisses++
        return delegate.evaluateConstant(const, exeCtx)
    }

    private fun queryJitCacheFor(query: RR_QueryDefinition): CompiledFn? {
        if (queryJitCache.containsKey(query)) return queryJitCache[query]
        val index = queryIndices[query]
        val entry = if (index == null) {
            null
        } else {
            val t0 = System.nanoTime()
            val ptr = RellLlvmNative.compileQueryByIndex(serializedApp, index)
            jitCompileNanos += System.nanoTime() - t0
            if (ptr == 0L) null else CompiledFn(query.params().size, ptr)
        }
        queryJitCache[query] = entry
        return entry
    }

    private fun constantJitCacheFor(const: RR_GlobalConstantDefinition): CompiledFn? {
        if (constantJitCache.containsKey(const)) return constantJitCache[const]
        val index = constantIndices[const]
        val entry = if (index == null) {
            null
        } else {
            val t0 = System.nanoTime()
            val ptr = RellLlvmNative.compileConstantByIndex(serializedApp, index)
            jitCompileNanos += System.nanoTime() - t0
            if (ptr == 0L) null else CompiledFn(0, ptr)
        }
        constantJitCache[const] = entry
        return entry
    }

    override fun evaluateAttributeDefault(
        defId: DefinitionId,
        attrIndex: Int,
        exeCtx: Rt_ExecutionContext,
        dbUpdateAllowed: Boolean,
    ): Rt_Value = delegate.evaluateAttributeDefault(defId, attrIndex, exeCtx, dbUpdateAllowed)

    override fun evaluateParamDefault(param: RR_FunctionParam, defCtx: Rt_DefinitionContext): Rt_Value =
        delegate.evaluateParamDefault(param, defCtx)

    override fun evaluateExpr(expr: RR_Expr, frame: Rt_Frame): Rt_Value = delegate.evaluateExpr(expr, frame)

    override fun callTarget(
        target: RR_FunctionCallTarget,
        base: Rt_Value?,
        args: List<Rt_Value>,
        frame: Rt_Frame,
        callPos: FilePos?,
    ): Rt_Value = delegate.callTarget(target, base, args, frame, callPos)

    override fun unwrapInterpreterImpl(): Any = delegate

    companion object {
        fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Llvm_Backend {
            @Suppress("UNCHECKED_CAST")
            val sysFnMap = compilationSysFns as Map<String, R_SysFunction>
            return Llvm_Backend(rrApp, Rt_StdlibEnv(sysFnMap.toImmMap()))
        }
    }
}
