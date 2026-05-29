/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.staticobject.StaticProperty
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_FrameInfo
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked
import net.postchain.rell.base.runtime.truffle.values.Tf_DynStruct

/**
 * Native: direct dispatch for [net.postchain.rell.base.model.rr.RR_Expr.MemberAccess].
 *
 * Mirrors [net.postchain.rell.base.runtime.evaluateMemberAccess] /
 * [net.postchain.rell.base.runtime.evaluateMemberCalculator] in the tree-walker. The translator
 * picks one of these subclasses per [net.postchain.rell.base.model.rr.RR_MemberCalculator] shape
 * so PE sees a known dispatch shape at the call site instead of a fallback that drags
 * `Tf_FallbackExprNode -> Rt_InterpreterImpl.evaluateMemberAccess` into every member-read path.
 *
 * Coverage: simple attribute reads (struct, tuple, virtual tuple, virtual struct), sys-function
 * properties (`enum.name`, `module.name`, …), and sys-member function calls (`gtv.to_bytes()`,
 * `text.size()`, …). Calculators that depend on per-call SQL or lambda-block state
 * (`DataAttribute`, `DataAttributeExpr`, `ExprEval`, non-sys `FunctionCall.Full`, `Partial`) keep
 * routing through fallback — they are rare on the FT4 hot path.
 */
internal sealed class Tf_MemberAccessNode: Tf_ExprNode() {
    /**
     * Common safe-navigation prelude. Returns `Rt_NullValue` if `safe` and the base evaluates
     * to null; the caller then short-circuits without further work.
     */
    protected fun evaluateBase(frame: VirtualFrame, baseChild: Tf_ExprNode, safe: Boolean): Rt_Value? {
        val baseValue = baseChild.execute(frame)
        if (safe && baseValue === Rt_NullValue) return null
        return baseValue
    }

    // -------------------------------------------------------------------------
    // Simple attribute reads
    // -------------------------------------------------------------------------

    /**
     * Native: `s.attr` for struct values reached via `RR_Expr.MemberAccess` (the
     * `RR_MemberCalculator.StructAttr` shape). Distinct from [Tf_StructMemberNode] which serves
     * the dedicated `RR_Expr.StructMember` IR shape; both end up calling `asStruct().get(idx)`.
     *
     * Fast path: when [somProperty] is non-null (the translator captures the SOM
     * [StaticProperty] for the static struct type) AND the runtime base is a [Tf_DynStruct],
     * read directly from the SOM-generated slot — one `getfield`-equivalent after PE. The
     * generic [execute] returns an `Rt_Value` and routes through [Tf_DynStruct.get] for
     * slot-kind-aware boxing; the typed [IntAttr.executeLong] / [BoolAttr.executeBoolean]
     * subclasses skip the box and read the primitive slot directly.
     * Slow path: virtual `asStruct().get(attrIndex)` against
     * [net.postchain.rell.base.runtime.Rt_HeapStruct] or any other
     * [net.postchain.rell.base.runtime.Rt_StructValue] subclass.
     */
    internal open class StructAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal protected val attrIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
        @field:CompilationFinal protected val somProperty: StaticProperty?,
    ): Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            if (somProperty != null && baseValue is Tf_DynStruct) {
                return baseValue.get(attrIndex)
            }
            return (baseValue as Rt_StructValue).get(attrIndex)
        }

        /**
         * Evaluate the base for typed-primitive subclasses ([IntAttr]/[BoolAttr]). The translator
         * only picks the typed subclasses for non-nullable primitive results, which forbids
         * `safe == true` (a safe-call expression always has a nullable static type), so the
         * `Rt_NullValue` fallback below is dead today. Kept as a defensive sentinel — `prop.getLong`
         * would throw an obscure `ClassCastException` against `Rt_NullValue` if the safe-call
         * invariant ever loosened. See [IntAttr]/[BoolAttr] for the assumption.
         */
        protected fun evaluateBaseRaw(frame: VirtualFrame): Rt_Value {
            assert(!safe) { "typed primitive attr access invoked under safe-call" }
            return evaluateBase(frame, base, safe) ?: Rt_NullValue
        }

        internal class IntAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
            somProperty: StaticProperty?,
        ): StructAttr(base, attrIndex, safe, somProperty) {
            override fun executeLong(frame: VirtualFrame): Long {
                val baseValue = evaluateBaseRaw(frame)
                val prop = somProperty
                if (prop != null && baseValue is Tf_DynStruct) {
                    // Direct primitive read: PE folds `prop.getLong(baseValue)` to a single
                    // field load against the generated SOM class — no Rt_IntValue allocation.
                    return prop.getLong(baseValue)
                }
                return Tf_Unchecked.cast<Rt_IntValue>((baseValue as Rt_StructValue).get(attrIndex)).value
            }
        }

        internal class BoolAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
            somProperty: StaticProperty?,
        ): StructAttr(base, attrIndex, safe, somProperty) {
            override fun executeBoolean(frame: VirtualFrame): Boolean {
                val baseValue = evaluateBaseRaw(frame)
                val prop = somProperty
                if (prop != null && baseValue is Tf_DynStruct) {
                    return prop.getBoolean(baseValue)
                }
                return Tf_Unchecked.cast<Rt_BooleanValue>((baseValue as Rt_StructValue).get(attrIndex)).value
            }
        }
    }

    /** Native: `t.N` tuple field access — `RR_MemberCalculator.TupleAttr`. */
    internal open class TupleAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val attrIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return (baseValue as Rt_TupleValue).elements[attrIndex]
        }

        internal class IntAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ): TupleAttr(base, attrIndex, safe) {
            override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ): TupleAttr(base, attrIndex, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }

    /** Native: virtual tuple field access — `RR_MemberCalculator.VirtualTupleAttr`. */
    internal class VirtualTupleAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val fieldIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return (baseValue as Rt_VirtualTupleValue).get(fieldIndex)
        }
    }

    /** Native: virtual struct attribute access — `RR_MemberCalculator.VirtualStructAttr`. */
    internal class VirtualStructAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val attrDefIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value =
            (evaluateBase(frame, base, safe) as? Rt_VirtualStructValue)?.get(attrDefIndex) ?: Rt_NullValue
    }

    // -------------------------------------------------------------------------
    // Sys-function property reads (`RR_MemberCalculator.SysFunction`)
    // -------------------------------------------------------------------------

    /**
     * Inline cache for [buildCallCtx] — see `Tf_SysCallNode.cachedCallCtx` for the rationale.
     */
    private var cachedCallExeCtx: Rt_ExecutionContext? = null
    private var cachedCallDbUpdate: Boolean = false
    private var cachedCallCtx: Rt_CallContext? = null

    /**
     * Build the `Rt_CallContext` for a member sys-fn call without lazy-allocating a callee
     * [net.postchain.rell.base.runtime.Rt_CallFrame]. Mirrors [Tf_SysCallNode.buildCallCtx];
     * single-element cache eliminates the per-call wrapper allocation in steady state.
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
     * Sys-fn dispatch behind a `@TruffleBoundary` — keeps [R_SysFunctionUtils.call]'s
     * `callAndCatch` exception-tagging logic out of PE for the same reason as
     * [Tf_SysCallNode.invokeSysFn].
     */
    @TruffleBoundary
    protected fun invokeSysFn(
        callCtx: Rt_CallContext,
        fn: R_SysFunction,
        displayName: String,
        args: List<Rt_Value>,
    ): Rt_Value = R_SysFunctionUtils.call(callCtx, fn, displayName, args)

    /**
     * Native: sys-property/sys-fn member access — `RR_MemberCalculator.SysFunction`. The
     * tree-walker emits this for type-bound sys properties like `enum.name`, `module.name`,
     * `gtv_pretty.size`. The args list is always `[base]` — the calculator carries no further
     * arguments — so we can specialise the dispatch much harder than the variable-arity
     * [Tf_SysCallNode.SysMember] path.
     */
    internal open class SysFn(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val fn: R_SysFunction,
        @field:CompilationFinal private val displayName: String,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return invokeSysFn(buildCallCtx(frame), fn, displayName, listOf(baseValue))
        }

        internal class IntResult(
            base: Tf_ExprNode,
            fn: R_SysFunction,
            displayName: String,
            safe: Boolean,
        ): SysFn(base, fn, displayName, safe) {
            override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolResult(
            base: Tf_ExprNode,
            fn: R_SysFunction,
            displayName: String,
            safe: Boolean,
        ): SysFn(base, fn, displayName, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }

    // -------------------------------------------------------------------------
    // Sys-member function calls — `RR_MemberCalculator.FunctionCall(target=SysMember)`
    // -------------------------------------------------------------------------

    /**
     * Native: sys-member function call reached via member-access — e.g. `gtv.to_bytes()`,
     * `text.size()`. The tree-walker's [net.postchain.rell.base.runtime.evaluateMemberCalculator]
     * dispatches this through `callTarget(target=SysMember, base, args, …)` which is the same
     * shape the regular [Tf_SysCallNode.SysMember] node already serves — but the IR shape is
     * different (`MemberAccess.calculator=FunctionCall` vs `FunctionCall(target=SysMember)`),
     * so the translator routes here.
     *
     * The ctor takes the resolved [R_SysFunction] reference, the pre-stripped display name, and
     * the call's argument expressions / mapping. At runtime: evaluate the base, optionally
     * short-circuit on safe-null, evaluate args, then dispatch via [invokeSysFn] with `[base, args…]`.
     */
    internal open class SysMemberFnCall(
        @field:Child private var base: Tf_ExprNode,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal private val fn: R_SysFunction,
        @field:CompilationFinal private val displayName: String,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_MemberAccessNode() {

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue

            val mapped: List<Rt_Value> = buildArgList(frame, baseValue)
            val callCtx = buildCallCtx(frame)

            return try {
                invokeSysFn(callCtx, fn, displayName, mapped)
            } catch (e: Rt_Exception) {
                rethrowNested(tfRtFrame(frame), callPos, e)
            }
        }

        /**
         * Identity case: evaluate args directly into a pre-sized `Array<Rt_Value>` (slot 0 holds
         * `baseValue`, slots 1..n hold args) and wrap it in a [Tf_ArrayBackedList]. Skips the
         * `ArrayList` wrapper plus its `grow` cost — the dominant SysMemberFnCall arg-eval cost on
         * stdlib-heavy workloads (~2% on `bench_locations`).
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

        @TruffleBoundary
        private fun rethrowNested(rt: Rt_CallFrame, callPos: FilePos, e: Rt_Exception): Nothing =
            tfRethrowNested(rt, ErrorPos(callPos), e)

        internal class IntResult(
            base: Tf_ExprNode,
            args: Array<Tf_ExprNode>,
            fn: R_SysFunction,
            displayName: String,
            callPos: FilePos,
            mapping: IntArray,
            identityMapping: Boolean,
            safe: Boolean,
        ): SysMemberFnCall(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
            override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolResult(
            base: Tf_ExprNode,
            args: Array<Tf_ExprNode>,
            fn: R_SysFunction,
            displayName: String,
            callPos: FilePos,
            mapping: IntArray,
            identityMapping: Boolean,
            safe: Boolean,
        ): SysMemberFnCall(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }
}
