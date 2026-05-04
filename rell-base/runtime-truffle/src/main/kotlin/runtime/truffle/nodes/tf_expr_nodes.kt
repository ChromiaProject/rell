/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.GlobalConstantId
import net.postchain.rell.base.model.rr.RR_VarPtr
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked

/**
 * Native expression node: read variable from current frame slot.
 *
 * `RR_Expr.Var` carries the slot pointer; the value lookup is `frame.getObject(slot)` —
 * a direct read of Truffle's own [VirtualFrame] storage. This is the single hottest
 * expression in any non-trivial Rell program (every read of a local is one). Specialising
 * it natively gives Graal a direct slot load that PE can virtualise into compiled-graph
 * SSA values (no array indirection at all once the surrounding code is compiled).
 *
 * **Why the slot index cache**. The translator emits each `RR_VarPtr.offset` at compile
 * time — we hold it as `@CompilationFinal` so PE folds the slot index directly into the
 * surrounding graph. The slot index = `RR_VarPtr.offset`; the FrameDescriptor was wired
 * up in [net.postchain.rell.base.runtime.truffle.Tf_Translator.buildFrameDescriptor] with one `Object`-kind slot per
 * `RR_VarPtr.offset`.
 *
 * **Block tracking elimination.** With direct [VirtualFrame] slot access we no longer
 * need the tree-walker's `curBlockUid` / `curBlockOffset` / `curBlockSize` machinery on
 * the Truffle hot path. Block scopes are translator-time concerns; runtime slot access
 * is a single Truffle slot read. See [Tf_BlockStmtNode_0] for how block enter/exit was
 * conditionally dropped where no descendant needs slow-path block-state.
 *
 * The typed [executeLong] / [executeBoolean] paths skip the [Rt_Value.asInteger] /
 * `asBoolean` indirection. The default `Tf_ExprNode.executeLong` routes through
 * `(this as? Rt_IntValue)?.value ?: typeError(...)` — and the slow `typeError` arm
 * dominated profiling (~6.5K samples) because PE can't fold the cast away without
 * the static type info we already have at translate time. Forcing the cast directly
 * via [Tf_Unchecked.cast] lets PE specialise on the value-class shape.
 */
internal class Tf_VarReadNode(ptr: RR_VarPtr): Tf_ExprNode() {
    @field:CompilationFinal private val slot: Int = ptr.offset

    override fun execute(frame: VirtualFrame): Rt_Value = Tf_Unchecked.cast(frame.getObject(slot))
    override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(frame.getObject(slot)).value
    override fun executeBoolean(frame: VirtualFrame): Boolean = Tf_Unchecked.cast<Rt_BooleanValue>(frame.getObject(slot)).value
}

/**
 * Native expression node: a compile-time-constant value.
 *
 * The translator pre-computes the [Rt_Value] (via `Rt_InterpreterImpl.toRtValue`) and stores it
 * in `@CompilationFinal`, so Graal's partial evaluator inlines the value directly into the
 * caller. After PE this is literally a constant load — no allocation, no map lookup.
 *
 * The translator picks [IntConst] / [BoolConst] / [Generic] by `RR_Expr.ConstantValue.type`,
 * so the typed entry points fold to a primitive load with no `as?` / `typeError` branch in the
 * compiled graph.
 */
internal sealed class Tf_ConstantNode: Tf_ExprNode() {
    internal class Generic(@field:CompilationFinal private val value: Rt_Value): Tf_ConstantNode() {
        override fun execute(frame: VirtualFrame): Rt_Value = value
    }

    /** Integer constant — `executeLong` returns the primitive directly. */
    internal class IntConst(@field:CompilationFinal private val primitive: Long): Tf_ConstantNode() {
        @field:CompilationFinal private val boxed: Rt_IntValue = Rt_IntValue.get(primitive)
        override fun execute(frame: VirtualFrame): Rt_Value = boxed
        override fun executeLong(frame: VirtualFrame): Long = primitive
    }

    /** Boolean constant — `executeBoolean` returns the primitive directly. */
    internal class BoolConst(@field:CompilationFinal private val primitive: Boolean): Tf_ConstantNode() {
        @field:CompilationFinal private val boxed: Rt_BooleanValue = Rt_BooleanValue.get(primitive)
        override fun execute(frame: VirtualFrame): Rt_Value = boxed
        override fun executeBoolean(frame: VirtualFrame): Boolean = primitive
    }
}

/**
 * Native: `if (cond) trueExpr else falseExpr`.
 *
 * Reads the condition through the typed `executeBoolean` path so that comparison/logic
 * operators feeding the test never allocate an intermediate [Rt_BooleanValue]. Result-typed
 * subclasses ([IntIf] / [BoolIf]) chain through both branches' typed paths so the entire
 * ternary stays primitive — used when the result type itself is integer or boolean.
 */
internal sealed class Tf_IfExprNode: Tf_ExprNode() {
    internal class Generic(
        @field:Child private var cond: Tf_ExprNode,
        @field:Child private var trueBranch: Tf_ExprNode,
        @field:Child private var falseBranch: Tf_ExprNode,
    ): Tf_IfExprNode() {
        override fun execute(frame: VirtualFrame): Rt_Value =
            if (cond.executeBoolean(frame)) trueBranch.execute(frame) else falseBranch.execute(frame)
    }

    internal class IntIf(
        @field:Child private var cond: Tf_ExprNode,
        @field:Child private var trueBranch: Tf_ExprNode,
        @field:Child private var falseBranch: Tf_ExprNode,
    ): Tf_IfExprNode() {
        override fun executeLong(frame: VirtualFrame): Long =
            if (cond.executeBoolean(frame)) trueBranch.executeLong(frame) else falseBranch.executeLong(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    internal class BoolIf(
        @field:Child private var cond: Tf_ExprNode,
        @field:Child private var trueBranch: Tf_ExprNode,
        @field:Child private var falseBranch: Tf_ExprNode,
    ): Tf_IfExprNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            if (cond.executeBoolean(frame)) trueBranch.executeBoolean(frame) else falseBranch.executeBoolean(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }
}

/**
 * Native: `left ?: right` — evaluate right only if left is null.
 *
 * Typed subclasses ([IntElvis]/[BoolElvis]) unbox via [Tf_Unchecked.cast] so the typed
 * `executeLong`/`executeBoolean` callers skip the default `asInteger()`/`asBoolean()`
 * `typeError` branch in PE-traced graphs.
 */
internal open class Tf_ElvisNode(
    @field:Child private var left: Tf_ExprNode,
    @field:Child private var right: Tf_ExprNode,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val l = left.execute(frame)
        return if (l != Rt_NullValue) l else right.execute(frame)
    }

    internal class IntElvis(left: Tf_ExprNode, right: Tf_ExprNode) : Tf_ElvisNode(left, right) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    internal class BoolElvis(left: Tf_ExprNode, right: Tf_ExprNode) : Tf_ElvisNode(left, right) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }
}

/**
 * Native: `expr!!` — error if null.
 *
 * Typed subclasses ([IntNotNull]/[BoolNotNull]) skip the default unbox path. See [Tf_ElvisNode].
 */
internal open class Tf_NotNullNode(
    @field:Child private var expr: Tf_ExprNode,
    @field:CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val v = expr.execute(frame)
        if (v === Rt_NullValue) {
            tfRtFrame(frame).error(errPos, "null_value", "Null value")
        }
        return v
    }

    internal class IntNotNull(expr: Tf_ExprNode, errPos: ErrorPos) : Tf_NotNullNode(expr, errPos) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    internal class BoolNotNull(expr: Tf_ExprNode, errPos: ErrorPos) : Tf_NotNullNode(expr, errPos) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }
}

/** Native: tuple literal. */
internal class Tf_TupleLiteralNode(
    @CompilationFinal private val rtType: Rt_ValueClass<*>,
    @field:Children private val children: Array<Tf_ExprNode>,
): Tf_ExprNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_TupleValue(
        rtType,
        buildList(children.size) {
            for (i in children.indices) {
                add(children[i].execute(frame))
            }
        },
    )
}

/** Native: list literal `[a, b, c]`. */
internal class Tf_ListLiteralNode(
    @CompilationFinal private val rtType: Rt_ValueClass<*>,
    @field:Children private val children: Array<Tf_ExprNode>,
): Tf_ExprNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Rt_Value {
        val values = ArrayList<Rt_Value>(children.size)
        for (i in children.indices) {
            values += children[i].execute(frame)
        }
        return Rt_ListValue(rtType, values)
    }
}

/**
 * Native: map literal `[k1: v1, k2: v2]`.
 *
 * Detects duplicate keys with the same error code (`expr_map_dupkey:...`) the tree-walker uses,
 * preserving differential parity.
 */
internal class Tf_MapLiteralNode(
    @CompilationFinal private val rtType: Rt_ValueClass<*>,
    @CompilationFinal private val errPos: ErrorPos,
    @field:Children private val keyNodes: Array<Tf_ExprNode>,
    @field:Children private val valueNodes: Array<Tf_ExprNode>,
): Tf_ExprNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Rt_Value {
        val rt = tfRtFrame(frame)
        val map = LinkedHashMap<Rt_Value, Rt_Value>(keyNodes.size)
        for (i in keyNodes.indices) {
            val k = keyNodes[i].execute(frame)
            val v = valueNodes[i].execute(frame)
            if (k in map) {
                rt.error(
                    errPos,
                    "expr_map_dupkey:${k.strCode()}",
                    "Duplicate map key: ${k.str()}",
                )
            }
            map[k] = v
        }
        return Rt_MapValue(rtType, map)
    }
}

/**
 * Native: statement-as-expression. Executes the wrapped statement node and returns
 * [Rt_UnitValue] (the only valid type for a statement-shaped expression).
 *
 * Propagates the body's `executeStmt` status when this node is itself used in a statement
 * context (e.g. wrapped by a [Tf_ExprStmtNode]) — preserving the prior exception-based
 * behaviour where a `return`/`break`/`continue` inside a statement-expression bubbled up
 * to the enclosing function body.
 */
internal class Tf_StatementExprNode(@field:Child private var body: Tf_ExprNode): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        body.executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame): Int = body.executeStmt(frame)
}

/**
 * Native: read a global constant value (`val FOO = ...`).
 *
 * The constant ID is precomputed at translate time. The value itself is initialised lazily by
 * the app context's constants table — we just look it up.
 *
 * Typed subclasses ([IntConst]/[BoolConst]) skip the default `execute().asInteger()` chain
 * when the constant's declared type is statically integer or boolean.
 */
internal open class Tf_GlobalConstantNode(
    @CompilationFinal private val constId: GlobalConstantId,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = tfRtFrame(frame).appCtx.getGlobalConstant(constId)

    internal class IntConst(constId: GlobalConstantId) : Tf_GlobalConstantNode(constId) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    internal class BoolConst(constId: GlobalConstantId) : Tf_GlobalConstantNode(constId) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }
}

/** Native: read the linked chain's height by chain index. */
internal class Tf_ChainHeightNode(
    @CompilationFinal private val chainIndex: Int,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))

    override fun executeLong(frame: VirtualFrame): Long =
        tfRtFrame(frame).defCtx.sqlCtx.linkedChainByIndex(chainIndex).height
}

/** Native: object value reference (Rell `object` definition, returned by name). */
internal class Tf_ObjectValueNode(@CompilationFinal private val rtType: Rt_ValueClass<*>): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = Rt_ObjectValue(rtType)
}

/**
 * Native: struct attribute access — `s.attr`. Propagates null through safe-navigation chains.
 *
 * The translator picks [IntAttr] / [BoolAttr] when the attribute's type is statically integer
 * or boolean, so the typed `executeLong`/`executeBoolean` path skips the default
 * `execute().asInteger()` / `asBoolean()` chain whose `typeError` branch dominates PE-traced
 * graphs.
 */
internal open class Tf_StructMemberNode(
    @field:Child private var base: Tf_ExprNode,
    @field:CompilationFinal private val attrIndex: Int,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val b = base.execute(frame)
        return if (b === Rt_NullValue) Rt_NullValue else b.asStruct().get(attrIndex)
    }

    /**
     * Integer-typed attribute access. The base must be non-null at the typed call site (the
     * translator only emits this when the attribute's declared type is `integer`, which
     * implies the base struct is non-nullable).
     */
    internal class IntAttr(
        base: Tf_ExprNode,
        attrIndex: Int,
    ) : Tf_StructMemberNode(base, attrIndex) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Boolean-typed attribute access. See [IntAttr]. */
    internal class BoolAttr(
        base: Tf_ExprNode,
        attrIndex: Int,
    ) : Tf_StructMemberNode(base, attrIndex) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }
}

/** Native: list subscript xs&#91;i&#93;. */
internal class Tf_ListSubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var index: Tf_ExprNode,
    @field:CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {

    override fun execute(frame: VirtualFrame): Rt_Value {
        val list = base.execute(frame).asList()
        val idx = index.executeLong(frame)
        Rt_ListValue.checkIndex(tfRtFrame(frame), errPos, list.size, idx)
        return list[idx.toInt()]
    }
}

/** Native: map subscript m&#91;k&#93;. */
internal class Tf_MapSubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var key: Tf_ExprNode,
    @field:CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {

    override fun execute(frame: VirtualFrame): Rt_Value {
        val map = base.execute(frame).asMap()
        val k = key.execute(frame)
        return map[k] ?: tfRtFrame(frame).error(
            errPos,
            "fn_map_get_novalue:${k.strCode()}",
            "Key not in map: ${k.str()}",
        )
    }
}

/** Native: text subscript s&#91;i&#93; returning a length-1 text. */
internal class Tf_TextSubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var index: Tf_ExprNode,
    @field:CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {

    override fun execute(frame: VirtualFrame): Rt_Value {
        val text = base.execute(frame).asString()
        val idx = index.executeLong(frame)
        if (idx < 0 || idx >= text.length) tfRtFrame(frame).error(
            errPos,
            "expr_text_subscript_index:${text.length}:$idx",
            "Index out of bounds: $idx (length ${text.length})",
        )
        return Rt_TextValue.get(text[idx.toInt()].toString())
    }
}

/** Native: byte-array subscript b&#91;i&#93; returning the byte as an integer. */
internal class Tf_ByteArraySubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var index: Tf_ExprNode,
    @CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {

    override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))

    override fun executeLong(frame: VirtualFrame): Long {
        val ba = base.execute(frame).asByteArray()
        val idx = index.executeLong(frame)
        if (idx < 0 || idx >= ba.size) tfRtFrame(frame).error(
            errPos,
            "expr_bytearray_subscript_index:${ba.size}:$idx",
            "Byte array index out of range: $idx (size ${ba.size})",
        )
        return ba[idx.toInt()].toLong() and 0xFF
    }
}

/** Native: virtual-list subscript. Delegates to the stdlib's `VirtualList.get`. */
internal class Tf_VirtualListSubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var index: Tf_ExprNode,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val list = base.execute(frame).asVirtualList()
        val idx = index.executeLong(frame)
        return list.get(idx)
    }
}

/** Native: virtual-map subscript. */
internal class Tf_VirtualMapSubscriptNode(
    @field:Child private var base: Tf_ExprNode,
    @field:Child private var key: Tf_ExprNode,
    @field:CompilationFinal private val errPos: ErrorPos,
): Tf_ExprNode() {

    override fun execute(frame: VirtualFrame): Rt_Value {
        val map = base.execute(frame).asMap()
        val k = key.execute(frame)
        return map[k] ?: tfRtFrame(frame).error(
            errPos,
            "fn_map_get_novalue:${k.strCode()}",
            "Key not in map: ${k.str()}",
        )
    }
}

// JSON subscript nodes intentionally fall back to the interpreter — `JsonUtils.arrayGet` and
// `objectGet` return a Jackson `JsonNode` whose type isn't exposed on the runtime-truffle
// classpath. Specialising them would require pulling Jackson onto our compile path purely for
// one infrequently-used subscript variant; not worth the dep weight. The translator emits
// Tf_FallbackExprNode for `RR_Expr.JsonArraySubscript` / `RR_Expr.JsonObjectSubscript`.

/**
 * Lazy expression — `RR_Expr.Lazy`.
 *
 * The Truffle-side benefit is the *outer* call: by the time `Rt_RR_LazyValue.force()` runs we
 * are already in the slow path, so re-using interpreter dispatch costs nothing extra.
 *
 * # Lazy capture and frame lifetime
 *
 * `Rt_RR_LazyValue` may outlive the call that produced it (e.g. it gets returned, stored in a
 * collection, or passed across a fallback boundary). On the Truffle path the live frame the
 * captured `Rt_CallFrame` points at is a [com.oracle.truffle.api.frame.VirtualFrame] that
 * Graal will free as soon as the call target returns. We therefore snapshot the frame's slot
 * contents into a **fresh heap-backed** [Rt_CallFrame] at capture time and hand the snapshot
 * to the lazy — its `force()` will then evaluate against the snapshot, decoupled from the
 * VirtualFrame's lifetime.
 *
 * The snapshot reuses [Rt_DefinitionContext] / [RR_FrameDescriptor] from the live frame, so
 * stack-trace metadata and block-uid validation stay identical between eager and lazy
 * resolution.
 */
internal class Tf_LazyExprNode(
    @field:CompilationFinal private val rtType: Rt_ValueClass<*>,
    @field:CompilationFinal private val innerExpr: net.postchain.rell.base.model.rr.RR_Expr,
    private val backend: Tf_Backend,
): Tf_ExprNode() {
    override val needsBlockState: Boolean
        get() = true

    override fun execute(frame: VirtualFrame): Rt_Value = capture(frame)

    /**
     * `@TruffleBoundary`: the snapshot copy iterates every slot in the frame and constructs a
     * fresh [Rt_CallFrame]. Inlining this into the compiled graph would drag the whole
     * interpreter constructor chain into PE — and we never need it on the JIT-compiled path
     * (lazy capture is by definition a slow-path event).
     */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private fun capture(frame: VirtualFrame): Rt_Value {
        // `snapshotIfEphemeral` clones the VirtualFrame-backed `Rt_CallFrame` into a fresh
        // heap-backed one so the lazy outlives the current call's VirtualFrame. For the rare
        // outer-entry case where the caller-supplied frame is already heap-backed, the call
        // is a no-op and just returns `live` unchanged.
        val snapshot = tfRtFrame(frame).snapshotIfEphemeral()
        return Rt_RR_LazyValue(rtType, innerExpr, snapshot, backend.delegate)
    }
}
