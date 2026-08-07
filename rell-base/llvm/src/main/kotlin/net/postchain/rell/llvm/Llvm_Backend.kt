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
import net.postchain.rell.base.runtime.Rt_RR_EnumValue
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
    private val funcJitCache: MutableMap<RR_FunctionDefinition, CompiledFn?> = HashMap()

    /** Per-query JIT cache. Same `null = not compilable` convention as [funcJitCache]. */
    private val queryJitCache: MutableMap<RR_QueryDefinition, CompiledI64Fn?> = HashMap()

    /** Per-constant JIT cache. Same `null = not compilable` convention as [funcJitCache]. */
    private val constantJitCache: MutableMap<RR_GlobalConstantDefinition, CompiledI64Fn?> = HashMap()

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

    /**
     * JIT cache record for a USER FUNCTION, QUERY or GLOBAL CONSTANT. These lower through the i64-only entries
     * ([RellLlvmNative.compileQueryByIndex] / [RellLlvmNative.compileConstantByIndex]) and run via
     * [RellLlvmNative.callI64Function]; there is no extended value ABI for them yet (the native side
     * exposes no `compile{Query,Constant}Extended`). A query/constant body that is not purely i64
     * soft-fails at compile (returns 0) and delegates, so the raw `fnPtr` only ever runs i64 args.
     */
    private data class CompiledI64Fn(val paramCount: Int, val fnPtr: Long)

    /**
     * JIT cache record for a USER FUNCTION compiled through the extended value ABI
     * ([RellLlvmNative.compileFunctionExtended] / [RellLlvmNative.callValueFunction]). Carries the
     * opaque native `CompiledFn` handle plus the dense `R_SysFunction?` dispatch table — resolved
     * once at compile time from the id-ordered sysfn names the native lowerer interned, so the JVM
     * never re-derives the native `SysFnId` space (see [Llvm_SysBridge]).
     */
    private class CompiledFn(
        val paramCount: Int,
        val fnHandle: Long,
        /** Dense `R_SysFunction?` table; index == native `SysFnId`. */
        val sysFns: Array<R_SysFunction?>,
        /**
         * Dense list-literal type table; index == native `ListTypeId`. Built by [Llvm_ListTypeWalker]
         * from the function body in the SAME pre-order the native `ListTypeTable` interns, so
         * `Llvm_SysBridge.listType(id)` resolves the EXACT `Rt_ValueClass` the native `rell_make_list`
         * needs for a constructed list. Empty when the body has no native list literal.
         */
        val listTypes: List<Rt_ValueClass<*>>,
    )

    override val metaGtv: Gtv get() = delegate.metaGtv

    override fun resolveType(type: RR_Type): Rt_ValueClass<*> = delegate.resolveType(type)
    override fun resolveRType(rType: R_Type): Rt_ValueClass<*> = delegate.resolveRType(rType)

    override fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean,
    ): Rt_Value {
        // The extended value ABI lowers ANY body the native lowerer can prove bit-exact — its args
        // and result cross the JNI boundary as marshalled `Rt_Value`s (not just i64), and stdlib /
        // SQL nodes route back into the JVM through `Llvm_SysBridge`. The C++ side soft-fails (the
        // handle is 0) any body outside its envelope, so a non-compilable function still delegates
        // to the interpreter. There is no i64-only arg filter: a boolean/text/collection arg takes
        // the native value path exactly like an integer one.
        val compiled = funcJitCacheFor(fn)
        if (compiled != null && args.size == compiled.paramCount) {
            // The native body may ESCAPE the long-fit decimal/big_integer envelope at runtime (a wide
            // HANDLE operand, an i64 mantissa/scale overflow, or a decimal `/`/`%`). On escape the
            // native call returns null and pollJitEscape() is set; we re-run the WHOLE call on the
            // interpreter (bit-exact) and count it as a MISS, not a hit — so jitHits reflects only
            // truly-native runs (the long-fit envelope) and jitMisses the interpreter fallbacks.
            val result = invokeValueNative(compiled, fn, exeCtx, dbUpdateAllowed, args)
            if (result != null) {
                jitHits++
                return result
            }
            jitMisses++
            return delegate.callFunction(fn, exeCtx, args, dbUpdateAllowed)
        }
        jitMisses++
        return delegate.callFunction(fn, exeCtx, args, dbUpdateAllowed)
    }

    private fun funcJitCacheFor(fn: RR_FunctionDefinition): CompiledFn? {
        // `null` in the cache means "tried, not compilable" — `containsKey` distinguishes
        // that from "never tried" so we don't repeatedly retry impossible bodies.
        if (funcJitCache.containsKey(fn)) return funcJitCache[fn]
        val index = functionIndices[fn]
        val entry = if (index == null) {
            null
        } else {
            val sysFnNames = ArrayList<String>()
            val dbNodeCount = IntArray(1)
            val listTypeCount = IntArray(1)
            val t0 = System.nanoTime()
            val handle = RellLlvmNative.compileFunctionExtended(
                serializedApp, index, sysFnNames, dbNodeCount, listTypeCount,
            )
            jitCompileNanos += System.nanoTime() - t0
            if (handle == 0L) {
                null
            } else {
                // Mirror the native ListTypeTable walk: collect each ListLiteral's static type in the
                // identical pre-order, resolve to its Rt_ValueClass. The count MUST match the native
                // outListTypeCount — a mismatch is a walk-order desync (a hard bug, not a soft-fail),
                // so assert it before trusting the dense array the native rell_make_list indexes.
                val listRRTypes = Llvm_ListTypeWalker.collect(fn.fnBase.body)
                check(listRRTypes.size == listTypeCount[0]) {
                    "LLVM list-type walk desync for ${fn.fnBase.defName.appLevelName}: " +
                        "JVM walk found ${listRRTypes.size} list literals, native interned ${listTypeCount[0]}"
                }
                CompiledFn(
                    paramCount = fn.fnBase.params.size,
                    fnHandle = handle,
                    sysFns = Llvm_SysBridge.resolveSysFns(stdlib, sysFnNames),
                    listTypes = listRRTypes.map { resolveType(it) },
                )
            }
        }
        funcJitCache[fn] = entry
        return entry
    }

    private fun invokeI64Native(compiled: CompiledI64Fn, args: List<Rt_Value>): Rt_Value {
        val raw = LongArray(compiled.paramCount) { i ->
            (args[i] as Rt_IntValue).value
        }
        val out = RellLlvmNative.callI64Function(compiled.fnPtr, raw)
        // The JIT'd integer arithmetic is CHECKED (matching Math.*Exact and the div0 throws in
        // rt_ops.kt): on overflow or division-by-zero it records the exact Rell error code in a
        // thread-local channel and the i64 result is junk. Raise the identical Rt_Exception the
        // tree-walker would, keyed on that code, so consensus behaviour (the error's `code`) is
        // exact. The message is diagnostic only; the code is the consensus identifier.
        val errorCode = RellLlvmNative.pollIntOverflow()
        if (errorCode != null) {
            throw Rt_Exception.common(errorCode, "Integer arithmetic error")
        }
        return Rt_IntValue.get(out)
    }

    /**
     * Runs the JIT'd value-ABI body. Returns the native result, or `null` when the body ESCAPED the
     * long-fit decimal/big_integer envelope at runtime (the caller then re-runs on the interpreter).
     * Integer overflow / div-by-zero is a different channel: it raises the exact Rt_Exception here.
     */
    private fun invokeValueNative(
        compiled: CompiledFn,
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        dbUpdateAllowed: Boolean,
        args: List<Rt_Value>,
    ): Rt_Value? {
        // The native back-calls (Llvm_SysBridge.dispatch, SQL evaluation) need the live call
        // context for THIS invocation. Build it from the same defId the interpreter would
        // (Rt_InterpreterImpl.callFunction uses fn.base.defId), register it under an opaque handle,
        // and tear it down after the native call returns.
        val defCtx = Rt_DefinitionContext(exeCtx, dbUpdateAllowed, fn.base.defId)
        val callEnv = Llvm_CallEnv(
            defCtx.toCallContext(), compiled.sysFns, ::reboxEnum, ::reboxStruct,
            // The list-type resolver indexes THIS call's dense list-type array (built by the
            // Llvm_ListTypeWalker mirror). The native rell_make_list passes the interned ListTypeId;
            // a bad id is a walk-order desync (already asserted in funcJitCacheFor) -> hard error.
            listType = { id ->
                compiled.listTypes.getOrNull(id)
                    ?: error("Llvm list-type id $id out of range (size ${compiled.listTypes.size})")
            },
        )
        val ctxHandle = Llvm_SysBridge.register(callEnv)
        val result = try {
            RellLlvmNative.callValueFunction(compiled.fnHandle, args.toTypedArray(), ctxHandle)
        } finally {
            Llvm_SysBridge.unregister(ctxHandle)
        }
        // Envelope escape FIRST: a decimal/big_integer fast path left the long-fit envelope at
        // runtime (wide HANDLE operand, mantissa/scale overflow, or decimal `/`/`%`). The native
        // result is meaningless and `result` is null; signal the caller (return null) to re-run the
        // whole call on the interpreter. This is NOT an error — distinct from the overflow channel.
        if (RellLlvmNative.pollJitEscape()) {
            return null
        }
        // The JIT'd body performs CHECKED integer arithmetic (matching Math.*Exact / the div0
        // throws in rt_ops.kt): on overflow or division-by-zero it records the exact Rell error
        // code into a thread-local channel, completes with a meaningless wrapped value, and
        // callValueFunction returns a `null` result. Raise the identical Rt_Exception the
        // tree-walker would, keyed on that code, so consensus behaviour is bit-exact. This mirrors
        // invokeI64Native; the channel is per-thread, matching the per-thread JNI invocation.
        val errorCode = RellLlvmNative.pollIntOverflow()
        if (errorCode != null) {
            // The body executed NATIVELY and trapped — this is the JIT result, not a degradation to
            // the interpreter. Count the hit before raising, since the throw escapes callFunction's
            // `jitHits++` (mirrors how the i64 query/constant path counts the hit before invoking).
            jitHits++
            throw Rt_Exception.common(errorCode, "Integer arithmetic error")
        }
        // List subscript out of bounds: the native body recorded the exact code into the list-error
        // channel and returned a poison result. Raise the IDENTICAL Rt_Exception the interpreter's
        // Rt_ListValue.checkIndex would (code "list:index:<size>:<index>", message "List index out of
        // bounds: <index> (size <size>)"). The message is rebuilt here from the code's <size>/<index>
        // fields so it is byte-identical to the tree-walker's; the code is the consensus identifier.
        val listErr = RellLlvmNative.pollListError()
        if (listErr != null) {
            jitHits++
            val parts = listErr.split(':')  // "list:index:<size>:<index>"
            val size = parts[2].toInt()
            val index = parts[3].toLong()
            throw Rt_Exception.common(listErr, "List index out of bounds: $index (size $size)")
        }
        // byte_array subscript out of bounds: the native body recorded the exact code into the
        // byte-array-error channel and returned a poison result. Raise the IDENTICAL Rt_Exception the
        // interpreter's ByteArraySubscript would (code "expr_bytearray_subscript_index:<size>:<index>",
        // message "Byte array index out of range: <index> (size <size>)"). The message is rebuilt here
        // from the code's <size>/<index> fields so it is byte-identical to the tree-walker's.
        val byteArrayErr = RellLlvmNative.pollByteArrayError()
        if (byteArrayErr != null) {
            jitHits++
            val parts = byteArrayErr.split(':')  // "expr_bytearray_subscript_index:<size>:<index>"
            val size = parts[1].toInt()
            val index = parts[2].toLong()
            throw Rt_Exception.common(byteArrayErr, "Byte array index out of range: $index (size $size)")
        }
        // text subscript out of bounds: the native body recorded the exact code into the text-error
        // channel and returned a poison result. Raise the IDENTICAL Rt_Exception the interpreter's
        // TextSubscript would (code "expr_text_subscript_index:<len>:<index>", message "Index out of
        // bounds: <index> (length <len>)"). The message is rebuilt here from the code's <len>/<index>
        // fields so it is byte-identical to the tree-walker's.
        val textErr = RellLlvmNative.pollTextError()
        if (textErr != null) {
            jitHits++
            val parts = textErr.split(':')  // "expr_text_subscript_index:<len>:<index>"
            val len = parts[1].toInt()
            val index = parts[2].toLong()
            throw Rt_Exception.common(textErr, "Index out of bounds: $index (length $len)")
        }
        // Member text-op error (char_at / sub / index_of/2 / repeat): the native body recorded the EXACT
        // op-specific code AND message into the general text-op channel and returned a poison result.
        // Raise the IDENTICAL Rt_Exception the interpreter's lib_type_text.kt body would — the native
        // helper built both strings verbatim from that source, so no rebuild is needed here.
        val textOpErr = RellLlvmNative.pollTextOpError()
        if (textOpErr != null) {
            jitHits++
            throw Rt_Exception.common(textOpErr[0], textOpErr[1])
        }
        return result
    }

    /**
     * Rebuilds the canonical enum [Rt_Value] from (enum-type index, ordinal) for the native ENUM
     * rebox ([Llvm_SysBridge.enumValue], reached via the [Llvm_CallEnv] this is wired into). This is
     * the EXACT construction the interpreter uses for an `RR_ConstantValue.Enum`
     * (`rr_interpreter.kt`): resolve the enum type, take its `attrs[ordinal]`, build the
     * `Rt_RR_EnumValue`. So `from_jvm` (cracking an enum to `(typeIdx, ordinal)`) ∘ `to_jvm`
     * (this rebox) round-trips to the identical canonical value, and the value is bit-exact with the
     * interpreter's — same equals (ordinal + type name) and same comparator (ordinal).
     */
    private fun reboxEnum(typeIdx: Int, ordinal: Int): Rt_Value {
        val rtType = resolveType(RR_Type.Enum(typeIdx))
        val attr = rrApp.allEnums[typeIdx].attrs[ordinal]
        return Rt_RR_EnumValue(rtType, attr)
    }

    /**
     * Rebuilds the canonical struct [Rt_Value] from (def-index, attribute values) for the native
     * STRUCT rebox ([Llvm_SysBridge.structValue], reached via the [Llvm_CallEnv] this is wired into).
     * This is the EXACT construction the interpreter uses for an `RR_Expr.StructCreate`
     * (`rr_interpreter.kt`): resolve the struct type, take the def's attribute name list, build the
     * `Rt_StructValue`. So `from_jvm` (cracking a struct to its def-index + recursively-cracked
     * fields) ∘ `to_jvm` (this rebox) round-trips to a structurally-equal value
     * (`Rt_StructValue.structEquals` — type name + pairwise attr equality). The native side only
     * cracks structs whose every attribute round-trips and that carry NO sizeConstraint (the lowering
     * gate, lower_expr.cpp / value.cpp), so reconstructing here cannot diverge from the interpreter.
     */
    private fun reboxStruct(defIndex: Int, attrs: List<Rt_Value>): Rt_Value {
        val rtType = resolveType(RR_Type.Struct(defIndex))
        val attrNames = rrApp.allStructs[defIndex].struct.attributesList.map { it.name }
        return net.postchain.rell.base.runtime.Rt_StructValue(rtType, attrNames, attrs.toMutableList())
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
            return invokeI64Native(compiled, args)
        }
        jitMisses++
        return delegate.callQuery(query, exeCtx, args)
    }

    override fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value {
        val compiled = constantJitCacheFor(const)
        if (compiled != null) {
            jitHits++
            // A constant takes no arguments; the JIT'd entry ignores its (unused) i64* slot.
            return invokeI64Native(compiled, emptyList())
        }
        jitMisses++
        return delegate.evaluateConstant(const, exeCtx)
    }

    private fun queryJitCacheFor(query: RR_QueryDefinition): CompiledI64Fn? {
        if (queryJitCache.containsKey(query)) return queryJitCache[query]
        val index = queryIndices[query]
        val entry = if (index == null) {
            null
        } else {
            val t0 = System.nanoTime()
            val ptr = RellLlvmNative.compileQueryByIndex(serializedApp, index)
            jitCompileNanos += System.nanoTime() - t0
            if (ptr == 0L) null else CompiledI64Fn(query.params().size, ptr)
        }
        queryJitCache[query] = entry
        return entry
    }

    private fun constantJitCacheFor(const: RR_GlobalConstantDefinition): CompiledI64Fn? {
        if (constantJitCache.containsKey(const)) return constantJitCache[const]
        val index = constantIndices[const]
        val entry = if (index == null) {
            null
        } else {
            val t0 = System.nanoTime()
            val ptr = RellLlvmNative.compileConstantByIndex(serializedApp, index)
            jitCompileNanos += System.nanoTime() - t0
            if (ptr == 0L) null else CompiledI64Fn(0, ptr)
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
