/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.profiles.BranchProfile
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.R_SysFunctionUtils
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_DefinitionContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.sysFnDisplayName
import net.postchain.rell.base.runtime.truffle.Tf_FrameInfo
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked
import net.postchain.rell.base.utils.LazyString

/**
 * Native: direct dispatch for sys-function calls (`RR_FunctionCallTarget.SysGlobal` and
 * `RR_FunctionCallTarget.SysMember`).
 *
 * # Specialisation by arity / result type
 *
 * - [SysGlobal] / [SysMember] handle variable-arity calls.
 * - [SysGlobalInt] / [SysMemberInt] / [SysGlobalBool] / [SysMemberBool] override
 *   `executeLong`/`executeBoolean` so callers reading the result via the typed path skip the
 *   default `asInteger()`/`asBoolean()` chain.
 *
 * Identity-mapping detection at translate time skips the permutation list build at runtime in
 * the common case (mapping_i == i for all i).
 */
internal sealed class Tf_SysCallNode : Tf_ExprNode() {
    /**
     * Inline cache for [buildCallCtx]: the (`exeCtx`, `dbUpdateAllowed`) pair feeding the
     * `Rt_DefinitionContext` constructor is monomorphic per execution (one driver/exeCtx,
     * `dbUpdateAllowed` flips only across guard-block boundaries), so a single-element cache
     * recovers the steady-state to one comparison + reference return on the hot path.
     *
     * Plain non-`@CompilationFinal` fields: PE compiles the comparison and branch into the
     * graph but doesn't fold the cache value, so refreshing the cache requires no
     * deopt/invalidation cycle. The call-context construction itself happens behind a
     * `@TruffleBoundary` slow path so the constructor's downstream code never enters PE.
     */
    private var cachedCallExeCtx: Rt_ExecutionContext? = null
    private var cachedCallDbUpdate: Boolean = false
    private var cachedCallCtx: Rt_CallContext? = null

    /**
     * Build the `Rt_CallContext` for a sys-fn call without lazy-allocating a callee
     * [net.postchain.rell.base.runtime.Rt_CallFrame].
     *
     * Mirrors [tfLazyAllocRtFrame]'s defCtx construction: `exeCtx` and `dbUpdateAllowed`
     * come from the outermost live caller (`tfPropagateRtFrame`, no alloc, no boundary);
     * `defId` comes from the current frame descriptor's `Tf_FrameInfo` so the ctx's defId
     * matches what the slow path would have built.
     *
     * Steady state: single-element cache hit returns the previously built `Rt_CallContext`
     * with no allocation — both the `Rt_DefinitionContext` and its `Rt_CallContext` wrapper
     * are reused as long as the live (`exeCtx`, `dbUpdateAllowed`) pair stays stable.
     */
    protected fun buildCallCtx(frame: VirtualFrame): Rt_CallContext {
        val caller = tfPropagateRtFrame(frame)
        val exeCtx = caller.exeCtx
        val dbUpdate = caller.dbUpdateAllowed()
        val cached = cachedCallCtx
        if (cached != null && cachedCallExeCtx === exeCtx && cachedCallDbUpdate == dbUpdate) {
            return cached
        }
        // Extract defId from the frame descriptor *before* the boundary call. The Truffle
        // Bytecode DSL annotation processor rejects `@TruffleBoundary` methods that take a
        // `VirtualFrame` parameter (non-materialised frames cannot cross a boundary).
        val info = Tf_Unchecked.cast<Tf_FrameInfo>(frame.frameDescriptor.info)
        return refreshCallCtxCache(info.defId, exeCtx, dbUpdate)
    }

    /**
     * Cache-miss slow path: builds a fresh defCtx + callCtx and stores both. Behind a
     * `@TruffleBoundary` so the `Rt_DefinitionContext` / `Rt_CallContext` constructors —
     * which reach into `runtime-core` field-init chains — never enter PE.
     */
    @TruffleBoundary
    private fun refreshCallCtxCache(
        defId: DefinitionId,
        exeCtx: Rt_ExecutionContext,
        dbUpdate: Boolean,
    ): Rt_CallContext {
        val defCtx = Rt_DefinitionContext(exeCtx, dbUpdate, defId)
        val callCtx = defCtx.toCallContext()
        cachedCallExeCtx = exeCtx
        cachedCallDbUpdate = dbUpdate
        cachedCallCtx = callCtx
        return callCtx
    }

    /**
     * Sys-fn dispatch behind a `@TruffleBoundary` so the impl body — which reaches into stdlib
     * code that PE has no business inlining (regex compilation, gtv parsing, decimal arithmetic,
     * SQL execution, etc.) — never enters PE.
     *
     * # Why no try/catch here (was the 30 % wrapper)
     *
     * Previously this routed through [R_SysFunctionUtils.call] → `callAndCatch`, which paid for
     * a try/catch with four catch arms (`Rt_Exception`, `RellInterpreterCrashException`,
     * `InterruptedException`, `Throwable`) and an `Rt_Exception` reconstruction on every call —
     * including the 99.99 % that don't throw. asprof on the FT4 `rule_serde` workload showed
     * `R_SysFunctionUtils.callAndCatch` at ~30 % inclusive CPU, with the `call` body itself
     * dwarfed by the surrounding wrapper.
     *
     * The fix: drop the wrapper from the success path and move the catch arms to a slow-path
     * helper guarded by a [BranchProfile] in the `execute` body. PE prunes the whole catch
     * block until the profile is tripped on the first thrown exception. The four-arm catch
     * still fires when needed, just not on every call.
     *
     * Direct dispatch into [R_SysFunction.call] preserves the same per-call cost
     * (one virtual call) without the wrapper allocation tax.
     */
    @TruffleBoundary
    protected fun invokeSysFn(
        callCtx: Rt_CallContext,
        fn: R_SysFunction,
        args: List<Rt_Value>,
    ): Rt_Value = fn.call(callCtx, args)

    /**
     * Slow-path conversion of a host throwable raised inside a sys-fn body into the throwable
     * that should leave the call boundary, plus the nested-frame stack decoration that the
     * tree-walker's [net.postchain.rell.base.runtime.Rt_InterpreterImpl.callTarget] applies.
     *
     * Two-step decoration:
     *
     * 1. [R_SysFunctionUtils.decorateSysFnException] — the same catch-arm logic as the
     *    interpreter's `callAndCatch` (Rell-exception extra-message remap, JDK-exception wrap
     *    when `wrapFunctionCallErrors`, [net.postchain.rell.base.runtime.utils.RellInterpreterCrashException] /
     *    SQL-cancel /
     *    `InterruptedException` passthroughs).
     * 2. If the result is an [Rt_Exception], append the call site's stack frame via
     *    [tfRethrowNested]; otherwise rethrow as-is.
     *
     * `@TruffleBoundary`: the entire path involves Kotlin/JDK libraries and exception
     * construction that PE can't usefully trace. The [BranchProfile.enter] in [execute]
     * is what keeps the boundary call out of compiled code on the success path.
     */
    @TruffleBoundary
    protected fun rethrowAfterDecorate(
        rt: Rt_CallFrame,
        callCtx: Rt_CallContext,
        callPos: FilePos,
        displayName: String,
        e: Throwable,
    ): Nothing {
        val decorated = R_SysFunctionUtils.decorateSysFnException(callCtx, LazyString.of(displayName), e)
        if (decorated is Rt_Exception) {
            tfRethrowNested(rt, ErrorPos(callPos), decorated)
        }
        throw decorated
    }

    /**
     * Global sys-fn call (no `base`). Translator-time fields:
     *
     * - [fn]: resolved [R_SysFunction] reference (compilation-local).
     * - [displayName]: pre-stripped via `sysFnDisplayName(fnName)` for error messages.
     * - [callPos]: call-site position for stack-frame decoration.
     * - [mapping] / [identityMapping]: argument permutation (identity skipped at runtime).
     */
    internal open class SysGlobal(
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal private val fn: R_SysFunction,
        @field:CompilationFinal private val displayName: String,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val identityMapping: Boolean,
    ) : Tf_SysCallNode() {

        /**
         * Cold-branch profile for the sys-fn exception path. PE folds the entire catch block
         * out of compiled code until the first throw trips the profile — replacing the
         * always-paid `callAndCatch` wrapper with a single guard that costs nothing on the
         * success path.
         */
        private val errorProfile: BranchProfile = BranchProfile.create()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Rt_Value {
            val mapped: List<Rt_Value> = buildArgList(frame)
            val callCtx = buildCallCtx(frame)
            return try {
                invokeSysFn(callCtx, fn, mapped)
            } catch (e: Throwable) {
                errorProfile.enter()
                rethrowAfterDecorate(tfRtFrame(frame), callCtx, callPos, displayName, e)
            }
        }

        /**
         * Identity case: evaluate args directly into a pre-sized `Array<Rt_Value>` and wrap it in
         * a [Tf_ArrayBackedList] view — no `ArrayList`/`grow` cost, one allocation pair (the array
         * + the wrapper). Non-identity case: keep a separate source-evaluated array because output
         * positions may pull from any source (the mapping spec allows duplicates).
         */
        @ExplodeLoop
        private fun buildArgList(frame: VirtualFrame): List<Rt_Value> {
            if (identityMapping) {
                val out = Array(args.size) { args[it].execute(frame) }
                return Tf_ArrayBackedList(out)
            }

            val evaluated = Array(args.size) { args[it].execute(frame) }

            val mapping = this.mapping
            val out = Array<Rt_Value>(mapping.size) { Tf_Unchecked.cast(evaluated[mapping[it]]) }
            return Tf_ArrayBackedList(out)
        }
    }

    /** Global sys-fn whose result is statically integer — overrides [executeLong] to unbox. */
    internal class SysGlobalInt(
        args: Array<Tf_ExprNode>,
        fn: R_SysFunction,
        displayName: String,
        callPos: FilePos,
        mapping: IntArray,
        identityMapping: Boolean,
    ) : SysGlobal(args, fn, displayName, callPos, mapping, identityMapping) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Global sys-fn whose result is statically boolean — overrides [executeBoolean] to unbox. */
    internal class SysGlobalBool(
        args: Array<Tf_ExprNode>,
        fn: R_SysFunction,
        displayName: String,
        callPos: FilePos,
        mapping: IntArray,
        identityMapping: Boolean,
    ) : SysGlobal(args, fn, displayName, callPos, mapping, identityMapping) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

    /**
     * Member sys-fn call (`base.fn(args)`). Same fields as [SysGlobal] plus a [base] child
     * node; runtime prepends `base` to the evaluated argument list.
     *
     * `safe` (== safe-navigation `?.`): if true and base evaluates to null, returns null
     * without evaluating args or calling the impl. Mirrors [Tf_FunctionCallNode.UserFn]'s
     * short-circuit.
     */
    internal open class SysMember(
        @field:Child private var base: Tf_ExprNode,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal private val fn: R_SysFunction,
        @field:CompilationFinal private val displayName: String,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val safe: Boolean,
    ) : Tf_SysCallNode() {

        /** See [SysGlobal.errorProfile] — same role on the member-call path. */
        private val errorProfile: BranchProfile = BranchProfile.create()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = base.execute(frame)
            // Reference equality on the singleton `Rt_NullValue` — see [Tf_FunctionCallNode.UserFn]
            // for why `==` is avoided on the hot path.
            if (safe && baseValue === Rt_NullValue) return Rt_NullValue

            val mapped: List<Rt_Value> = buildArgList(frame, baseValue)
            val callCtx = buildCallCtx(frame)
            return try {
                invokeSysFn(callCtx, fn, mapped)
            } catch (e: Throwable) {
                errorProfile.enter()
                rethrowAfterDecorate(tfRtFrame(frame), callCtx, callPos, displayName, e)
            }
        }

        /**
         * Identity case: evaluate args directly into a pre-sized `Array<Rt_Value>` (slot 0 holds
         * `baseValue`, slots 1..n hold args) and wrap it in a [Tf_ArrayBackedList]. Eliminates the
         * `ArrayList` / `grow` overhead that dominated sys-member arg-eval on stdlib-heavy
         * workloads. The interpreter's SysMember path passes `[base] + args`; mirror that exactly.
         */
        @ExplodeLoop
        private fun buildArgList(frame: VirtualFrame, baseValue: Rt_Value): List<Rt_Value> {
            if (identityMapping) {
                val args = this.args
                val out = arrayOfNulls<Rt_Value>(args.size + 1)
                out[0] = baseValue
                for (i in args.indices) {
                    out[i + 1] = args[i].execute(frame)
                }
                @Suppress("UNCHECKED_CAST")
                return Tf_ArrayBackedList(out as Array<Rt_Value>)
            }

            val evaluated = Array(args.size) { args[it].execute(frame) }

            val mapping = this.mapping
            val out = arrayOfNulls<Rt_Value>(mapping.size + 1)
            out[0] = baseValue
            for (i in mapping.indices) {
                out[i + 1] = Tf_Unchecked.cast(evaluated[mapping[i]])
            }
            @Suppress("UNCHECKED_CAST")
            return Tf_ArrayBackedList(out as Array<Rt_Value>)
        }
    }

    /** Member sys-fn whose result is statically integer. */
    internal class SysMemberInt(
        base: Tf_ExprNode,
        args: Array<Tf_ExprNode>,
        fn: R_SysFunction,
        displayName: String,
        callPos: FilePos,
        mapping: IntArray,
        identityMapping: Boolean,
        safe: Boolean,
    ) : SysMember(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Member sys-fn whose result is statically boolean. */
    internal class SysMemberBool(
        base: Tf_ExprNode,
        args: Array<Tf_ExprNode>,
        fn: R_SysFunction,
        displayName: String,
        callPos: FilePos,
        mapping: IntArray,
        identityMapping: Boolean,
        safe: Boolean,
    ) : SysMember(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }
}

/**
 * Strip the sys-fn key suffix down to the user-facing display name, exposed at translator
 * scope so [net.postchain.rell.base.runtime.truffle.Tf_Translator] can resolve it once at
 * translate time and pin it as a `@CompilationFinal` field on the call node.
 *
 * Wraps [sysFnDisplayName] (which lives in `runtime-core`); local re-export keeps the
 * translator's import surface narrow.
 */
internal fun resolveSysFnDisplayName(fnName: String): String = sysFnDisplayName(fnName)
