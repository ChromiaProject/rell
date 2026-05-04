/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.profiles.BranchProfile
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.R_SysFunctionUtils
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_DefinitionContext
import net.postchain.rell.base.runtime.Rt_Exception
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
     * Build the `Rt_CallContext` for a sys-fn call without lazy-allocating a callee
     * [net.postchain.rell.base.runtime.Rt_CallFrame].
     *
     * Mirrors [tfLazyAllocRtFrame]'s defCtx construction: `exeCtx` and `dbUpdateAllowed`
     * come from the outermost live caller (`tfPropagateRtFrame`, no alloc, no boundary);
     * `defId` comes from the current frame descriptor's `Tf_FrameInfo` so the ctx's defId
     * matches what the slow path would have built.
     *
     * The two object allocations here (`Rt_DefinitionContext` + `Rt_CallContext`) are the only
     * runtime cost beyond the sys-fn body itself — a far cry from the slow path's
     * `Rt_CallFrame` alloc + entire-frame `pushToLegacy`/`pullFromLegacy` mirror.
     */
    protected fun buildCallCtx(frame: VirtualFrame): Rt_CallContext {
        val info = Tf_Unchecked.cast<Tf_FrameInfo>(frame.frameDescriptor.info)
        val caller = tfPropagateRtFrame(frame)
        val defCtx = Rt_DefinitionContext(caller.exeCtx, caller.dbUpdateAllowed(), info.defId)
        return Rt_CallContext(defCtx)
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
        frame: VirtualFrame,
        callCtx: Rt_CallContext,
        callPos: FilePos,
        displayName: String,
        e: Throwable,
    ): Nothing {
        val decorated = R_SysFunctionUtils.decorateSysFnException(callCtx, LazyString.of(displayName), e)
        if (decorated is Rt_Exception) {
            tfRethrowNested(frame, ErrorPos(callPos), decorated)
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
            val evaluated = arrayOfNulls<Rt_Value>(args.size)
            for (i in args.indices) {
                evaluated[i] = args[i].execute(frame)
            }
            val mapped: List<Rt_Value> = buildArgList(evaluated)
            val callCtx = buildCallCtx(frame)
            return try {
                invokeSysFn(callCtx, fn, mapped)
            } catch (e: Throwable) {
                errorProfile.enter()
                rethrowAfterDecorate(frame, callCtx, callPos, displayName, e)
            }
        }

        @ExplodeLoop
        private fun buildArgList(evaluated: Array<Rt_Value?>): List<Rt_Value> {
            if (identityMapping) {
                return Tf_Unchecked.cast<Array<Rt_Value>>(evaluated).asList()
            }
            return buildList(mapping.size) {
                for (i in mapping.indices) {
                    add(Tf_Unchecked.cast(evaluated[mapping[i]]))
                }
            }
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

            val evaluated = arrayOfNulls<Rt_Value>(args.size)
            for (i in args.indices) {
                evaluated[i] = args[i].execute(frame)
            }
            val mapped: List<Rt_Value> = buildArgList(baseValue, evaluated)
            val callCtx = buildCallCtx(frame)
            return try {
                invokeSysFn(callCtx, fn, mapped)
            } catch (e: Throwable) {
                errorProfile.enter()
                rethrowAfterDecorate(frame, callCtx, callPos, displayName, e)
            }
        }

        @ExplodeLoop
        private fun buildArgList(baseValue: Rt_Value, evaluated: Array<Rt_Value?>): List<Rt_Value> {
            // The interpreter's SysMember path passes `[base] + args`; mirror that exactly.
            return buildList(mapping.size + 1) {
                add(baseValue)
                if (identityMapping) {
                    for (i in evaluated.indices) {
                        add(Tf_Unchecked.cast(evaluated[i]))
                    }
                } else {
                    for (i in mapping.indices) {
                        add(Tf_Unchecked.cast(evaluated[mapping[i]]))
                    }
                }
            }
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
