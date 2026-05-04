/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.LoopNode
import com.oracle.truffle.api.profiles.BranchProfile
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_IterableAdapterKind
import net.postchain.rell.base.model.rr.RR_VarDeclarator
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_TupleValue
import net.postchain.rell.base.runtime.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.runtime.asIterable
import net.postchain.rell.base.runtime.asMap
import net.postchain.rell.base.runtime.initializeDeclarator
import net.postchain.rell.base.runtime.truffle.STATUS_BREAK
import net.postchain.rell.base.runtime.truffle.STATUS_CONTINUE
import net.postchain.rell.base.runtime.truffle.STATUS_FALLTHROUGH
import net.postchain.rell.base.runtime.truffle.STATUS_RETURN
import net.postchain.rell.base.runtime.truffle.TF_RETURN_VALUE_AUX_SLOT
import net.postchain.rell.base.runtime.truffle.Tf_Backend

/** Native: `;` no-op. */
internal class Tf_EmptyStmtNode : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_UnitValue
}

/**
 * Native: `return [expr]`. Writes the optional return value to [TF_RETURN_VALUE_AUX_SLOT]
 * and returns [STATUS_RETURN]; loop and block nodes propagate the status up to the function-
 * body root, which reads the slot.
 */
internal class Tf_ReturnStmtNode(
    @field:Child private var expr: Tf_ExprNode?,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int {
        val value = expr?.execute(frame)
        frame.setAuxiliarySlot(TF_RETURN_VALUE_AUX_SLOT, value)
        return STATUS_RETURN
    }
}

/** Native: `break`. */
internal class Tf_BreakStmtNode : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_UnitValue
    override fun executeStmt(frame: VirtualFrame): Int = STATUS_BREAK
}

/** Native: `continue`. */
internal class Tf_ContinueStmtNode : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_UnitValue
    override fun executeStmt(frame: VirtualFrame): Int = STATUS_CONTINUE
}

/**
 * Native: braced block with its own frame block-scope. Mirrors `RR_Statement.Block` —
 * runs children inside `frame.block(frameBlock) { ... }` so locals introduced inside the
 * block are zeroed on exit, matching the tree-walker's scoping discipline.
 *
 * **Why this is a tower of identical-shape classes**: Graal's partial evaluator throws
 * `PermanentBailoutException("Too deep inlining, probably caused by recursive inlining")`
 * whenever the same Java method appears twice on its inline-decoder stack. Real Rell code
 * routinely nests blocks (`while { if { ... } }`) — every brace pair is an
 * [net.postchain.rell.base.model.rr.RR_Statement.Block] — so a single `Tf_BlockStmtNode.execute` would recur on PE's
 * stack and the compile would fail every time.
 *
 * Solution: provide N distinct Java classes ([Tf_BlockStmtNode_0] … [Tf_BlockStmtNode_5])
 * with byte-for-byte identical bodies. The translator picks one based on the block's
 * static nesting depth, modulo N. PE sees a different method at each level of nesting up
 * to N levels deep, which covers any realistic Rell function. (Going past N levels still
 * trips the bailout for that one inner shape, but real code should never get that deep —
 * if it does, the fallback is the tree-walker, which is correct if not fast.)
 *
 * The cycle modulus N must be at least one greater than the deepest possible "block-shaped
 * stack" — currently 6 is comfortable headroom for Rell's typical control-flow nesting.
 *
 * Use [makeBlockStmtNode] to construct rather than picking the subclass manually.
 */
internal sealed class Tf_BlockStmtNode : Tf_ExprNode() {
    /**
     * Block identity captured as primitives so PE folds the values directly into compiled
     * code. Going via `RR_FrameBlock.uid/offset/size` (data-class getters behind a
     * `@CompilationFinal` field) was the path that prevented PE from unrolling the
     * slot-clearing loop in the previous `exitBlockUnchecked` — a dominant bench hotspot.
     */
    @JvmField @CompilationFinal protected var blockUid: Long = 0
    @JvmField @CompilationFinal protected var blockOffset: Int = 0
    @JvmField @CompilationFinal protected var blockSize: Int = 0

    @JvmField
    @field:Children
    protected var children: Array<Tf_ExprNode> = emptyArray()

    /**
     * Translator-time flag: `true` if any child (or descendant) takes a slow path that
     * needs `Rt_CallFrame.curBlockUid` / etc. to be correct. When `false`, [execute]
     * skips the entire `enterBlockSet` / `clearSlotsRange` bookkeeping — PE folds the
     * constant branch and the body becomes a pure children loop.
     *
     * Captured as `@CompilationFinal` (not a method call) so PE constant-folds the
     * `if (slowPathNeeded) { ... }` test at compile time.
     */
    @JvmField @CompilationFinal protected var slowPathNeeded: Boolean = false

    /**
     * Cold-branch profile for non-fallthrough child results. Tripped only when a child
     * `executeStmt` returned `return`/`break`/`continue` — the exit propagation path. PE
     * uses the never-tripped state to fold the early-exit branch out of the JIT graph
     * entirely until the workload actually exercises non-local control flow.
     */
    @JvmField protected val nonFallthroughProfile: BranchProfile = BranchProfile.create()

    /** Initialize the shared payload. Done outside the constructor so all subclasses share one path. */
    fun init(frameBlock: RR_FrameBlock, children: Array<Tf_ExprNode>) {
        this.blockUid = frameBlock.uid
        this.blockOffset = frameBlock.offset
        this.blockSize = frameBlock.size
        this.children = children
        this.slowPathNeeded = anyNeedsBlockState(children)
    }

    /** Children carry the block-state demand; surface it for parent containers/loops. */
    final override val needsBlockState: Boolean
        get() = slowPathNeeded
}

internal class Tf_BlockStmtNode_0 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
                // Clear the VF mirror too. Otherwise the next block entry's slow-path
                // [Tf_VarStmtNode] would [pushToLegacy] the stale VF value back into
                // [Rt_CallFrame.values], defeating [clearSlotsRange] above and tripping
                // `setUnchecked(overwrite = false)`.
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

internal class Tf_BlockStmtNode_1 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

internal class Tf_BlockStmtNode_2 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

internal class Tf_BlockStmtNode_3 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

internal class Tf_BlockStmtNode_4 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

internal class Tf_BlockStmtNode_5 : Tf_BlockStmtNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    @ExplodeLoop
    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            var status = STATUS_FALLTHROUGH
            try {
                for (i in children.indices) {
                    val s = children[i].executeStmt(frame)
                    if (s != STATUS_FALLTHROUGH) {
                        nonFallthroughProfile.enter()
                        status = s
                        break
                    }
                }
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
            }
            return status
        } else {
            for (i in children.indices) {
                val s = children[i].executeStmt(frame)
                if (s != STATUS_FALLTHROUGH) {
                    nonFallthroughProfile.enter()
                    return s
                }
            }
            return STATUS_FALLTHROUGH
        }
    }
}

/** Number of cycle slots in [Tf_BlockStmtNode]'s class tower. */
internal const val TF_BLOCK_CYCLE: Int = 6

/** Construct a block node for the given nesting depth (0 = outermost). */
internal fun makeBlockStmtNode(
    depth: Int,
    frameBlock: RR_FrameBlock,
    children: Array<Tf_ExprNode>,
): Tf_BlockStmtNode {
    val node: Tf_BlockStmtNode = when (depth % TF_BLOCK_CYCLE) {
        0 -> Tf_BlockStmtNode_0()
        1 -> Tf_BlockStmtNode_1()
        2 -> Tf_BlockStmtNode_2()
        3 -> Tf_BlockStmtNode_3()
        4 -> Tf_BlockStmtNode_4()
        5 -> Tf_BlockStmtNode_5()
        else -> error("unreachable")
    }
    node.init(frameBlock, children)
    return node
}

/**
 * Native: expression-statement: evaluate expression, discard the result.
 *
 * Forwards the status of the wrapped expression's [Tf_ExprNode.executeStmt] so a
 * `Tf_StatementExprNode` body's `return`/`break`/`continue` propagates up. For pure
 * expression children (the common case), the default `executeStmt` evaluates `execute`
 * and returns [STATUS_FALLTHROUGH] — same as before.
 */
internal class Tf_ExprStmtNode(
    @field:Child private var expr: Tf_ExprNode,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        expr.execute(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int = expr.executeStmt(frame)
}

/** Native: REPL expression-statement: evaluate, then print to repl output. */
internal class Tf_ReplExprStmtNode(
    @field:Child private var expr: Tf_ExprNode,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val value = expr.execute(frame)
        tfRtFrame(frame).appCtx.replOut?.printValue(value)
        return Rt_UnitValue
    }
}

/**
 * Native: `if (cond) trueStmt else falseStmt`. Reads the condition through the typed
 * `executeBoolean` path so feeding comparisons skip the [net.postchain.rell.base.runtime.Rt_BooleanValue] box.
 *
 * Propagates `return`/`break`/`continue` from whichever branch was taken — the if itself
 * never consumes them.
 */
internal class Tf_IfStmtNode(
    @field:Child private var cond: Tf_ExprNode,
    @field:Child private var trueBranch: Tf_ExprNode,
    @field:Child private var falseBranch: Tf_ExprNode,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int =
        if (cond.executeBoolean(frame)) trueBranch.executeStmt(frame) else falseBranch.executeStmt(frame)
}

/**
 * Native: `while (cond) body`.
 *
 * Plain Kotlin loop — the surrounding [net.postchain.rell.base.runtime.truffle.Tf_RootNode] is
 * already a JIT-compilable Truffle entry point, so partial evaluation will turn this into a
 * straight-line loop in compiled code. We don't wrap in [LoopNode] (which targets OSR) because
 * the body always runs within an already-compiled CallTarget. `break`/`continue` are consumed
 * locally by inspecting the body's [Tf_ExprNode.executeStmt] status code — no exception
 * handlers, no JVM unwind. `return` propagates up to the enclosing function-body root.
 */
internal class Tf_WhileStmtNode(
    @field:Child private var cond: Tf_ExprNode,
    @field:Child private var body: Tf_ExprNode,
    frameBlock: RR_FrameBlock,
) : Tf_ExprNode() {
    /**
     * Block identity captured as primitives. See [Tf_BlockStmtNode] for why this matters
     * for PE — going via `RR_FrameBlock.{uid,offset,size}` getters didn't constant-fold
     * cleanly through a `@CompilationFinal` data-class field, leaving the slot-clearing
     * loop un-unrolled.
     */
    @CompilationFinal private val blockUid: Long = frameBlock.uid
    @CompilationFinal private val blockOffset: Int = frameBlock.offset
    @CompilationFinal private val blockSize: Int = frameBlock.size

    /**
     * Translator-time flag — `true` if [cond] or [body] (or any descendant) takes a
     * slow path that depends on `Rt_CallFrame.curBlockUid`. When `false`, the
     * per-iteration `enterBlockSet`/`clearSlotsRange` bookkeeping (the 1875-sample
     * bench hotspot) folds out under PE — the loop becomes pure typed-slot reads
     * and writes against [VirtualFrame].
     */
    @CompilationFinal private val slowPathNeeded: Boolean = cond.needsBlockState || body.needsBlockState

    /** Cold-branch profile for the `return` propagation path. */
    private val returnProfile: BranchProfile = BranchProfile.create()

    override val needsBlockState: Boolean
        get() = slowPathNeeded

    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int {
        if (slowPathNeeded) {
            // Per-iteration enter/restore is REQUIRED here. While the Tf_VarRead/Write fast
            // path ignores curBlockUid, fallback boundary calls (`backend.delegate.assignTo`,
            // `evaluateExpr`, etc.) route through tree-walker `Rt_CallFrame.get(ptr)` which
            // calls `checkPtr(ptr.blockUid, ...)` and rejects mismatches. Hoisting
            // enterBlockSet outside the loop trips that check on the next iteration's
            // `cond.executeBoolean(frame)` if cond reads a parent-scope var via a fallback
            // path.
            val rt = tfRtFrame(frame)
            val savedUid = rt.curBlockUid
            val savedOffset = rt.curBlockOffset
            val savedSize = rt.curBlockSize
            while (true) {
                if (!cond.executeBoolean(frame)) break
                rt.enterBlockSet(blockUid, blockOffset, blockSize)
                val s: Int
                try {
                    s = body.executeStmt(frame)
                } finally {
                    rt.enterBlockSet(savedUid, savedOffset, savedSize)
                    rt.clearSlotsRange(blockOffset, blockSize)
                    // Clear the VF mirror so the next iteration's slow-path
                    // bind sees null in [Rt_CallFrame.values]; otherwise
                    // [pushToLegacy] could rewrite the stale value before bind.
                }
                if (s == STATUS_FALLTHROUGH || s == STATUS_CONTINUE) continue
                if (s == STATUS_BREAK) break
                // STATUS_RETURN — propagate up to the function-body root.
                returnProfile.enter()
                return s
            }
        } else {
            while (true) {
                if (!cond.executeBoolean(frame)) break
                val s = body.executeStmt(frame)
                if (s == STATUS_FALLTHROUGH || s == STATUS_CONTINUE) continue
                if (s == STATUS_BREAK) break
                returnProfile.enter()
                return s
            }
        }
        return STATUS_FALLTHROUGH
    }
}

/**
 * Native: `for` loop. Two iterable adaptations exist: DIRECT (any `Rt_Value` that exposes
 * `asIterable()` — list, set, range, virtual-list, etc.) and LEGACY_MAP (treats a map as a
 * sequence of `(key, value)` tuples to support the older syntax).
 *
 * `LEGACY_MAP` materialises the entire iteration as a list of [Rt_TupleValue]s up front, the
 * same way the tree-walker does — preserving exact iteration order and parity. The tuple's
 * runtime type is precomputed at translate time when the declarator names a tuple type;
 * otherwise we fall back to the unit-typed placeholder the tree-walker uses.
 */
internal class Tf_ForStmtNode(
    @field:Child private var iterableExpr: Tf_ExprNode,
    @field:Child private var body: Tf_ExprNode,
    @field:CompilationFinal private val declarator: RR_VarDeclarator,
    @field:CompilationFinal private val iterableAdapter: RR_IterableAdapterKind,
    frameBlock: RR_FrameBlock,
    @field:CompilationFinal private val legacyMapTupleType: Rt_ValueClass<*>,
    private val backend: Tf_Backend,
) : Tf_ExprNode() {
    @CompilationFinal private val blockUid: Long = frameBlock.uid
    @CompilationFinal private val blockOffset: Int = frameBlock.offset
    @CompilationFinal private val blockSize: Int = frameBlock.size

    /**
     * `for` loops always need slow-path block-state: each iteration's loop-variable bind
     * runs through `initializeDeclarator` which writes to [Rt_CallFrame.values] under
     * `frame.setUnchecked(ptr, value, false)` — that requires `curBlockUid` to match the
     * declarator's block. So we keep the per-iteration enter/exit unconditionally and
     * bubble `needsBlockState = true` to any enclosing block.
     */
    override val needsBlockState: Boolean
        get() = true

    /** Cold-branch profile for the `return` propagation path. */
    private val returnProfile: BranchProfile = BranchProfile.create()

    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int {
        val iterable = iterableExpr.execute(frame)
        val iterator: Iterable<Rt_Value> = when (iterableAdapter) {
            RR_IterableAdapterKind.DIRECT -> iterable.asIterable()
            RR_IterableAdapterKind.LEGACY_MAP -> iterable.asMap().entries.map { (k, v) ->
                Rt_TupleValue(legacyMapTupleType, listOf(k, v))
            }
        }

        // Per-iteration enter/restore — see [Tf_WhileStmtNode.executeStmt] comment.
        val rt = tfRtFrame(frame)
        val savedUid = rt.curBlockUid
        val savedOffset = rt.curBlockOffset
        val savedSize = rt.curBlockSize
        for (element in iterator) {
            rt.enterBlockSet(blockUid, blockOffset, blockSize)
            val s: Int
            try {
                // Mirror current [VirtualFrame] state for slots OUTSIDE this for-block's
                // range into [Rt_CallFrame.values]. This makes the legacy mirror reflect
                // any hot-path writes from previous iterations (e.g. loop-accumulator
                // vars) so the subsequent [pullFromLegacy] doesn't clobber them with
                // stale slow-path-init values. We exclude the for-block's own range
                // because [bindLoopVar] requires those slots to be null
                // (`overwrite = false` enforced by `frame.setUnchecked`).
                bindLoopVar(rt, element)
                // `bindLoopVar` writes to `Rt_CallFrame.values`; mirror those writes
                // into [VirtualFrame] so the body's hot-path `Tf_VarReadNode` sees the
                // loop variable.
                s = body.executeStmt(frame)
            } finally {
                rt.enterBlockSet(savedUid, savedOffset, savedSize)
                rt.clearSlotsRange(blockOffset, blockSize)
                // Also clear the for-block's slots in [VirtualFrame] so the next
                // iteration's [pushToLegacyExcept] doesn't see a stale value where
                // [bindLoopVar] would re-init.
            }
            if (s == STATUS_FALLTHROUGH || s == STATUS_CONTINUE) continue
            if (s == STATUS_BREAK) break
            // STATUS_RETURN — propagate up.
            returnProfile.enter()
            return s
        }
        return STATUS_FALLTHROUGH
    }

    @TruffleBoundary
    private fun bindLoopVar(frame: Rt_CallFrame, element: Rt_Value) {
        backend.delegate.initializeDeclarator(declarator, frame, element)
    }
}

/**
 * Native: `when` statement. The shared chooser yields a branch index (or -1 when no branch
 * matched and there is no else); statement-shaped whens silently skip a -1, matching the
 * tree-walker's `executeWhenStmt`.
 *
 * Propagates `return`/`break`/`continue` from the matched branch up to the enclosing block
 * or loop.
 */
internal class Tf_WhenStmtNode(
    @field:Child private var chooser: Tf_WhenChooserNode,
    @field:Children private val branches: Array<Tf_ExprNode>,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int {
        val idx = chooser.chooseIndex(frame)
        return if (idx >= 0) branches[idx].executeStmt(frame) else STATUS_FALLTHROUGH
    }
}
