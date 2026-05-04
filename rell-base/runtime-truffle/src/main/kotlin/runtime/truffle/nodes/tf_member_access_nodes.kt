/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
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
import net.postchain.rell.base.runtime.asStruct
import net.postchain.rell.base.runtime.asTuple
import net.postchain.rell.base.runtime.asVirtualStruct
import net.postchain.rell.base.runtime.asVirtualTuple
import net.postchain.rell.base.runtime.truffle.Tf_FrameInfo
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked

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
internal sealed class Tf_MemberAccessNode : Tf_ExprNode() {
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
     */
    internal open class StructAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val attrIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ) : Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return baseValue.asStruct().get(attrIndex)
        }

        internal class IntAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ) : StructAttr(base, attrIndex, safe) {
            override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ) : StructAttr(base, attrIndex, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }

    /** Native: `t.N` tuple field access — `RR_MemberCalculator.TupleAttr`. */
    internal open class TupleAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val attrIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ) : Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return baseValue.asTuple()[attrIndex]
        }

        internal class IntAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ) : TupleAttr(base, attrIndex, safe) {
            override fun executeLong(frame: VirtualFrame): Long =
                Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolAttr(
            base: Tf_ExprNode,
            attrIndex: Int,
            safe: Boolean,
        ) : TupleAttr(base, attrIndex, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }

    /** Native: virtual tuple field access — `RR_MemberCalculator.VirtualTupleAttr`. */
    internal class VirtualTupleAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val fieldIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ) : Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return baseValue.asVirtualTuple().get(fieldIndex)
        }
    }

    /** Native: virtual struct attribute access — `RR_MemberCalculator.VirtualStructAttr`. */
    internal class VirtualStructAttr(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val attrDefIndex: Int,
        @field:CompilationFinal private val safe: Boolean,
    ) : Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return baseValue.asVirtualStruct().get(attrDefIndex)
        }
    }

    // -------------------------------------------------------------------------
    // Sys-function property reads (`RR_MemberCalculator.SysFunction`)
    // -------------------------------------------------------------------------

    /**
     * Build the `Rt_CallContext` for a member sys-fn call without lazy-allocating a callee
     * [net.postchain.rell.base.runtime.Rt_CallFrame]. Mirrors [Tf_SysCallNode.buildCallCtx].
     */
    protected fun buildCallCtx(frame: VirtualFrame): Rt_CallContext {
        val info = Tf_Unchecked.cast<Tf_FrameInfo>(frame.frameDescriptor.info)
        val caller = tfPropagateRtFrame(frame)
        val defCtx = Rt_DefinitionContext(caller.exeCtx, caller.dbUpdateAllowed(), info.defId)
        return Rt_CallContext(defCtx)
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
    ) : Tf_MemberAccessNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            val callCtx = buildCallCtx(frame)
            return invokeSysFn(callCtx, fn, displayName, listOf(baseValue))
        }

        internal class IntResult(
            base: Tf_ExprNode,
            fn: R_SysFunction,
            displayName: String,
            safe: Boolean,
        ) : SysFn(base, fn, displayName, safe) {
            override fun executeLong(frame: VirtualFrame): Long =
                Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
        }

        internal class BoolResult(
            base: Tf_ExprNode,
            fn: R_SysFunction,
            displayName: String,
            safe: Boolean,
        ) : SysFn(base, fn, displayName, safe) {
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
    ) : Tf_MemberAccessNode() {

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue

            val evaluated = arrayOfNulls<Rt_Value>(args.size)
            for (i in args.indices) {
                evaluated[i] = args[i].execute(frame)
            }
            val mapped: List<Rt_Value> = buildArgList(baseValue, evaluated)
            val callCtx = buildCallCtx(frame)
            return try {
                invokeSysFn(callCtx, fn, displayName, mapped)
            } catch (e: Rt_Exception) {
                rethrowNested(frame, callPos, e)
            }
        }

        @ExplodeLoop
        private fun buildArgList(baseValue: Rt_Value, evaluated: Array<Rt_Value?>): List<Rt_Value> = buildList(mapping.size + 1) {
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

        @TruffleBoundary
        private fun rethrowNested(frame: VirtualFrame, callPos: FilePos, e: Rt_Exception): Nothing =
            tfRethrowNested(frame, ErrorPos(callPos), e)

        internal class IntResult(
            base: Tf_ExprNode,
            args: Array<Tf_ExprNode>,
            fn: R_SysFunction,
            displayName: String,
            callPos: FilePos,
            mapping: IntArray,
            identityMapping: Boolean,
            safe: Boolean,
        ) : SysMemberFnCall(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
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
        ) : SysMemberFnCall(base, args, fn, displayName, callPos, mapping, identityMapping, safe) {
            override fun executeBoolean(frame: VirtualFrame): Boolean =
                Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
        }
    }
}
