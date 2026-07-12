/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.rr.RR_Expr
import net.postchain.rell.base.model.rr.RR_Statement
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.*

/**
 * Bridge from a Truffle [VirtualFrame] back to a legacy [Rt_CallFrame] view, lazy-allocating
 * one for the inner-entry path when no slow-path node has yet demanded it.
 */
internal fun tfRtFrame(frame: VirtualFrame): Rt_CallFrame {
    val cached = frame.getAuxiliarySlot(TF_RT_FRAME_AUX_SLOT)
    if (cached != null) return Tf_Unchecked.cast(cached)
    // Materialise once on the slow path. The Truffle DSL forbids `VirtualFrame` parameters
    // across `@TruffleBoundary` (DSL contract — non-materialised frames cannot cross a
    // boundary), so we hand the boundary a [MaterializedFrame] instead. The materialisation
    // cost is paid only on the first cache miss; the hot path (cache hit) never reaches here.
    return tfLazyAllocRtFrame(frame.materialize())
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
 * Takes a [MaterializedFrame] rather than a [VirtualFrame] because the Truffle Bytecode DSL
 * annotation processor rejects `@TruffleBoundary` methods that take a `VirtualFrame` parameter
 * (non-materialised frames cannot cross a boundary). [MaterializedFrame] is a Truffle subtype
 * of [VirtualFrame] that the DSL accepts; the materialisation cost is paid only on the slow
 * path (cache miss in [tfRtFrame]), so PE-virtualisation of the hot path is unaffected.
 *
 * Note that [TruffleBoundary] is mandatory here: [Rt_DefinitionContext] / [Rt_CallFrame]
 * constructors live in `runtime-interpreter` (and `runtime-core`) and call into helpers whose
 * Java bytecode shape is unrelated to the call payload. Without the boundary, PE would inline
 * those constructors into every node that touches [tfRtFrame] and bloat the compiled graph.
 */
@TruffleBoundary
private fun tfLazyAllocRtFrame(frame: MaterializedFrame): Rt_CallFrame {
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
 * Takes [Rt_CallFrame] rather than [VirtualFrame] because the Truffle Bytecode DSL annotation
 * processor rejects `@TruffleBoundary` methods that take a `VirtualFrame` parameter. Callers
 * extract the [Rt_CallFrame] via [tfRtFrame] *before* the boundary call.
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
internal fun tfRethrowAt(rt: Rt_CallFrame, errPos: ErrorPos, e: Rt_Exception): Nothing =
    rt.error(errPos, e)

/** See [tfRethrowAt]. Variant for nested-call sites that always append a stack frame. */
@TruffleBoundary
internal fun tfRethrowNested(rt: Rt_CallFrame, errPos: ErrorPos, e: Rt_Exception): Nothing =
    rt.error(errPos, e, nested = true)

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
     * Statement-shaped execute path: runs the node for side effects. `return` / `break` /
     * `continue` propagate as the conventional Truffle control-flow exceptions
     * ([net.postchain.rell.base.runtime.truffle.Tf_ReturnException] /
     * [net.postchain.rell.base.runtime.truffle.Tf_BreakException] /
     * [net.postchain.rell.base.runtime.truffle.Tf_ContinueException]): loop nodes catch
     * break/continue around their body, the function-body root catches return.
     *
     * Why the default is to call [execute] and discard: it lets every existing expression node
     * (binary ops, var reads, function calls, …) participate in a statement context — e.g. the
     * `Tf_ExprStmtNode` wrapper — without each one re-overriding `executeStmt`.
     */
    open fun executeStmt(frame: VirtualFrame) {
        execute(frame)
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
    open fun executeLong(frame: VirtualFrame): Long = (execute(frame) as Rt_IntValue).value

    /**
     * Typed boolean execute path. Same idea as [executeLong] — control-flow conditions
     * (`if`, `while`, short-circuit `&&`/`||`) call [executeBoolean] and skip the
     * intermediate [net.postchain.rell.base.runtime.Rt_BooleanValue] when the operand is a comparison node, a boolean
     * constant, etc.
     */
    open fun executeBoolean(frame: VirtualFrame): Boolean = (execute(frame) as Rt_BooleanValue).value

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
 * Convert the tree-walker's value-block escape into the Truffle control-flow exceptions.
 * Needed wherever a Truffle node hands an *untranslated* [RR_Expr] subtree to the delegate
 * ([Tf_FallbackExprNode], the catch-all [Tf_AssignStmtNode]): a `return`/`break`/`continue`
 * escaping from a value block inside that subtree leaves `delegate.evaluateExpr`/`assignTo`
 * as an [Rt_ValueBlockEscapeException] (the interpreter converts it only at its own statement
 * boundaries, which expression evaluation never crosses). `delegate.executeStmt` needs no such
 * conversion — the interpreter's wrapper already turns the escape into an [Rt_StatementResult].
 */
internal fun tfConvertValueBlockEscape(e: Rt_ValueBlockEscapeException): Nothing =
    when (val result = e.result) {
        Rt_StatementResult.Break -> throw Tf_BreakException.INSTANCE
        Rt_StatementResult.Continue -> throw Tf_ContinueException.INSTANCE
        is Rt_StatementResult.Return -> throw Tf_ReturnException(result.value)
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

    /**
     * Public override stays on `VirtualFrame` (the parent contract) and itself carries no
     * `@TruffleBoundary`. The boundary lives on [executeBoundary] so the Truffle Bytecode DSL
     * annotation processor's rule (`@TruffleBoundary` methods may not take a `VirtualFrame`
     * parameter — non-materialised frames cannot cross a boundary) is satisfied while the
     * surrounding JIT graph still treats the fallback body as opaque.
     */
    override fun execute(frame: VirtualFrame): Rt_Value {
        return try {
            executeBoundary(tfRtFrame(frame))
        } catch (e: Rt_ValueBlockEscapeException) {
            tfConvertValueBlockEscape(e)
        }
    }

    @TruffleBoundary
    private fun executeBoundary(rt: Rt_CallFrame): Rt_Value = backend.delegate.evaluateExpr(expr, rt)
}

/**
 * Catch-all statement node that delegates to [net.postchain.rell.base.runtime.Rt_InterpreterImpl].
 *
 * Translates the tree-walker's [Rt_StatementResult] into the control-flow exceptions:
 * `Return → Tf_ReturnException` (carrying the value), `Break → Tf_BreakException`,
 * `Continue → Tf_ContinueException`. Loop nodes consume break/continue; the function-body root
 * consumes return.
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

    /**
     * Public override stays on `VirtualFrame`. Extracts [Rt_CallFrame] before the boundary
     * call so the inner [executeStmtBoundary] satisfies the Truffle Bytecode DSL's rule that
     * `@TruffleBoundary` methods may not take a `VirtualFrame` parameter.
     */
    override fun executeStmt(frame: VirtualFrame) {
        val rt = tfRtFrame(frame)
        when (val res = executeStmtBoundary(rt)) {
            null -> {}
            Rt_StatementResult.Break -> throw Tf_BreakException.INSTANCE
            Rt_StatementResult.Continue -> throw Tf_ContinueException.INSTANCE
            is Rt_StatementResult.Return -> throw Tf_ReturnException(res.value)
        }
    }

    /**
     * `@TruffleBoundary`: same reasoning as [Tf_FallbackExprNode.executeBoundary] —
     * `executeStmt` recursively dispatches into the tree-walker. Keep it out of the compiled
     * graph.
     */
    @TruffleBoundary
    private fun executeStmtBoundary(rt: Rt_CallFrame): Rt_StatementResult? =
        backend.delegate.executeStmt(stmt, rt)
}
