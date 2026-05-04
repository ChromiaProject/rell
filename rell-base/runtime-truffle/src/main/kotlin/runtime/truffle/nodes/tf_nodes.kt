/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.rr.RR_Expr
import net.postchain.rell.base.model.rr.RR_Statement
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_DefinitionContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_StatementResult
import net.postchain.rell.base.runtime.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.asBoolean
import net.postchain.rell.base.runtime.asInteger
import net.postchain.rell.base.runtime.truffle.STATUS_BREAK
import net.postchain.rell.base.runtime.truffle.STATUS_CONTINUE
import net.postchain.rell.base.runtime.truffle.STATUS_FALLTHROUGH
import net.postchain.rell.base.runtime.truffle.STATUS_RETURN
import net.postchain.rell.base.runtime.truffle.TF_RETURN_VALUE_AUX_SLOT
import net.postchain.rell.base.runtime.truffle.TF_RT_FRAME_AUX_SLOT
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.runtime.truffle.Tf_FrameInfo
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked
import net.postchain.rell.base.runtime.truffle.Tf_VirtualFrameStorage

/**
 * Bridge from a Truffle [VirtualFrame] back to a legacy [Rt_CallFrame] view, lazy-allocating
 * one for the inner-entry path when no slow-path node has yet demanded it.
 */
internal fun tfRtFrame(frame: VirtualFrame): Rt_CallFrame {
    val cached = frame.getAuxiliarySlot(TF_RT_FRAME_AUX_SLOT)
    if (cached != null) return Tf_Unchecked.cast(cached)
    return tfLazyAllocRtFrame(frame)
}

/**
 * Fast-path lookup of the **outermost live** [Rt_CallFrame] visible from [frame] — used solely
 * to seed the `arguments[1]` slot of an inner-entry call site. Never lazy-allocates; never
 * crosses a [TruffleBoundary]; never materialises [frame].
 *
 * Two reads, in order:
 *
 * 1. **Aux slot.** Outer-entry roots stash the current frame's [Rt_CallFrame] there. If set,
 *    that's the live Rt_CallFrame at this depth — return it.
 * 2. **`arguments[1]`.** Inner-entry callees never populated their aux slot (the whole point of
 *    wave 3). Instead, the inner-entry caller propagates its own caller's Rt_CallFrame through
 *    `arguments[1]`. Reading it back here gives the same outermost Rt_CallFrame the original
 *    outer-entry caller stashed — no chain walk, no allocation.
 *
 * The two paths converge to the same `Rt_CallFrame` instance: the one created by the driver at
 * the top of the call stack. Inner-entry chains thread it through `arguments[1]` without ever
 * touching their own aux slot, which keeps [frame] virtualisable through PE.
 *
 * # Why we can't reuse [tfRtFrame] here
 *
 * [tfRtFrame] eagerly lazy-allocates a callee-specific [Rt_CallFrame] when the aux slot is
 * empty. That allocation routes through [tfLazyAllocRtFrame] (`@TruffleBoundary`), which forces
 * Graal to materialise [frame] before crossing the boundary — and a materialised
 * `FrameWithoutBoxing` flips the recursive `Tf_FunctionRootNode`'s tier-1 compile from "inline
 * candidate" to `SourceStackTraceBailoutException ("Object [...] should not be materialized")`.
 * Inner-entry call sites never need a callee-specific [Rt_CallFrame]; they need only the
 * outermost live one, for `exeCtx` propagation. Hence the dedicated, no-alloc lookup.
 */
internal fun tfPropagateRtFrame(frame: VirtualFrame): Rt_CallFrame {
    val cached = frame.getAuxiliarySlot(TF_RT_FRAME_AUX_SLOT)
    if (cached != null) return Tf_Unchecked.cast(cached)
    // Inner-entry frame: parent's Rt_CallFrame is in arguments[1], stashed there by the
    // caller's `buildInnerCallArgs`. By induction (inner entries always seed arguments[1]
    // from `tfPropagateRtFrame` of their own caller, and the outermost frame in the chain
    // is outer-entry with a populated aux slot), this is non-null on every well-formed
    // inner-entry frame.
    return Tf_Unchecked.cast(frame.arguments[1])
}

/**
 * Slow path of [tfRtFrame] — runs only on the inner-entry path the first time a slow-path node
 * demands an [Rt_CallFrame].
 *
 * Note that [TruffleBoundary] is mandatory here: [Rt_DefinitionContext] / [Rt_CallFrame]
 * constructors live in `runtime-interpreter` (and `runtime-core`) and call into helpers whose
 * Java bytecode shape is unrelated to the call payload. Without the boundary, PE would inline
 * those constructors into every node that touches [tfRtFrame] and bloat the compiled graph.
 */
@TruffleBoundary
private fun tfLazyAllocRtFrame(frame: VirtualFrame): Rt_CallFrame {
    val info = Tf_Unchecked.cast<Tf_FrameInfo>(frame.frameDescriptor.info)
    val caller = Tf_Unchecked.cast<Rt_CallFrame>(frame.arguments[1])
    val defCtx = Rt_DefinitionContext(caller.exeCtx, caller.dbUpdateAllowed(), info.defId)
    val callee = Rt_CallFrame(defCtx, info.rrFrame, null, Tf_VirtualFrameStorage(frame))
    frame.setAuxiliarySlot(TF_RT_FRAME_AUX_SLOT, callee)
    return callee
}

/**
 * `@TruffleBoundary` slow-path: decorate an [Rt_Exception] with a Rell stack frame and rethrow.
 *
 * Keeping this opaque to PE is critical. [Rt_CallFrame.error] builds a new stack list with
 * `ImmList<R_StackPos>.plus(stackPos)`, which routes through
 * `kotlinx.collections.immutable.toPersistentList`. That library is *third-party* and we can't
 * strip its `Intrinsics.checkNotNullParameter` calls — and those intrinsics drag in the JDK
 * `StringBuilder.appendNull → Locale → SecurityManager → AccessControlContext` recursion cycle
 * that overflows Graal's inliner-depth budget. With the boundary here, the catch handler in
 * each Tf_*Node compiles to a single boundary call instead of pulling the whole error path
 * into the compiled graph.
 */
@TruffleBoundary
internal fun tfRethrowAt(frame: VirtualFrame, errPos: ErrorPos, e: Rt_Exception): Nothing =
    tfRtFrame(frame).error(errPos, e)

/** See [tfRethrowAt]. Variant for nested-call sites that always append a stack frame. */
@TruffleBoundary
internal fun tfRethrowNested(frame: VirtualFrame, errPos: ErrorPos, e: Rt_Exception): Nothing =
    tfRtFrame(frame).error(errPos, e, nested = true)

/**
 * Base class for Truffle expression nodes in the Rell backend.
 *
 * Every [Tf_ExprNode] consumes an [Rt_CallFrame] (the live activation record) and produces a
 * single [Rt_Value]. Mirrors the contract of `Rt_InterpreterImpl.evaluateExpr(RR_Expr,
 * Rt_CallFrame)` exactly.
 *
 * Why we extend Truffle's [Node] directly rather than using the DSL processor's generated
 * specialisation classes: hand-rolled nodes give us a predictable Kotlin codebase with no
 * generated `*NodeGen` siblings, which keeps the module compatible with `kapt`-free
 * configurations and side-steps Truffle's non-trivial annotation-processor dance under recent
 * Kotlin versions. The trade-off is that we don't get automatic inline caches; we hand-write
 * them with `@CompilationFinal` where needed.
 */
internal abstract class Tf_ExprNode : Node() {
    abstract fun execute(frame: VirtualFrame): Rt_Value

    /**
     * Statement-shaped execute path: runs the node for side effects and returns a control-flow
     * status code (one of [STATUS_FALLTHROUGH], [STATUS_RETURN], [STATUS_BREAK],
     * [STATUS_CONTINUE]). When the status is [STATUS_RETURN] the return value (or `null` for
     * `return;`) is in the [TF_RETURN_VALUE_AUX_SLOT] aux slot.
     *
     * The default falls through: nodes that have no notion of `return`/`break`/`continue`
     * (every expression node, the simple-var/assign nodes, the no-op statement, ...) execute
     * for side effects and return [STATUS_FALLTHROUGH]. Statement nodes that *do* propagate
     * control flow (`Tf_ReturnStmtNode`, `Tf_BreakStmtNode`, `Tf_ContinueStmtNode`,
     * `Tf_BlockStmtNode_*`, `Tf_IfStmtNode`, `Tf_WhileStmtNode`, `Tf_ForStmtNode`,
     * `Tf_WhenStmtNode`, `Tf_FallbackStmtNode`) override this to return the status directly.
     *
     * Why the default is to call [execute] and discard: it lets every existing expression node
     * (binary ops, var reads, function calls, …) participate in a statement context — e.g. the
     * `Tf_ExprStmtNode` wrapper — without each one re-overriding `executeStmt`. PE folds the
     * `STATUS_FALLTHROUGH` constant return through, so loop and block nodes' `if (status != 0)`
     * checks compile to a single primitive comparison.
     */
    open fun executeStmt(frame: VirtualFrame): Int {
        execute(frame)
        return STATUS_FALLTHROUGH
    }

    /**
     * Typed integer execute path. Default implementation runs [execute] and unboxes the
     * result; specialised nodes (constants, integer arithmetic, integer var reads) override
     * this to skip allocating an intermediate [net.postchain.rell.base.runtime.Rt_IntValue] altogether.
     *
     * The trade-off is that callers must know — at translate time — that the expression is
     * statically integer-typed. The translator only invokes [executeLong] from contexts where
     * the type is provably `integer` (e.g. the operands of an integer arithmetic node), so
     * the unbox-on-default path is a safety net rather than a normal occurrence.
     */
    open fun executeLong(frame: VirtualFrame): Long = execute(frame).asInteger()

    /**
     * Typed boolean execute path. Same idea as [executeLong] — control-flow conditions
     * (`if`, `while`, short-circuit `&&`/`||`) call [executeBoolean] and skip the
     * intermediate [net.postchain.rell.base.runtime.Rt_BooleanValue] when the operand is a comparison node, a boolean
     * constant, etc.
     */
    open fun executeBoolean(frame: VirtualFrame): Boolean = execute(frame).asBoolean()

    /**
     * Translator-time signal: `true` if this node (or any descendant) takes a slow path
     * that depends on `Rt_CallFrame`'s block-uid validation (`frame.get(ptr)` /
     * `setUnchecked(ptr, ...)`) — i.e. fallback nodes and the general-shape var/assign
     * nodes that route through `initializeDeclarator` / `assignTo`.
     *
     * Block/loop nodes inspect this on their children at translate time and skip the
     * `enterBlockSet` / `clearSlotsRange` bookkeeping entirely when no descendant needs
     * it. The hot path then becomes a plain Kotlin loop over fast-path-only children
     * with no per-iteration block-state thrash.
     *
     * Default walks Truffle's `@Child` / `@Children` adoption graph via [getChildren]:
     * each [Tf_ExprNode] descendant inherits its OR-of-descendants without per-class
     * boilerplate. Slow-path leaf nodes (`Tf_FallbackExprNode`, `Tf_FallbackStmtNode`,
     * `Tf_VarStmtNode` general, `Tf_AssignStmtNode` general) override the property to
     * return `true`. Block/loop nodes consult this on their already-built child tree
     * and can fold the bookkeeping out at construction time.
     *
     * The walk runs at translate time (block/loop ctor inspects children), not on the
     * hot path — recursion cost is amortised across all subsequent invocations.
     */
    open val needsBlockState: Boolean
        get() = childrenNeedBlockState()

    /** Recursively check if any Truffle child needs block state. */
    private fun childrenNeedBlockState(): Boolean {
        for (child in this.children) {
            if (child is Tf_ExprNode && child.needsBlockState) return true
        }
        return false
    }
}

/** True if any node in [nodes] needs slow-path block-state. */
internal fun anyNeedsBlockState(nodes: Array<Tf_ExprNode>): Boolean {
    for (node in nodes) if (node.needsBlockState) return true
    return false
}

/**
 * Catch-all expression node that delegates to [net.postchain.rell.base.runtime.Rt_InterpreterImpl].
 *
 * Used by the translator for any [RR_Expr] variant that doesn't yet have a hand-written Truffle
 * counterpart. The fallback is *correct by construction* — the interpreter is the canonical
 * reference implementation, so passing the un-translated AST through it gives bit-identical
 * results to the tree-walker.
 *
 * Performance: a fallback call is one virtual dispatch (`backend.delegate.evaluateExpr(expr,
 * frame)`) plus the cost of the original tree-walk. Graal still gains the benefit of
 * specialising the surrounding Truffle nodes (block, loop, return) around the fallback's
 * argument types; the fallback itself is opaque to the JIT.
 *
 * As specific RR_Expr variants get hand-rolled nodes, the translator stops emitting fallbacks
 * for them. Eventually the fallback should be a rarely-hit safety net.
 */
internal class Tf_FallbackExprNode(
    private val backend: Tf_Backend,
    @CompilationFinal private val expr: RR_Expr,
) : Tf_ExprNode() {
    override val needsBlockState: Boolean
        get() = true

    @TruffleBoundary
    override fun execute(frame: VirtualFrame): Rt_Value {
        val rt = tfRtFrame(frame)
        val result = backend.delegate.evaluateExpr(expr, rt)
        return result
    }
}

/**
 * Catch-all statement node that delegates to [net.postchain.rell.base.runtime.Rt_InterpreterImpl].
 *
 * Translates the tree-walker's [Rt_StatementResult] into the status-code protocol:
 * `Return → STATUS_RETURN` (writes value to [TF_RETURN_VALUE_AUX_SLOT]),
 * `Break → STATUS_BREAK`, `Continue → STATUS_CONTINUE`. Loop nodes consume the break/continue
 * codes locally; the function-body root reads the slot when it sees [STATUS_RETURN].
 */
internal class Tf_FallbackStmtNode(
    private val backend: Tf_Backend,
    @CompilationFinal private val stmt: RR_Statement,
) : Tf_ExprNode() {
    /** See [Tf_FallbackExprNode.needsBlockState] — same reasoning. */
    override val needsBlockState: Boolean
        get() = true

    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int {
        val rt = tfRtFrame(frame)
        return when (val res = runFallback(rt)) {
            null -> STATUS_FALLTHROUGH
            Rt_StatementResult.Break -> STATUS_BREAK
            Rt_StatementResult.Continue -> STATUS_CONTINUE
            is Rt_StatementResult.Return -> {
                frame.setAuxiliarySlot(TF_RETURN_VALUE_AUX_SLOT, res.value)
                STATUS_RETURN
            }
        }
    }

    /**
     * `@TruffleBoundary`: same reasoning as [Tf_FallbackExprNode] — `executeStmt` recursively
     * dispatches into the tree-walker. Keep it out of the compiled graph.
     */
    @TruffleBoundary
    private fun runFallback(frame: Rt_CallFrame): Rt_StatementResult? = backend.delegate.executeStmt(stmt, frame)
}
