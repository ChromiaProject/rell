/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.nodes.Tf_ExprNode

/**
 * Runtime driver for the Truffle backend.
 *
 * Every entry point on the [net.postchain.rell.base.runtime.Rt_Interpreter] interface routes
 * through here. The driver:
 *
 * 1. Builds (and caches per-definition) a Truffle [RootCallTarget] whose [RootNode] holds a
 *    translated body.
 * 2. Constructs an [Rt_CallFrame] using the wrapped [net.postchain.rell.base.runtime.Rt_InterpreterImpl]'s frame
 *    factory (re-using its block-scope tracking and parameter validation).
 * 3. Invokes the call target with the frame as the sole argument.
 *
 * Caching call targets per-definition is the *whole point* of running on Truffle: subsequent
 * calls to the same function reuse the [RootCallTarget], allowing Graal to specialize and
 * inline based on observed types. Without that cache the JIT would never see the same node
 * twice.
 */
internal class Tf_Driver(private val backend: Tf_Backend) {
    private val translator: Tf_Translator = Tf_Translator(backend)

    /**
     * Build (without caching) a [RootCallTarget] for a user function's body.
     *
     * **Wave 3 wiring.** User-function targets are the *only* targets reachable through
     * `DirectCallNode` (from [net.postchain.rell.base.runtime.truffle.nodes.Tf_FunctionCallNode.UserFn])
     * — operations/queries/guards/constants are entered exclusively via the driver's
     * `invokeBody`. We therefore build a [Tf_FunctionRootNode] (with `paramOffsets` pre-extracted)
     * for functions and a plain [Tf_RootNode] for the rest.
     */
    fun buildFunctionTarget(fn: RR_FunctionDefinition): RootCallTarget {
        val base = fn.fnBase
        val paramOffsets = IntArray(base.paramVars.size) { base.paramVars[it].ptr.offset }
        // Per-param declared types — the translator uses these to seed slot-kind classification
        // for parameter slots (e.g. an `integer` param becomes a `Long` slot).
        val paramTypes = Array(base.paramVars.size) { base.paramVars[it].type }
        return translator.buildBodyTarget(base.body, base.frame, fn.base.defId, paramOffsets, paramTypes)
    }

    fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean,
    ): Rt_Value {
        val base = fn.fnBase
        backend.delegate.validateParams(base.params, args)
        val frame = backend.delegate.createFrame(exeCtx, base.frame, dbUpdateAllowed, fn.base.defId)
        backend.delegate.setParams(frame, base.paramVars, args, base.defName.appLevelName)
        val target = backend.functionTarget(fn)
        return invokeBody(target, frame)
    }

    fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        backend.delegate.validateParams(op.params, args)
        val frame = backend.delegate.createFrame(exeCtx, op.frame, true, op.base.defId)
        backend.delegate.setParams(frame, op.paramVars, args, op.base.appLevelName)
        val target = backend.opTargets.getOrPut(op) {
            translator.buildBodyTarget(op.body, op.frame, op.base.defId)
        }
        invokeBody(target, frame)
    }

    fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        val guardBody = op.guardBody ?: return
        val frame = backend.delegate.createFrame(exeCtx, op.frame, true, op.base.defId)
        backend.delegate.setParams(frame, op.paramVars, args, op.base.appLevelName)
        val target = backend.opGuardTargets.getOrPut(op) {
            translator.buildBodyTarget(guardBody, op.frame, op.base.defId)
        }
        invokeBody(target, frame)
    }

    fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
        backend.delegate.validateParams(query.body.params, args)
        return when (val body = query.body) {
            is RR_UserQueryBody -> {
                val frame = backend.delegate.createFrame(exeCtx, body.frame, false, query.base.defId)
                backend.delegate.setParams(frame, body.paramVars, args, query.base.appLevelName)
                val target = backend.queryTargets.getOrPut(query) {
                    translator.buildBodyTarget(body.body, body.frame, query.base.defId)
                }
                invokeBody(target, frame)
            }

            is RR_SysQueryBody -> {
                // Sys-query bodies are primitive function dispatches; no Truffle benefit beyond a
                // direct delegate call — keep the fast path.
                val fn = checkNotNull(backend.stdlib.sysFunctions[body.fnName]) {
                    "Sys query function not found: ${body.fnName}"
                }
                val callCtx = Rt_CallContext(Rt_DefinitionContext(exeCtx, false, query.base.defId))
                fn.call(callCtx, args)
            }
        }
    }

    fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value {
        val frame = backend.delegate.createFrame(exeCtx, const.base.initFrame, false, const.base.defId)
        // Constants are expressions, not statements. Wrap them in a statement-shaped expression
        // so the same call-target machinery can be reused.
        val target = backend.constTargets.getOrPut(const) {
            translator.buildExprAsBodyTarget(const.expr, const.base.initFrame, const.base.defId)
        }
        return invokeBody(target, frame)
    }

    /**
     * Invokes a body-call-target whose body is a [Tf_ExprNode]. The root node's `execute`
     * returns the function's return value directly — falling through bodies return
     * [Rt_UnitValue]; bodies that hit a `return` expression cause the root to return the
     * value supplied to the [net.postchain.rell.base.runtime.truffle.nodes.Tf_ReturnStmtNode]
     * (or [Rt_UnitValue] for parameterless `return;`).
     */
    private fun invokeBody(target: RootCallTarget, frame: Rt_CallFrame): Rt_Value =
        Tf_Unchecked.cast(target.call(frame))
}

/**
 * Truffle [RootNode] that runs a translated body against a driver-supplied [Rt_CallFrame].
 *
 * # Outer-entry path only
 *
 * This root handles the **outer-entry** calling convention exclusively:
 * `arguments = [calleeRtFrame: Rt_CallFrame]` — set by [Tf_Driver.invokeBody] from
 * `callOperation`/`callQuery`/`executeOperationGuard`/`callFunction`/`evaluateConstant`. User
 * functions reached *through* `DirectCallNode` use the [Tf_FunctionRootNode] subclass instead;
 * its `execute` overrides this one to handle the inner-entry shape.
 *
 * On entry, the root copies `arguments[0]` into the [TF_RT_FRAME_AUX_SLOT] auxiliary slot so
 * downstream [net.postchain.rell.base.runtime.truffle.nodes.tfRtFrame] calls return that frame without lazy allocation, then pulls each
 * [Rt_CallFrame].values&#91;i&#93; into the [VirtualFrame] slot of the same index for the hot-path
 * body's first reads.
 *
 * Storage is **bridged** at body entry/exit when `needsBridge` is true: pre-call sync makes
 * the latest hot-path writes visible to slow-path nodes; post-call sync mirrors any
 * tree-walker writes back into [VirtualFrame] for callers that inspect legacy storage (REPL
 * `dumpState`, fallback re-entry).
 */
internal open class Tf_RootNode(
    language: TruffleLanguage<*>?,
    private val name: String,
    @field:Child protected var body: Tf_ExprNode,
    frameDescriptor: FrameDescriptor,
) : RootNode(language, frameDescriptor) {
    /**
     * Translator-time flag: when `true`, the body has slow-path nodes that read from
     * [Rt_CallFrame].values / mutate `curBlockUid`, so we must run the bridge sync.
     * When `false`, the body is pure hot-path; we still need to pull params from
     * [Rt_CallFrame].values (where `setParams` / `setupCalleeFrameFast` wrote them) into
     * [VirtualFrame] for the body's first read, but we skip the post-call push because
     * no slow path can have written to legacy storage during the body.
     */
    @field:CompilationFinal protected val needsBridge: Boolean = body.needsBlockState

    /**
     * Frame size captured at translate time — used by [pullParamsInline]
     * for an `@ExplodeLoop`-able copy loop. The descriptor's slot count equals the frame's
     * `values.size`, so PE can unroll the loop fully.
     */
    @field:CompilationFinal protected val frameSize: Int = frameDescriptor.numberOfSlots

    /**
     * Per-slot kind table mirroring the FrameDescriptor's reserved kinds — `@CompilationFinal`
     * with `dimensions = 1` so PE folds each slot's classifier into the unrolled
     * [pullParamsInline] body. Sourced from the descriptor's [Tf_FrameInfo] so
     * driver-built test descriptors without a Tf_FrameInfo fall through to all-Object.
     */
    @field:CompilationFinal(dimensions = 1)
    protected val slotKinds: ByteArray =
        (frameDescriptor.info as? Tf_FrameInfo)?.slotKinds ?: ByteArray(frameSize)

    override fun execute(frame: VirtualFrame): Any {
        // Outer entry: arguments[0] is the pre-built Rt_CallFrame supplied by the driver.
        // Cache it in the aux slot so any downstream `tfRtFrame(frame)` call returns this
        // frame without lazy allocation.
        val rt = Tf_Unchecked.cast<Rt_CallFrame>(frame.arguments[0])
        frame.setAuxiliarySlot(TF_RT_FRAME_AUX_SLOT, rt)
        val status: Int
        if (needsBridge) {
            // Driver-built Rt_CallFrame is heap-backed: setParams wrote args into the
            // heap storage. Pull those args into the VirtualFrame, then swap the frame's
            // storage to a VirtualFrame-backed one so subsequent slow-path writes
            // (Tf_VarStmtNode, Tf_AssignStmtNode, Tf_FallbackStmtNode) land directly in
            // the same slots the hot-path Tf_VarReadNode reads from. No bidirectional
            // mirror, single source of truth.
            pullParamsInline(frame, rt)
            rt.storage = Tf_VirtualFrameStorage(frame)
            status = body.executeStmt(frame)
        } else {
            // Fast path: pure hot-path body. Params arrive in [Rt_CallFrame.values];
            // pull them inline (no boundary) so PE can fold each slot read into the
            // compiled graph.
            pullParamsInline(frame, rt)
            status = body.executeStmt(frame)
            // No push-back needed: the body is pure hot-path — it never writes
            // [Rt_CallFrame.values], only [VirtualFrame] slots. Callers that need
            // legacy storage (REPL `dumpState`, fallback re-entry) reach a body whose
            // `needsBlockState = true`, which takes the slow branch above.
        }
        return readReturnOrUnit(frame, status)
    }

    /**
     * `@ExplodeLoop`-friendly mirror of `Tf_FrameSync.pullFromLegacy`. PE unrolls the loop
     * (the `frameSize` is `@CompilationFinal`), and each iteration becomes a guarded slot
     * write that virtualises away into compiled-graph SSA — no
     * [com.oracle.truffle.api.CompilerDirectives.TruffleBoundary], no frame
     * materialisation.
     */
    @ExplodeLoop
    protected fun pullParamsInline(frame: VirtualFrame, rt: Rt_CallFrame) {
        for (i in 0..<frameSize) {
            val value = rt.getUncheckedAt(i) ?: continue
            // Dispatch on the slot's reserved kind — a `setObject` on a `Long`/`Boolean` slot
            // throws `FrameSlotTypeException` at runtime, so we must unbox primitive-typed
            // values before writing. The slot-kind array is `@CompilationFinal` per index, so
            // PE folds the dispatch entirely and each unrolled iteration becomes one typed
            // store after compilation.
            when (slotKinds[i]) {
                TF_SLOT_KIND_LONG -> frame.setLong(i, Tf_Unchecked.cast<Rt_IntValue>(value).value)
                TF_SLOT_KIND_BOOLEAN -> frame.setBoolean(i, Tf_Unchecked.cast<Rt_BooleanValue>(value).value)
                else -> frame.setObject(i, value)
            }
        }
    }

    override fun getName(): String = name
    override fun isCloningAllowed(): Boolean = true
}

/**
 * Read the return value from [TF_RETURN_VALUE_AUX_SLOT] when [status] is [STATUS_RETURN];
 * otherwise return [Rt_UnitValue]. Falling-through bodies that never set the slot return
 * [Rt_UnitValue] just like the previous exception-based protocol's `catch` did when no
 * `Tf_ReturnException` was thrown.
 *
 * `STATUS_BREAK` / `STATUS_CONTINUE` should never reach a function-body root — the enclosing
 * loop must consume them. If one does, that's a translator bug; we treat it as fallthrough
 * so the caller observes [Rt_UnitValue] rather than crashing the workload.
 */
internal fun readReturnOrUnit(frame: VirtualFrame, status: Int): Rt_Value {
    if (status != STATUS_RETURN) return Rt_UnitValue
    return Tf_Unchecked.cast(frame.getAuxiliarySlot(TF_RETURN_VALUE_AUX_SLOT) ?: return Rt_UnitValue)
}

/**
 * Function-body root that handles **both** inner-entry and outer-entry calling conventions.
 *
 * # Calling conventions
 *
 * - **Outer entry** (driver → top-level call): `arguments = [calleeRtFrame: Rt_CallFrame]`.
 *   Behaves identically to [Tf_RootNode]: copy `arguments[0]` into the aux slot, optionally
 *   pull `values[]` for the hot path, run body.
 *
 * - **Inner entry** (UserFn DirectCallNode → callee root):
 *   `arguments = [null, callerRtFrame, arg1, …, argN]`. The `null` sentinel at slot 0 marks
 *   inner entry; slot 1 holds the **caller's** [Rt_CallFrame] (only used by
 *   `tfLazyAllocRtFrame` if a slow-path node demands an [Rt_CallFrame]); slots 2..N+1 are
 *   the evaluated arguments. The root writes each `arguments[2 + i]` directly into the
 *   param's [VirtualFrame] slot using the [paramOffsets] table pre-extracted at translate
 *   time. **No [Rt_CallFrame] allocation happens on this path** unless a slow-path node
 *   triggers `tfLazyAllocRtFrame` inside the body.
 *
 * The two paths diverge only in the prologue; the body is the same. Inner entry is the hot
 * path the wave-3 refactor is optimising — the recursive bench callees (`is_prime`,
 * `collatz_steps`, `fib`) are all pure hot-path bodies, so the inner-entry prologue collapses
 * (after PE) to N `frame.setObject(slot, arg)` calls + the body, no allocations.
 */
internal class Tf_FunctionRootNode(
    language: TruffleLanguage<*>?,
    name: String,
    body: Tf_ExprNode,
    frameDescriptor: FrameDescriptor,
    @field:CompilationFinal(dimensions = 1) private val paramOffsets: IntArray,
    /**
     * Per-parameter slot-kind table aligned with [paramOffsets]: `paramSlotKinds[i]` is the
     * kind of `paramOffsets[i]`. We pre-extract this at translate time so the inner-entry
     * write loop dispatches on a `@CompilationFinal` byte rather than reading
     * `frame.frameDescriptor.getSlotKind(...)` per iteration. PE constant-folds the dispatch
     * for each unrolled `i`.
     *
     * The caller (`Tf_FunctionCallNode.UserFn.buildInnerCallArgs*`) hands us already-boxed
     * `Rt_Value` argument values — the type system enforces correctness at the call site, so
     * unboxing here is a `Tf_Unchecked.cast` to the corresponding primitive value class.
     */
    @field:CompilationFinal(dimensions = 1) private val paramSlotKinds: ByteArray,
) : Tf_RootNode(language, name, body, frameDescriptor) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val arguments = frame.arguments
        val arg0 = arguments[0]
        val status: Int
        if (arg0 == null) {
            // Inner entry. Write each evaluated arg into its param slot directly. No
            // [Rt_CallFrame] is allocated here — the body executes against the
            // [VirtualFrame] alone. The aux slot stays null; slow-path nodes that demand an
            // [Rt_CallFrame] go through [tfLazyAllocRtFrame] which uses arguments[1] (the
            // caller's frame) to build a fresh callee context.
            for (i in paramOffsets.indices) {
                // arguments[2 + i] is the i-th evaluated arg. Tf_Unchecked.cast keeps PE
                // out of Kotlin's `Intrinsics.checkNotNull` chain.
                val argValue: Rt_Value = Tf_Unchecked.cast(arguments[2 + i])
                val slot = paramOffsets[i]
                when (paramSlotKinds[i]) {
                    TF_SLOT_KIND_LONG ->
                        frame.setLong(slot, Tf_Unchecked.cast<Rt_IntValue>(argValue).value)
                    TF_SLOT_KIND_BOOLEAN ->
                        frame.setBoolean(slot, Tf_Unchecked.cast<Rt_BooleanValue>(argValue).value)
                    else -> frame.setObject(slot, argValue)
                }
            }
            // needsBridge stays meaningful: a body with slow-path nodes has those nodes
            // synchronise [VirtualFrame] <-> [Rt_CallFrame.values] internally via
            // [Tf_FrameSync] around their boundaries (see [Tf_VarStmtNode], [Tf_AssignStmtNode],
            // [Tf_FallbackExprNode]/[Tf_FallbackStmtNode]). The lazy [Rt_CallFrame] alloc
            // already pushed the param slots into `values[]` at materialisation time.
            status = body.executeStmt(frame)
            return readReturnOrUnit(frame, status)
        }
        // Outer entry — same as [Tf_RootNode.execute].
        val rt = Tf_Unchecked.cast<Rt_CallFrame>(arg0)
        frame.setAuxiliarySlot(TF_RT_FRAME_AUX_SLOT, rt)
        if (needsBridge) {
            // Heap-backed Rt_CallFrame from driver: pull params into VF, then swap
            // storage so subsequent slow-path writes share the VF with the hot path.
            pullParamsInline(frame, rt)
            rt.storage = Tf_VirtualFrameStorage(frame)
            status = body.executeStmt(frame)
        } else {
            pullParamsInline(frame, rt)
            status = body.executeStmt(frame)
        }
        return readReturnOrUnit(frame, status)
    }
}

/**
 * Variant of [Tf_RootNode] used for constant/default-value bodies that must produce a [Rt_Value]
 * result.
 *
 * Outer entry only — constants/defaults are never invoked through `DirectCallNode`.
 */
internal class Tf_ExprBodyRootNode(
    language: TruffleLanguage<*>?,
    private val name: String,
    @field:Child private var body: Tf_ExprNode,
    frameDescriptor: FrameDescriptor,
) : RootNode(language, frameDescriptor) {
    @field:CompilationFinal private val needsBridge: Boolean = body.needsBlockState
    @field:CompilationFinal private val frameSize: Int = frameDescriptor.numberOfSlots

    /** See [Tf_RootNode.slotKinds]. */
    @field:CompilationFinal(dimensions = 1)
    private val slotKinds: ByteArray =
        (frameDescriptor.info as? Tf_FrameInfo)?.slotKinds ?: ByteArray(frameSize)

    override fun execute(frame: VirtualFrame): Any {
        val rt = Tf_Unchecked.cast<Rt_CallFrame>(frame.arguments[0])
        frame.setAuxiliarySlot(TF_RT_FRAME_AUX_SLOT, rt)
        val status: Int

        if (needsBridge) {
            try {
                status = body.executeStmt(frame)
            } finally {
            }
        } else {
            pullParamsInline(frame, rt)
            status = body.executeStmt(frame)
        }

        return readReturnOrUnit(frame, status)
    }

    @ExplodeLoop
    private fun pullParamsInline(frame: VirtualFrame, rt: Rt_CallFrame) {
        for (i in 0..<frameSize) {
            val value = rt.getUncheckedAt(i) ?: continue
            when (slotKinds[i]) {
                TF_SLOT_KIND_LONG -> frame.setLong(i, Tf_Unchecked.cast<Rt_IntValue>(value).value)
                TF_SLOT_KIND_BOOLEAN -> frame.setBoolean(i, Tf_Unchecked.cast<Rt_BooleanValue>(value).value)
                else -> frame.setObject(i, value)
            }
        }
    }

    override fun getName(): String = name
    override fun isCloningAllowed(): Boolean = true
}
