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

    /**
     * Per-function JIT cache. A `null` entry means "C++ reported not compilable; don't retry."
     * The cache is keyed by definition identity (the [RR_App] is immutable; identity is stable).
     */
    private val jitCache: MutableMap<RR_FunctionDefinition, CompiledFn?> = HashMap()

    /** Counters exposed for tests to assert the JIT path actually fired (vs. silently delegating). */
    @Volatile var jitHits: Int = 0
        private set

    @Volatile var jitMisses: Int = 0
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
        if (compiled != null && args.size == compiled.paramCount) {
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
            val ptr = RellLlvmNative.compileFunctionByIndex(serializedApp, index)
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
        return Rt_IntValue.get(out)
    }

    override fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) =
        delegate.callOperation(op, exeCtx, args)

    override fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) =
        delegate.executeOperationGuard(op, exeCtx, args)

    override fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value =
        delegate.callQuery(query, exeCtx, args)

    override fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value =
        delegate.evaluateConstant(const, exeCtx)

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
