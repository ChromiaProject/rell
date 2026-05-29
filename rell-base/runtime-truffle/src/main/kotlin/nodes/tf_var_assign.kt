/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import net.postchain.rell.base.model.rr.RR_BinaryOp
import net.postchain.rell.base.model.rr.RR_Expr
import net.postchain.rell.base.model.rr.RR_VarDeclarator
import net.postchain.rell.base.model.rr.RR_VarPtr
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_Backend

/**
 * Native: simple variable declaration. Specialised for `Simple`-declarator + null-adapter
 * (the common `var x = expr` shape — no tuple destructuring, no implicit conversion). PE
 * folds this to a single `frame.setObject(slot, value)`; no boundary, no
 * declarator-shape dispatch, no `checkPtr` block-uid validation in the compiled graph.
 * The `Object`-kind FrameDescriptor slot is virtualised by Truffle into compiled-graph
 * SSA storage when the surrounding loop is JIT-compiled.
 *
 * Tuple/Wildcard declarators or non-trivial type adapters fall back to [Tf_VarStmtNode].
 */
internal open class Tf_VarSimpleStmtNode(
    ptr: RR_VarPtr,
    @field:Child private var initExpr: Tf_ExprNode?,
) : Tf_ExprNode() {
    @CompilationFinal protected val slot: Int = ptr.offset

    override fun execute(frame: VirtualFrame): Rt_Value {
        val value = initExpr?.execute(frame) ?: return Rt_UnitValue
        frame.setObject(slot, value)
        return Rt_UnitValue
    }

    /**
     * `var x = expr` for a slot the FrameDescriptor reserved as
     * [com.oracle.truffle.api.frame.FrameSlotKind.Long]. Calls the rhs's typed `executeLong`
     * — no intermediate [Rt_IntValue] allocation — and writes through
     * [com.oracle.truffle.api.frame.VirtualFrame.setLong].
     *
     * The rhs falls back to a boxing `execute` + unbox via [asInteger] when its node doesn't
     * override `executeLong`; that path is correct but loses the primitive chain. Most rhs
     * shapes the bench cares about (var read, integer arithmetic, integer constants) override
     * `executeLong` directly.
     */
    internal class IntVarSimple(
        ptr: RR_VarPtr,
        @field:Child private var initExpr: Tf_ExprNode,
    ) : Tf_ExprNode() {
        @CompilationFinal private val slot: Int = ptr.offset

        override fun execute(frame: VirtualFrame): Rt_Value {
            frame.setLong(slot, initExpr.executeLong(frame))
            return Rt_UnitValue
        }
    }

    /** Boolean counterpart to [IntVarSimple]. */
    internal class BoolVarSimple(
        ptr: RR_VarPtr,
        @field:Child private var initExpr: Tf_ExprNode,
    ) : Tf_ExprNode() {
        @CompilationFinal private val slot: Int = ptr.offset

        override fun execute(frame: VirtualFrame): Rt_Value {
            frame.setBoolean(slot, initExpr.executeBoolean(frame))
            return Rt_UnitValue
        }
    }
}

/**
 * Native: variable declaration with optional initialiser (general case).
 *
 * `var x = expr;` → evaluate `expr` (via the Truffle child node) then bind via the
 * interpreter's `initializeDeclarator` helper. The helper handles all three declarator shapes
 * (`Simple`, `Tuple` destructuring, `Wildcard`) so we don't reimplement them here.
 *
 * `initializeDeclarator` writes via `frame.setUnchecked(ptr, ...)` which goes through
 * [net.postchain.rell.base.runtime.Rt_FrameStorage] — on the Truffle path that's a
 * [net.postchain.rell.base.runtime.truffle.Tf_VirtualFrameStorage] backed by this same
 * [VirtualFrame], so the slow-path write lands directly in the indexed slot the hot path
 * reads from. No mirror, no per-call sync.
 */
internal class Tf_VarStmtNode(
    @CompilationFinal private val declarator: RR_VarDeclarator,
    @field:Child private var initExpr: Tf_ExprNode?,
    private val backend: Tf_Backend,
) : Tf_ExprNode() {
    /** Slow-path: `initializeDeclarator` writes via `frame.setUnchecked(ptr, ...)` which
     *  validates `ptr.blockUid == curBlockUid`. */
    override val needsBlockState: Boolean
        get() = true

    override fun execute(frame: VirtualFrame): Rt_Value {
        val value = initExpr?.execute(frame) ?: return Rt_UnitValue
        val rt = tfRtFrame(frame)
        bind(rt, value)
        return Rt_UnitValue
    }

    /**
     * `@TruffleBoundary`: stops PE from inlining the tree-walker's
     * [initializeDeclarator] (which dispatches across `Simple`/`Tuple`/`Wildcard`,
     * recursively for tuple destructuring). Without this boundary, every var-init
     * would drag the entire declarator-handling Java method tree into the compiled
     * graph, blowing past Graal's inlining-depth budget.
     */
    @TruffleBoundary
    private fun bind(frame: Rt_CallFrame, value: Rt_Value) {
        backend.delegate.initializeDeclarator(declarator, frame, value)
    }
}

/**
 * Native: simple `var = expr` assignment to a local variable. Specialised for
 * [RR_Expr.Var] targets with no compound op — the dominant shape on the bench profile
 * (`acc = ...`, `i = ...`).
 *
 * PE folds this to one `frame.setObject(slot, value)` plus the rhs evaluation,
 * no dispatch, no `checkPtr` block-uid validation, no overwrite check.
 */
internal open class Tf_AssignVarStmtNode(
    ptr: RR_VarPtr,
    @field:Child private var valueExpr: Tf_ExprNode,
) : Tf_ExprNode() {
    @CompilationFinal protected val slot: Int = ptr.offset

    override fun execute(frame: VirtualFrame): Rt_Value {
        frame.setObject(slot, valueExpr.execute(frame))
        return Rt_UnitValue
    }

    /**
     * Typed `acc = expr` for a [com.oracle.truffle.api.frame.FrameSlotKind.Long] slot. Mirrors
     * [Tf_VarSimpleStmtNode.IntVarSimple] but for plain reassignment (no compound op).
     */
    internal class IntAssignVar(
        ptr: RR_VarPtr,
        @field:Child private var valueExpr: Tf_ExprNode,
    ) : Tf_ExprNode() {
        @CompilationFinal private val slot: Int = ptr.offset

        override fun execute(frame: VirtualFrame): Rt_Value {
            frame.setLong(slot, valueExpr.executeLong(frame))
            return Rt_UnitValue
        }
    }

    /** Boolean counterpart to [IntAssignVar]. */
    internal class BoolAssignVar(
        ptr: RR_VarPtr,
        @field:Child private var valueExpr: Tf_ExprNode,
    ) : Tf_ExprNode() {
        @CompilationFinal private val slot: Int = ptr.offset

        override fun execute(frame: VirtualFrame): Rt_Value {
            frame.setBoolean(slot, valueExpr.executeBoolean(frame))
            return Rt_UnitValue
        }
    }
}

/**
 * Native: integer compound-assignment to a local — `i += 1`, `acc += collatz_steps(i)`,
 * `i *= ...`, etc. Specialised for [RR_Expr.Var] target + integer-typed binding +
 * an integer-arith op (`R_BinaryOp_Add_Integer` and friends). The op itself is provided
 * as a [Tf_BinaryNode] applied to a synthetic var-read on the lhs and the value rhs;
 * we don't rebuild the op semantics here — the binary node owns those.
 *
 * Why specialise: the generic `Tf_AssignStmtNode` routes through `Rt_InterpreterImpl
 * .assignTo` behind a `@TruffleBoundary`, which loses the typed `executeLong` chain
 * and re-dispatches the op via `evaluateBinaryOp`'s op-key HashMap. Bench's tight
 * `i += 1` was hot in profiling for that reason.
 */
internal open class Tf_AssignVarCompoundIntStmtNode(
    ptr: RR_VarPtr,
    @field:Child private var combined: Tf_ExprNode,
) : Tf_ExprNode() {
    @CompilationFinal protected val slot: Int = ptr.offset

    override fun execute(frame: VirtualFrame): Rt_Value {
        // `combined` is a `Tf_BinaryNode.IntAdd/IntSub/IntMul/IntDiv/IntMod` whose `left`
        // is a `Tf_VarReadNode(ptr)` and `right` is the rhs. Reusing the binary node keeps
        // overflow / div-by-zero semantics identical to the eager path.
        val newLong = combined.executeLong(frame)
        frame.setObject(slot, Rt_IntValue.get(newLong))
        return Rt_UnitValue
    }

    /**
     * Compound integer assignment to a [com.oracle.truffle.api.frame.FrameSlotKind.Long] slot
     * — `i += 1`, `acc *= rhs`, etc. The combined binary node's `executeLong` chain stays
     * primitive end-to-end, and the result writes via [com.oracle.truffle.api.frame.VirtualFrame.setLong]
     * with no [Rt_IntValue] flyweight on the way out.
     */
    internal class TypedSlot(
        ptr: RR_VarPtr,
        @field:Child private var combined: Tf_ExprNode,
    ) : Tf_ExprNode() {
        @CompilationFinal private val slot: Int = ptr.offset

        override fun execute(frame: VirtualFrame): Rt_Value {
            frame.setLong(slot, combined.executeLong(frame))
            return Rt_UnitValue
        }
    }
}

/**
 * Native: assignment statement (catch-all). Routes through the interpreter's `assignTo`
 * helper which correctly handles the various destination expression kinds (struct member,
 * list/map subscript, member access, etc.) and any compound-assign op. Doing the dispatch
 * here in Truffle avoids re-implementing all of those — the interpreter helper is already
 * type-correct.
 *
 * The `dstExpr` is held verbatim as the interpreter helper inspects its variant inside
 * `assignTo`; we don't pre-translate it because the helper expects an [RR_Expr] and the
 * interpreter dispatch is the cheapest correct shape.
 *
 * `assignTo` reads via `frame.get(ptr)` and writes via `frame.setUnchecked(ptr, ...)`; both
 * route through [net.postchain.rell.base.runtime.Rt_FrameStorage], which on the Truffle path
 * is backed by this same [VirtualFrame]. Slow-path writes therefore land directly in the
 * indexed slots the hot path reads — no mirror, no per-call sync.
 */
internal class Tf_AssignStmtNode(
    @CompilationFinal private val dstExpr: RR_Expr,
    @field:Child private var valueExpr: Tf_ExprNode,
    @CompilationFinal private val op: RR_BinaryOp?,
    private val backend: Tf_Backend,
) : Tf_ExprNode() {
    /** Slow-path: `assignTo` reads/writes via `frame.get(ptr)` / `frame.setUnchecked(ptr, ...)`
     *  which validate `ptr.blockUid == curBlockUid`. */
    override val needsBlockState: Boolean
        get() = true

    override fun execute(frame: VirtualFrame): Rt_Value {
        val value = valueExpr.execute(frame)
        val rt = tfRtFrame(frame)
        store(rt, value)
        return Rt_UnitValue
    }

    /**
     * `@TruffleBoundary`: keeps the tree-walker's `assignTo` (which dispatches by
     * `dstExpr` shape — Var, StructMember, Subscript, etc., and applies compound-op
     * semantics) opaque to PE. Without the boundary, PE inlines the full assign
     * dispatch into every loop iteration, exceeding the inliner's recursion budget.
     */
    @TruffleBoundary
    private fun store(frame: Rt_CallFrame, value: Rt_Value) {
        backend.delegate.assignTo(dstExpr, frame, value, op)
    }
}
