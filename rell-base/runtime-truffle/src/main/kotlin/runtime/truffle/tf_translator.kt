/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.runtime.truffle.nodes.*
import net.postchain.rell.base.runtime.truffle.values.Tf_LongScaleDecimal
import net.postchain.rell.base.runtime.truffle.values.Tf_StructShapeRegistry
import net.postchain.rell.base.runtime.truffle.values.Tf_TruffleStringText

/**
 * Translates [RR_Statement] / [RR_Expr] trees into Truffle node subtrees, then wraps the
 * result in a Truffle [RootCallTarget].
 *
 * Strategy: hand-write Truffle nodes for the common simple variants — variable read,
 * constants, control flow, literal containers, subscripts. Anything not yet ported (binary
 * arithmetic dispatch, function calls, member accesses, db expressions, …) goes to a
 * [Tf_FallbackExprNode] / [Tf_FallbackStmtNode] that delegates to the wrapped
 * [net.postchain.rell.base.runtime.Rt_InterpreterImpl]. Fallback is correct by construction
 * because the interpreter is the canonical reference; differential testing in CI catches any
 * drift between the two backends.
 *
 * Native-node coverage is purely additive — adding a new specialised node never changes
 * semantics, only steers more cases away from fallback.
 */
internal class Tf_Translator(private val backend: Tf_Backend) {
    private val delegate
        get() = backend.delegate

    /**
     * Static block-nesting depth maintained while translating. Drives [makeBlockStmtNode]'s
     * subclass selection so PE sees a different `execute` method at each level of nesting,
     * dodging Graal's "recursive inlining" bailout.
     */
    private var blockDepth: Int = 0

    /**
     * Per-body slot-kind table being built during the current top-level translation. Lives
     * for the duration of a single `buildBodyTarget` / `buildExprAsBodyTarget` call so the
     * dispatch logic in `translateStmt` / `translateExpr` can consult it via [slotKindAt]
     * without threading the table through every helper signature. `null` outside a body —
     * defensive against accidental reuse from a half-built target.
     */
    private var currentSlotKinds: ByteArray? = null

    /** Lookup of the slot kind at [offset]; returns [TF_SLOT_KIND_OBJECT] when unset. */
    private fun slotKindAt(offset: Int): Byte {
        val table = currentSlotKinds ?: return TF_SLOT_KIND_OBJECT
        return if (offset in table.indices) table[offset] else TF_SLOT_KIND_OBJECT
    }

    /** True if the slot at [offset] is reserved as `FrameSlotKind.Long`. */
    private fun isLongSlot(offset: Int): Boolean = slotKindAt(offset) == TF_SLOT_KIND_LONG

    /** True if the slot at [offset] is reserved as `FrameSlotKind.Boolean`. */
    private fun isBoolSlot(offset: Int): Boolean = slotKindAt(offset) == TF_SLOT_KIND_BOOLEAN

    // -------------------------------------------------------------------------
    // Statement translation
    // -------------------------------------------------------------------------

    fun translateStmt(stmt: RR_Statement): Tf_ExprNode = when (stmt) {
        is RR_Statement.Empty -> Tf_EmptyStmtNode()
        is RR_Statement.Return -> Tf_ReturnStmtNode(stmt.expr?.let { translateExpr(it) })
        is RR_Statement.Break -> Tf_BreakStmtNode()
        is RR_Statement.Continue -> Tf_ContinueStmtNode()
        is RR_Statement.Block -> {
            val depth = blockDepth
            blockDepth = depth + 1
            try {
                makeBlockStmtNode(depth, stmt.frameBlock, Array(stmt.stmts.size) { translateStmt(stmt.stmts[it]) })
            } finally {
                blockDepth = depth
            }
        }

        is RR_Statement.Expr -> Tf_ExprStmtNode(translateExpr(stmt.expr))
        is RR_Statement.ReplExpr -> Tf_ReplExprStmtNode(translateExpr(stmt.expr))
        is RR_Statement.If -> Tf_IfStmtNode(
            translateExpr(stmt.cond),
            translateStmt(stmt.trueStmt),
            translateStmt(stmt.falseStmt),
        )

        is RR_Statement.While -> Tf_WhileStmtNode(
            translateExpr(stmt.cond),
            translateStmt(stmt.body),
            stmt.frameBlock,
        )

        is RR_Statement.Var -> {
            val decl = stmt.declarator
            val initExpr = stmt.expr?.let { translateExpr(it) }
            // Specialise Simple-declarator + (no-op or null) adapter — the dominant
            // `var x = expr` shape. Folds to a single `frame.setObject` after PE.
            // [RR_TypeAdapter.Direct] is also a no-op (same as null); the bench's
            // `var i = 3` shape compiles with `decl.adapter = Direct`, and treating it as
            // null avoids tripping the fallback bridge unnecessarily.
            val noOpAdapter = decl is RR_VarDeclarator.Simple &&
                    (decl.adapter == null || decl.adapter === RR_TypeAdapter.Direct)
            if (noOpAdapter) {
                // Dispatch to typed initialiser when the slot was promoted to Long/Boolean
                // and the initialiser is non-null. Null-init keeps the Object path: a typed
                // simple-init with no rhs would write nothing and leave the slot in `Illegal`,
                // breaking subsequent `frame.getLong` reads.
                when {
                    initExpr != null && isLongSlot(decl.ptr.offset) ->
                        Tf_VarSimpleStmtNode.IntVarSimple(decl.ptr, initExpr)

                    initExpr != null && isBoolSlot(decl.ptr.offset) ->
                        Tf_VarSimpleStmtNode.BoolVarSimple(decl.ptr, initExpr)

                    else -> Tf_VarSimpleStmtNode(decl.ptr, initExpr)
                }
            } else {
                Tf_VarStmtNode(decl, initExpr, backend)
            }
        }

        is RR_Statement.Assign -> {
            val dst = stmt.dstExpr
            val op = stmt.op
            // Specialise local-variable assignments (the dominant shape on the bench profile,
            // ~4K samples in `Tf_AssignStmtNode.store`/`execute` before this).
            if (dst is RR_Expr.Var) {
                if (op == null) {
                    when {
                        isLongSlot(dst.ptr.offset) ->
                            Tf_AssignVarStmtNode.IntAssignVar(dst.ptr, translateExpr(stmt.expr))

                        isBoolSlot(dst.ptr.offset) ->
                            Tf_AssignVarStmtNode.BoolAssignVar(dst.ptr, translateExpr(stmt.expr))

                        else -> Tf_AssignVarStmtNode(dst.ptr, translateExpr(stmt.expr))
                    }
                } else {
                    val intOp = intCompoundOp(op)
                    if (intOp != null && isInt(dst.type) && isInt(stmt.expr.type)) {
                        // Synthesise `oldValue op rhs` using a typed binary node so the loop
                        // body stays on the primitive `executeLong` path.
                        val lhsRead = makeVarReadNode(dst.ptr)
                        val rhsNode = translateExpr(stmt.expr)
                        val combined = intOp(lhsRead, rhsNode, null)
                        if (isLongSlot(dst.ptr.offset)) {
                            Tf_AssignVarCompoundIntStmtNode.TypedSlot(dst.ptr, combined)
                        } else {
                            Tf_AssignVarCompoundIntStmtNode(dst.ptr, combined)
                        }
                    } else {
                        Tf_AssignStmtNode(dst, translateExpr(stmt.expr), op, backend)
                    }
                }
            } else {
                Tf_AssignStmtNode(dst, translateExpr(stmt.expr), op, backend)
            }
        }

        is RR_Statement.When -> Tf_WhenStmtNode(
            translateChooser(stmt.chooser),
            Array(stmt.stmts.size) { translateStmt(stmt.stmts[it]) },
        )

        is RR_Statement.For -> {
            val tupleType = legacyMapTupleType(stmt.iterableAdapter, stmt.varDeclarator)
            Tf_ForStmtNode(
                translateExpr(stmt.expr),
                translateStmt(stmt.body),
                stmt.varDeclarator,
                stmt.iterableAdapter,
                stmt.frameBlock,
                tupleType,
                backend,
            )
        }

        // Statements that still depend on private interpreter helpers (operation-guard
        // semantics, lambda block-scope helpers, db write executors). Routing them through
        // fallback keeps the implementation small without losing correctness.
        is RR_Statement.Guard,
        is RR_Statement.Lambda,
        is RR_Statement.Update,
        is RR_Statement.Delete -> Tf_FallbackStmtNode(backend, stmt)
    }

    // -------------------------------------------------------------------------
    // Expression translation
    // -------------------------------------------------------------------------

    fun translateExpr(expr: RR_Expr): Tf_ExprNode = when (expr) {
        is RR_Expr.Var -> makeVarReadNode(expr.ptr)
        is RR_Expr.ConstantValue -> {
            when (val rtValue = delegate.toRtValue(expr.value, expr.type)) {
                is Rt_IntValue if isInt(expr.type) -> Tf_ConstantNode.IntConst(rtValue.value)
                is Rt_BooleanValue if isBool(expr.type) -> Tf_ConstantNode.BoolConst(rtValue.value)
                // Pre-materialise text constants as Tf_TruffleStringText so downstream
                // text nodes can take their TruffleString fast paths (concat, substring,
                // encoding round-trips) without an intermediate Java-String → TruffleString
                // bounce on the hot path.
                is Rt_TextValue -> Tf_ConstantNode.Generic(Tf_TruffleStringText.fromJavaString(rtValue.value))
                // Pre-materialise decimal constants as Tf_LongScaleDecimal when the value fits
                // a long mantissa. Eliminates per-iteration BigDecimal-backed Rt_BigDecimalValue
                // reads on common literals (`0.5`, `2.5`, `0.21132487`, etc.) — every loop
                // iteration that reads such a constant now touches a primitive-backed value
                // directly, and the long-scale arithmetic fast path engages immediately.
                is Rt_DecimalValue -> Tf_ConstantNode.Generic(Tf_LongScaleDecimal.tryFrom(rtValue.value) ?: rtValue)
                else -> Tf_ConstantNode.Generic(rtValue)
            }
        }

        is RR_Expr.If -> {
            val condNode = translateExpr(expr.cond)
            val trueNode = translateExpr(expr.trueExpr)
            val falseNode = translateExpr(expr.falseExpr)
            when {
                isInt(expr.type) -> Tf_IfExprNode.IntIf(condNode, trueNode, falseNode)
                isBool(expr.type) -> Tf_IfExprNode.BoolIf(condNode, trueNode, falseNode)
                else -> Tf_IfExprNode.Generic(condNode, trueNode, falseNode)
            }
        }

        is RR_Expr.Elvis -> {
            val leftNode = translateExpr(expr.left)
            val rightNode = translateExpr(expr.right)
            when {
                isInt(expr.type) -> Tf_ElvisNode.IntElvis(leftNode, rightNode)
                isBool(expr.type) -> Tf_ElvisNode.BoolElvis(leftNode, rightNode)
                else -> Tf_ElvisNode(leftNode, rightNode)
            }
        }

        is RR_Expr.NotNull -> {
            val innerNode = translateExpr(expr.expr)
            when {
                isInt(expr.type) -> Tf_NotNullNode.IntNotNull(innerNode, expr.errPos)
                isBool(expr.type) -> Tf_NotNullNode.BoolNotNull(innerNode, expr.errPos)
                else -> Tf_NotNullNode(innerNode, expr.errPos)
            }
        }

        is RR_Expr.TupleLiteral -> Tf_TupleLiteralNode(
            delegate.resolveType(expr.type),
            Array(expr.exprs.size) { translateExpr(expr.exprs[it]) },
        )

        is RR_Expr.ListLiteral -> Tf_ListLiteralNode(
            delegate.resolveType(expr.type),
            Array(expr.exprs.size) { translateExpr(expr.exprs[it]) },
        )

        is RR_Expr.MapLiteral -> Tf_MapLiteralNode(
            delegate.resolveType(expr.type),
            expr.errPos,
            Array(expr.keys.size) { translateExpr(expr.keys[it]) },
            Array(expr.values.size) { translateExpr(expr.values[it]) },
        )

        is RR_Expr.StatementExpr -> Tf_StatementExprNode(translateStmt(expr.stmt))

        is RR_Expr.GlobalConstant -> {
            val constId = backend.rrApp.allConstants[expr.constDefIndex].constId
            when {
                isInt(expr.type) -> Tf_GlobalConstantNode.IntConst(constId)
                isBool(expr.type) -> Tf_GlobalConstantNode.BoolConst(constId)
                else -> Tf_GlobalConstantNode(constId)
            }
        }

        is RR_Expr.ChainHeight -> Tf_ChainHeightNode(expr.chainIndex)
        is RR_Expr.ObjectValue -> Tf_ObjectValueNode(delegate.resolveType(expr.type))

        is RR_Expr.StructMember -> {
            val baseNode = translateExpr(expr.base)
            when {
                isInt(expr.type) -> Tf_StructMemberNode.IntAttr(baseNode, expr.attrIndex)
                isBool(expr.type) -> Tf_StructMemberNode.BoolAttr(baseNode, expr.attrIndex)
                else -> Tf_StructMemberNode(baseNode, expr.attrIndex)
            }
        }

        is RR_Expr.ListSubscript -> Tf_ListSubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.index),
            expr.errPos,
        )

        is RR_Expr.MapSubscript -> Tf_MapSubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.key),
            expr.errPos,
        )

        is RR_Expr.TextSubscript -> Tf_TextSubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.index),
            expr.errPos,
        )

        is RR_Expr.ByteArraySubscript -> Tf_ByteArraySubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.index),
            expr.errPos,
        )

        is RR_Expr.VirtualListSubscript -> Tf_VirtualListSubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.index),
        )

        is RR_Expr.VirtualMapSubscript -> Tf_VirtualMapSubscriptNode(
            translateExpr(expr.base),
            translateExpr(expr.key),
            expr.errPos,
        )

        is RR_Expr.Lazy -> Tf_LazyExprNode(delegate.resolveType(expr.type), expr.innerExpr, backend)

        is RR_Expr.Binary -> translateBinary(expr)

        is RR_Expr.Unary -> when (expr.op) {
            "Minus_Integer" -> Tf_UnaryNode.IntMinus(translateExpr(expr.expr), expr.errPos)
            "Not" -> Tf_UnaryNode.Not(translateExpr(expr.expr))
            else -> Tf_UnaryNode.Generic(translateExpr(expr.expr), expr.op, expr.errPos)
        }

        is RR_Expr.TypeAdapter -> {
            val inner = translateExpr(expr.expr)
            // The Direct adapter is a no-op — when the inner type matches the outer (which
            // is the common case for primitive-passthrough), bypass the wrapper entirely so
            // the typed `executeLong`/`executeBoolean` chain stays primitive end-to-end.
            if (expr.adapter === RR_TypeAdapter.Direct) {
                when {
                    isInt(expr.type) && isInt(expr.expr.type) -> Tf_TypeAdapterNode.DirectInt(inner)
                    isBool(expr.type) && isBool(expr.expr.type) -> Tf_TypeAdapterNode.DirectBool(inner)
                    else -> Tf_TypeAdapterNode(inner, expr.adapter)
                }
            } else {
                Tf_TypeAdapterNode(inner, expr.adapter)
            }
        }

        is RR_Expr.Error -> Tf_ErrorNode(expr.message)

        is RR_Expr.When -> {
            val chooserNode = translateChooser(expr.chooser)
            val branchNodes = Array(expr.exprs.size) { translateExpr(expr.exprs[it]) }
            when {
                isInt(expr.type) -> Tf_WhenExprNode.IntWhen(chooserNode, branchNodes)
                isBool(expr.type) -> Tf_WhenExprNode.BoolWhen(chooserNode, branchNodes)
                else -> Tf_WhenExprNode(chooserNode, branchNodes)
            }
        }

        is RR_Expr.StructCreate -> {
            val structDef = backend.rrApp.allStructs[expr.structDefIndex]
            val attrCount = structDef.struct.attributesList.size
            // Tf_StructCreateNode.executeSomNoConstraints relies on attrs covering every
            // attribute exactly once (no fill-with-Rt_NullValue pass). The R_StructExpr.init
            // invariant currently guarantees this, but if a future RR-IR shape ever produced
            // a partial attrs list we would silently leave SOM primitive slots at their zero
            // defaults. Fail compilation up front instead.
            require(expr.attrs.size == attrCount) {
                "StructCreate for '${structDef.struct.name}': expected $attrCount attribute" +
                        " expressions, got ${expr.attrs.size}"
            }
            val attrIndices = IntArray(expr.attrs.size) { i -> expr.attrs[i].attrIndex }
            val attrExprs = Array(expr.attrs.size) { i -> translateExpr(expr.attrs[i].expr) }
            val attrNames = Array(attrCount) { i -> structDef.struct.attributesList[i].name }
            // Per-Rell-struct-type SOM shape, lazily built and cached process-wide. Returns
            // null when SOM isn't available (no polyglot registration); the node falls back to
            // canonical Rt_HeapStruct construction in that case.
            val rrType = expr.type as? RR_Type.Struct
            val shape = rrType?.let { Tf_StructShapeRegistry.shapeFor(backend.rrApp, it) }
            Tf_StructCreateNode(
                expr.structDefIndex,
                delegate.resolveType(expr.type),
                attrIndices,
                attrExprs,
                attrCount,
                attrNames,
                shape,
                backend,
            )
        }

        is RR_Expr.FunctionCall -> when (val call = expr.call) {
            is RR_FunctionCall.Full -> {
                val argSize = call.args.size
                val identity = isIdentityMapping(call.mapping, argSize)
                val mapping = if (identity) {
                    IntArray(argSize) { it }
                } else {
                    IntArray(call.mapping.size) { call.mapping[it] }
                }
                val baseNode = expr.base?.let { translateExpr(it) }
                val argNodes = Array(argSize) { i -> translateExpr(call.args[i]) }
                val resultIsInt = isInt(expr.type)
                val resultIsBool = isBool(expr.type)
                when (val target = call.target) {
                    is RR_FunctionCallTarget.RegularUser ->
                        translateUserFnCall(
                            baseNode, argNodes, mapping, expr.safe, identity, call.callPos,
                            target.fnDefIndex, call, resultIsInt, resultIsBool,
                        )

                    is RR_FunctionCallTarget.AbstractUser ->
                        translateUserFnCall(
                            baseNode, argNodes, mapping, expr.safe, identity, call.callPos,
                            target.fnDefIndex, call, resultIsInt, resultIsBool,
                        )

                    is RR_FunctionCallTarget.SysGlobal ->
                        translateSysGlobalCall(
                            argNodes, mapping, identity, call.callPos,
                            target.fnName, resultIsInt, resultIsBool,
                        ) ?: Tf_FunctionCallNode.makeGeneric(
                            baseNode, argNodes, target, mapping, expr.safe, identity,
                            call.callPos, backend, resultIsInt, resultIsBool,
                        )

                    is RR_FunctionCallTarget.SysMember ->
                        translateSysMemberCall(
                            baseNode, argNodes, mapping, expr.safe, identity, call.callPos,
                            target.fnName, resultIsInt, resultIsBool,
                        ) ?: Tf_FunctionCallNode.makeGeneric(
                            baseNode, argNodes, target, mapping, expr.safe, identity,
                            call.callPos, backend, resultIsInt, resultIsBool,
                        )

                    else -> Tf_FunctionCallNode.makeGeneric(
                        baseNode, argNodes, target, mapping, expr.safe, identity,
                        call.callPos, backend, resultIsInt, resultIsBool,
                    )
                }
            }
            // Partial calls construct an Rt_FunctionCallTarget that closes over the impl;
            // the closure is straightforward but rarely on a hot path, so it stays fallback.
            is RR_FunctionCall.Partial -> Tf_FallbackExprNode(backend, expr)
        }

        is RR_Expr.MemberAccess -> translateMemberAccess(expr)

        // Expressions that still depend on private interpreter helpers — DB-heavy paths
        // (DbAt, ColAt, Update, Delete; SQL dominates Java cost), entity/struct creation
        // (talks to SQL), default-value frame setup, JSON subscript (Jackson dep),
        // assign-as-expression (multi-shape lvalue dispatch).
        is RR_Expr.RegularCreate,
        is RR_Expr.StructEntityCreate,
        is RR_Expr.StructListCreate,
        is RR_Expr.Assign,
        is RR_Expr.ParameterDefaultValue,
        is RR_Expr.AttributeDefaultValue,
        is RR_Expr.DbAt,
        is RR_Expr.ColAt,
        is RR_Expr.JsonArraySubscript,
        is RR_Expr.JsonObjectSubscript -> Tf_FallbackExprNode(backend, expr)
    }

    /**
     * Pick the most-specialised [Tf_BinaryNode] subclass for the given binary expression.
     * Specialisation is by op-key string (`RR_BinaryOp` is a typealias for `String`); anything
     * not matched falls back to [Tf_BinaryNode.Generic], which preserves the dispatch-table
     * behaviour of the tree-walker exactly.
     *
     * The integer-arith and integer-comparison shapes are the ones that benefit from typed
     * `executeLong` / `executeBoolean` paths: chained arithmetic and loop conditions both
     * stop boxing intermediate `Rt_IntValue` / `Rt_BooleanValue` instances once partial
     * evaluation pins these subclasses at each call site.
     */
    private fun translateBinary(expr: RR_Expr.Binary): Tf_ExprNode {
        val left = translateExpr(expr.left)
        val right = translateExpr(expr.right)
        val cmpInfo = expr.cmpInfo
        if (cmpInfo != null) {
            if (cmpInfo.cmpType == "R_CmpType_Integer") {
                val intCmp: Tf_BinaryNode? = when (cmpInfo.cmpOp) {
                    "R_CmpOp_Lt" -> Tf_BinaryNode.IntLt(left, right)
                    "R_CmpOp_Le" -> Tf_BinaryNode.IntLe(left, right)
                    "R_CmpOp_Gt" -> Tf_BinaryNode.IntGt(left, right)
                    "R_CmpOp_Ge" -> Tf_BinaryNode.IntGe(left, right)
                    else -> null
                }
                if (intCmp != null) return intCmp
            }
            if (cmpInfo.cmpType == "R_CmpType_Decimal") {
                // Long-mantissa fast path for decimal compares — sidesteps `evaluateCmpBinaryOp`
                // → `asDecimal().compareTo` and the BigDecimal materialisation that costs the
                // most on Simplex-noise-style hot loops.
                val decCmp: Tf_BinaryNode? = when (cmpInfo.cmpOp) {
                    "R_CmpOp_Lt" -> Tf_BinaryNode.DecimalLt(left, right)
                    "R_CmpOp_Le" -> Tf_BinaryNode.DecimalLe(left, right)
                    "R_CmpOp_Gt" -> Tf_BinaryNode.DecimalGt(left, right)
                    "R_CmpOp_Ge" -> Tf_BinaryNode.DecimalGe(left, right)
                    else -> null
                }
                if (decCmp != null) return decCmp
            }
            // Comparison results are always boolean — pick the typed Generic variant so
            // callers reading via `executeBoolean` skip the default `asBoolean` typeError chain.
            return makeBinaryGeneric(left, right, expr.op, cmpInfo, expr.errPos, expr.type)
        }
        // Integer equality / inequality. The frontend doesn't tag these in `cmpInfo`
        // (it's reserved for `<`, `<=`, `>`, `>=`), so we sniff operand types here. With
        // both operands typed `integer`, Eq/Ne should compile to a single primitive
        // comparison — instead of routing through `evaluateBinaryOp`'s op-key HashMap
        // dispatch + `Rt_IntValue.equals`, which dominated the profile (~30%) before
        // this specialisation.
        val bothInt = isInt(expr.left.type) && isInt(expr.right.type)
        val bothDecimal = isDecimal(expr.left.type) && isDecimal(expr.right.type)
        return when (expr.op) {
            "R_BinaryOp_Add_Integer" -> Tf_BinaryNode.IntAdd(left, right, expr.errPos)
            "R_BinaryOp_Sub_Integer" -> Tf_BinaryNode.IntSub(left, right, expr.errPos)
            "R_BinaryOp_Mul_Integer" -> Tf_BinaryNode.IntMul(left, right, expr.errPos)
            "R_BinaryOp_Div_Integer" -> Tf_BinaryNode.IntDiv(left, right, expr.errPos)
            "R_BinaryOp_Mod_Integer" -> Tf_BinaryNode.IntMod(left, right, expr.errPos)
            "R_BinaryOp_Add_Decimal" -> Tf_BinaryNode.DecimalAdd(left, right, expr.errPos)
            "R_BinaryOp_Sub_Decimal" -> Tf_BinaryNode.DecimalSub(left, right, expr.errPos)
            "R_BinaryOp_Mul_Decimal" -> Tf_BinaryNode.DecimalMul(left, right, expr.errPos)
            "R_BinaryOp_Div_Decimal" -> Tf_BinaryNode.DecimalDiv(left, right, expr.errPos)
            "R_BinaryOp_Mod_Decimal" -> Tf_BinaryNode.DecimalMod(left, right, expr.errPos)
            "R_BinaryOp_Concat_Text" -> Tf_BinaryNode.TextConcat(left, right)
            "R_BinaryOp_Eq" -> when {
                bothInt -> Tf_BinaryNode.IntEq(left, right)
                bothDecimal -> Tf_BinaryNode.DecimalEq(left, right)
                else -> makeBinaryGeneric(left, right, expr.op, null, expr.errPos, expr.type)
            }

            "R_BinaryOp_Ne" -> when {
                bothInt -> Tf_BinaryNode.IntNe(left, right)
                bothDecimal -> Tf_BinaryNode.DecimalNe(left, right)
                else -> makeBinaryGeneric(left, right, expr.op, null, expr.errPos, expr.type)
            }

            "R_BinaryOp_And" -> Tf_BinaryNode.BoolAnd(left, right)
            "R_BinaryOp_Or" -> Tf_BinaryNode.BoolOr(left, right)
            else -> makeBinaryGeneric(left, right, expr.op, null, expr.errPos, expr.type)
        }
    }

    /**
     * Pick a [Tf_BinaryNode.Generic] variant typed by result. The typed variants override
     * `executeLong`/`executeBoolean` so callers reading the result through the typed path
     * skip the default `execute().asInteger()` / `asBoolean()` chain — eliminating the
     * `typeError` dead branch from PE-traced graphs.
     */
    private fun makeBinaryGeneric(
        left: Tf_ExprNode,
        right: Tf_ExprNode,
        op: RR_BinaryOp,
        cmpInfo: RR_CmpBinaryOp?,
        errPos: ErrorPos?,
        resultType: RR_Type,
    ): Tf_ExprNode = when {
        isInt(resultType) -> Tf_BinaryNode.GenericInt(left, right, op, cmpInfo, errPos)
        isBool(resultType) -> Tf_BinaryNode.GenericBool(left, right, op, cmpInfo, errPos)
        else -> Tf_BinaryNode.Generic(left, right, op, cmpInfo, errPos)
    }

    private fun isInt(t: RR_Type): Boolean =
        t is RR_Type.Primitive && t.kind === RR_PrimitiveKind.INTEGER

    private fun isBool(t: RR_Type): Boolean =
        t is RR_Type.Primitive && t.kind === RR_PrimitiveKind.BOOLEAN

    private fun isDecimal(t: RR_Type): Boolean =
        t is RR_Type.Primitive && t.kind === RR_PrimitiveKind.DECIMAL

    private fun makeVarReadNode(ptr: RR_VarPtr): Tf_ExprNode = when {
        isLongSlot(ptr.offset) -> Tf_VarReadNode.IntVar(ptr)
        isBoolSlot(ptr.offset) -> Tf_VarReadNode.BoolVar(ptr)
        else -> Tf_VarReadNode(ptr)
    }

    /**
     * Extract a [RR_Type.Struct] from a base expression type, transparently unwrapping
     * [RR_Type.Nullable]. Returns `null` for any type that isn't ultimately a struct (defensive
     * — the [RR_MemberCalculator.StructAttr] frontend invariant should already ensure this).
     */
    private tailrec fun unwrapStructType(t: RR_Type): RR_Type.Struct? = when (t) {
        is RR_Type.Struct -> t
        is RR_Type.Nullable -> unwrapStructType(t.value)
        else -> null
    }

    /**
     * Look up the SOM [com.oracle.truffle.api.staticobject.StaticProperty] for a given struct
     * attribute. Used by `Tf_MemberAccessNode.StructAttr` translation to capture a translate-time
     * handle that the hot-path reader uses to skip the virtual `asStruct().get(idx)` dispatch
     * when the runtime value is a [net.postchain.rell.base.runtime.truffle.values.Tf_DynStruct].
     */
    private fun somPropertyForStructAttr(
        baseType: RR_Type,
        attrIndex: Int,
    ): com.oracle.truffle.api.staticobject.StaticProperty? {
        val structType = unwrapStructType(baseType) ?: return null
        val shape = Tf_StructShapeRegistry
            .shapeFor(backend.rrApp, structType) ?: return null
        return shape.properties[attrIndex]
    }

    /**
     * For a Rell binary-op key that's also valid as a compound assignment (`+=` / `-=` /
     * `*=` / `/=` / `%=` over integers), return the constructor of the matching typed
     * [Tf_BinaryNode] — used to build the synthetic `oldVal op rhs` expression for
     * [Tf_AssignVarCompoundIntStmtNode]. Returns null for op keys that don't have an
     * integer-typed compound form (e.g. `R_BinaryOp_And`, big-integer arithmetic).
     */
    private fun intCompoundOp(
        op: RR_BinaryOp,
    ): ((Tf_ExprNode, Tf_ExprNode, ErrorPos?) -> Tf_ExprNode)? = when (op) {
        "R_BinaryOp_Add_Integer" -> Tf_BinaryNode::IntAdd
        "R_BinaryOp_Sub_Integer" -> Tf_BinaryNode::IntSub
        "R_BinaryOp_Mul_Integer" -> Tf_BinaryNode::IntMul
        "R_BinaryOp_Div_Integer" -> Tf_BinaryNode::IntDiv
        "R_BinaryOp_Mod_Integer" -> Tf_BinaryNode::IntMod
        else -> null
    }

    /**
     * Build a chooser node for `when` expressions/statements. For [RR_WhenChooser.Lookup] we
     * pre-evaluate all constant keys to [Rt_Value] at translate time, so the runtime path is
     * a flat array scan with no constant-conversion work per iteration.
     */
    private fun translateChooser(chooser: RR_WhenChooser): Tf_WhenChooserNode = when (chooser) {
        is RR_WhenChooser.Iterative -> Tf_WhenChooserNode.Iterative(
            translateExpr(chooser.keyExpr),
            Array(chooser.conditions.size) { translateExpr(chooser.conditions[it].expr) },
            IntArray(chooser.conditions.size) { chooser.conditions[it].index },
            chooser.elseIndex,
        )

        is RR_WhenChooser.Lookup -> Tf_WhenChooserNode.Lookup(
            translateExpr(chooser.keyExpr),
            Array(chooser.keys.size) { delegate.toRtValue(chooser.keys[it]) },
            IntArray(chooser.values.size) { chooser.values[it] },
            chooser.elseIndex,
        )
    }

    private fun legacyMapTupleType(
        adapter: RR_IterableAdapterKind,
        decl: RR_VarDeclarator,
    ): Rt_ValueClass<*> {
        if (adapter != RR_IterableAdapterKind.LEGACY_MAP) {
            // Unused for DIRECT; pick a cheap placeholder so we don't add a separate
            // null path through the Tf node.
            return delegate.resolveType(RR_Type.Primitive(RR_PrimitiveKind.UNIT))
        }
        val rrType = when (decl) {
            is RR_VarDeclarator.Simple -> decl.type
            else -> RR_Type.Primitive(RR_PrimitiveKind.UNIT)
        }
        return delegate.resolveType(rrType)
    }

    /**
     * Returns true if [mapping] is the trivial 0..n-1 mapping (or empty, which the tree-walker
     * also treats as identity). We pre-compute this at translate time so the runtime node
     * can skip building the permuted argument list in the common case.
     */
    private fun isIdentityMapping(mapping: List<Int>, argCount: Int): Boolean {
        when {
            mapping.isEmpty() -> return true
            mapping.size != argCount -> return false
            else -> {
                for (i in mapping.indices) {
                    if (mapping[i] != i) return false
                }
                return true
            }
        }
    }

    /**
     * Build a [Tf_FunctionCallNode.UserFn] / [Tf_FunctionCallNode.UserFnInt] /
     * [Tf_FunctionCallNode.UserFnBool] for a user-function call site, pushing as much
     * validation work to translate time as possible.
     *
     * Fast-path eligibility (`fastPath = true` on the resulting node) requires:
     * 1. Every argument's static type structurally matches the corresponding parameter's
     *    declared type — checked via `RR_Type` equality below. Equality is conservative:
     *    nullable-widening, type-adapter shapes, and other isAssignableRR-only-but-not-equal
     *    cases fall back to slow path. The tree-walker's compiler emits an explicit
     *    `RR_Expr.TypeAdapter` for those reshapes, so this rarely matters in practice.
     * 2. No parameter carries an `RR_SizeConstraint` — those are checked by `validateParams`
     *    and we keep that path on the slow track since constraints are runtime-checked.
     *
     * When eligible, we pre-extract the callee's frame descriptor, definition id, and the
     * per-param frame slot offsets (paramOffsets&#91;i&#93; = paramVars&#91;i&#93;.ptr.offset). The
     * runtime fast path uses these directly — no `cachedFnBase` walk, no `validateParams`,
     * no `setParams` — and skips `@TruffleBoundary` so PE folds the caller's argument
     * values straight into the callee's body for monomorphic recursive calls
     * (`is_prime(i)`, `collatz_steps(n)`, `fib(n - 1)` in the bench).
     */
    private fun translateUserFnCall(
        baseNode: Tf_ExprNode?,
        argNodes: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identity: Boolean,
        callPos: FilePos,
        fnDefIndex: Int,
        call: RR_FunctionCall.Full,
        resultIsInt: Boolean,
        resultIsBool: Boolean,
    ): Tf_ExprNode {
        val fn = backend.rrApp.allFunctions[fnDefIndex]
        val fnBase = fn.fnBase
        val params = fnBase.params
        val paramVars = fnBase.paramVars
        val argTypes = call.args
        val mappingList = call.mapping

        // Fast-path eligibility — see kdoc above. Requires: arg-count matches param count,
        // every (mapped) arg type structurally equals param type, and no param has a size
        // constraint. We additionally check `paramVars.size == params.size` defensively;
        // the compiler invariant holds for user functions but the assert keeps the
        // translator honest if a malformed RR ever lands here.
        var fastPath = paramVars.size == params.size && argTypes.size == paramVars.size
        if (fastPath) {
            for (param in params) {
                if (param.sizeConstraint != null) {
                    fastPath = false
                    break
                }
            }
        }
        if (fastPath) {
            // The runtime maps arg i to param i via `mapping[i]` (i.e. evaluated[mapping[i]]
            // is the value for param i). We need the static type of `args[mapping[i]]` to
            // equal `paramVars[i].type`.
            val mappingForCheck: IntArray = if (identity) {
                IntArray(argTypes.size) { it }
            } else {
                IntArray(mappingList.size) { mappingList[it] }
            }
            for (i in paramVars.indices) {
                val argIdx = mappingForCheck[i]
                if (argTypes[argIdx].type != paramVars[i].type) {
                    fastPath = false
                    break
                }
            }
        }

        val frameDescriptor = fnBase.frame
        val defId = fn.base.defId
        val paramOffsets = IntArray(paramVars.size) { paramVars[it].ptr.offset }

        return when {
            resultIsInt -> Tf_FunctionCallNode.UserFnInt(
                baseNode, argNodes, mapping, safe, identity, callPos,
                fnDefIndex, fastPath, frameDescriptor, defId, paramOffsets, backend,
            )

            resultIsBool -> Tf_FunctionCallNode.UserFnBool(
                baseNode, argNodes, mapping, safe, identity, callPos,
                fnDefIndex, fastPath, frameDescriptor, defId, paramOffsets, backend,
            )

            else -> Tf_FunctionCallNode.UserFn(
                baseNode, argNodes, mapping, safe, identity, callPos,
                fnDefIndex, fastPath, frameDescriptor, defId, paramOffsets, backend,
            )
        }
    }

    /**
     * Translate a `RR_FunctionCallTarget.SysGlobal` call site to a [Tf_SysCallNode.SysGlobal]
     * (or its typed-result variant). Returns null when the sys-fn key is not registered in the
     * compilation-local stdlib — the caller falls back to the generic-call shape, preserving
     * the slow-path semantics of [Tf_FunctionCallNode.Generic] without crashing the translation.
     *
     * Resolution happens once at translate time; the resulting [Tf_SysCallNode] holds the
     * `R_SysFunction` reference and the pre-stripped display name as `@CompilationFinal`
     * fields, so PE folds them straight into the call-site graph.
     */
    private fun translateSysGlobalCall(
        argNodes: Array<Tf_ExprNode>,
        mapping: IntArray,
        identity: Boolean,
        callPos: FilePos,
        fnName: String,
        resultIsInt: Boolean,
        resultIsBool: Boolean,
    ): Tf_ExprNode? {
        val fn = backend.stdlib.sysFunctions[fnName] ?: return null
        val displayName = resolveSysFnDisplayName(fnName)
        return when {
            resultIsInt -> Tf_SysCallNode.SysGlobalInt(argNodes, fn, displayName, callPos, mapping, identity)
            resultIsBool -> Tf_SysCallNode.SysGlobalBool(argNodes, fn, displayName, callPos, mapping, identity)
            else -> Tf_SysCallNode.SysGlobal(argNodes, fn, displayName, callPos, mapping, identity)
        }
    }

    /**
     * Translate a `RR_FunctionCallTarget.SysMember` call site to a [Tf_SysCallNode.SysMember]
     * (or its typed-result variant). Returns null when:
     *
     * - The sys-fn key is not registered in the compilation-local stdlib (defensive — the
     *   compiler invariant says every emitted SysMember key has a corresponding registration,
     *   but a malformed `RR_App` or a serialization round-trip skew would trip this).
     * - The call has no base — SysMember semantics require one, so a missing base means the
     *   compiler emitted the wrong target shape. The fallback through `Generic` keeps the old
     *   "best-effort" behaviour rather than crashing.
     *
     * Resolution happens once at translate time, same as [translateSysGlobalCall].
     */
    private fun translateSysMemberCall(
        baseNode: Tf_ExprNode?,
        argNodes: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identity: Boolean,
        callPos: FilePos,
        fnName: String,
        resultIsInt: Boolean,
        resultIsBool: Boolean,
    ): Tf_ExprNode? {
        if (baseNode == null) return null
        val fn = backend.stdlib.sysFunctions[fnName] ?: return null
        val displayName = resolveSysFnDisplayName(fnName)
        return when {
            resultIsInt -> Tf_SysCallNode.SysMemberInt(
                baseNode, argNodes, fn, displayName, callPos, mapping, identity, safe,
            )

            resultIsBool -> Tf_SysCallNode.SysMemberBool(
                baseNode, argNodes, fn, displayName, callPos, mapping, identity, safe,
            )

            else -> Tf_SysCallNode.SysMember(
                baseNode, argNodes, fn, displayName, callPos, mapping, identity, safe,
            )
        }
    }

    /**
     * Translate `RR_Expr.MemberAccess` to a [Tf_MemberAccessNode] subclass when the calculator
     * shape is supported, falling back to [Tf_FallbackExprNode] otherwise.
     *
     * Supported shapes (cover the FT4 hot path: `enum.name`, `gtv.to_bytes()`, struct/tuple
     * field reads):
     * - [RR_MemberCalculator.StructAttr], [RR_MemberCalculator.TupleAttr],
     *   [RR_MemberCalculator.VirtualTupleAttr], [RR_MemberCalculator.VirtualStructAttr]
     *   — direct accessor reads, no SQL or lambda block.
     * - [RR_MemberCalculator.SysFunction] — sys-property dispatch (e.g. `enum.name`,
     *   `module.name`). Resolved at translate time to an `R_SysFunction` reference.
     * - [RR_MemberCalculator.FunctionCall] with `RR_FunctionCall.Full` and a sys-member
     *   target — `gtv.to_bytes()`, `text.size()`, etc. Resolved exactly like
     *   [translateSysMemberCall].
     *
     * Calculators that depend on SQL (`DataAttribute`), lambda-block setup
     * (`DataAttributeExpr`), independent expression eval (`ExprEval`), partial calls
     * (`FunctionCall.Partial`), or non-sys targets keep routing through fallback. They are
     * either rare on the hot path or already heavyweight enough that the fallback is not the
     * dominant cost.
     */
    private fun translateMemberAccess(expr: RR_Expr.MemberAccess): Tf_ExprNode {
        val baseNode = translateExpr(expr.base)
        val safe = expr.safe
        val resultIsInt = isInt(expr.type)
        val resultIsBool = isBool(expr.type)
        return when (val calc = expr.calculator) {
            is RR_MemberCalculator.StructAttr -> {
                // Capture the SOM StaticProperty handle for the static base struct type, so the
                // hot-path reader can do a direct SOM slot load when the runtime value is a
                // Tf_DynStruct. Falls through to the virtual `asStruct().get(idx)` path for
                // tree-walk-allocated Rt_HeapStruct values.
                val somProperty = somPropertyForStructAttr(expr.base.type, calc.attrIndex)
                when {
                    resultIsInt -> Tf_MemberAccessNode.StructAttr.IntAttr(baseNode, calc.attrIndex, safe, somProperty)
                    resultIsBool -> Tf_MemberAccessNode.StructAttr.BoolAttr(baseNode, calc.attrIndex, safe, somProperty)
                    else -> Tf_MemberAccessNode.StructAttr(baseNode, calc.attrIndex, safe, somProperty)
                }
            }

            is RR_MemberCalculator.TupleAttr -> when {
                resultIsInt -> Tf_MemberAccessNode.TupleAttr.IntAttr(baseNode, calc.attrIndex, safe)
                resultIsBool -> Tf_MemberAccessNode.TupleAttr.BoolAttr(baseNode, calc.attrIndex, safe)
                else -> Tf_MemberAccessNode.TupleAttr(baseNode, calc.attrIndex, safe)
            }

            is RR_MemberCalculator.VirtualTupleAttr ->
                Tf_MemberAccessNode.VirtualTupleAttr(baseNode, calc.fieldIndex, safe)

            is RR_MemberCalculator.VirtualStructAttr ->
                Tf_MemberAccessNode.VirtualStructAttr(baseNode, calc.attrDefIndex, safe)

            is RR_MemberCalculator.SysFunction -> {
                // Translate-time resolution against the compilation-local stdlib. If the key is
                // not registered (defensive: malformed RR_App) fall back to the tree-walker which
                // emits the same "Member function not found" error.
                val fn = backend.stdlib.sysFunctions[calc.fnName]
                if (fn == null) {
                    Tf_FallbackExprNode(backend, expr)
                } else {
                    val displayName = resolveSysFnDisplayName(calc.fnName)
                    when {
                        resultIsInt -> Tf_MemberAccessNode.SysFn.IntResult(baseNode, fn, displayName, safe)
                        resultIsBool -> Tf_MemberAccessNode.SysFn.BoolResult(baseNode, fn, displayName, safe)
                        else -> Tf_MemberAccessNode.SysFn(baseNode, fn, displayName, safe)
                    }
                }
            }

            is RR_MemberCalculator.FunctionCall -> {
                val call = calc.call
                if (call !is RR_FunctionCall.Full) {
                    // Partial calls construct a function value — mirror the regular FunctionCall
                    // translator and keep them on fallback.
                    return Tf_FallbackExprNode(backend, expr)
                }
                val target = call.target
                if (target !is RR_FunctionCallTarget.SysMember) {
                    // Non-sys member function-call targets (operations, native, function-value, …)
                    // are rare on the hot path and benefit less from native dispatch. Stay on
                    // fallback to preserve the tree-walker's call-target semantics exactly.
                    return Tf_FallbackExprNode(backend, expr)
                }
                val fn = backend.stdlib.sysFunctions[target.fnName] ?: return Tf_FallbackExprNode(backend, expr)
                val argSize = call.args.size
                val identity = isIdentityMapping(call.mapping, argSize)
                val mapping = if (identity) {
                    IntArray(argSize) { it }
                } else {
                    IntArray(call.mapping.size) { call.mapping[it] }
                }
                val argNodes = Array(argSize) { i -> translateExpr(call.args[i]) }
                val displayName = resolveSysFnDisplayName(target.fnName)
                when {
                    resultIsInt -> Tf_MemberAccessNode.SysMemberFnCall.IntResult(
                        baseNode, argNodes, fn, displayName, call.callPos, mapping, identity, safe,
                    )

                    resultIsBool -> Tf_MemberAccessNode.SysMemberFnCall.BoolResult(
                        baseNode, argNodes, fn, displayName, call.callPos, mapping, identity, safe,
                    )

                    else -> Tf_MemberAccessNode.SysMemberFnCall(
                        baseNode, argNodes, fn, displayName, call.callPos, mapping, identity, safe,
                    )
                }
            }

            // SQL-bound, lambda-bound, and independent-expression calculators stay on fallback.
            is RR_MemberCalculator.DataAttribute,
            is RR_MemberCalculator.DataAttributeExpr,
            is RR_MemberCalculator.ExprEval -> Tf_FallbackExprNode(backend, expr)
        }
    }

    // -------------------------------------------------------------------------
    // Top-level CallTarget builders
    // -------------------------------------------------------------------------

    /**
     * Wraps a top-level body in a Truffle [RootCallTarget]. The CallTarget is what the JIT
     * specialises and compiles; building one per [net.postchain.rell.base.model.rr.RR_FunctionDefinition]
     * lets Graal's profiles persist across calls.
     *
     * The Truffle [FrameDescriptor] mirrors the Rell function's [RR_FrameDescriptor]: one
     * slot per `RR_VarPtr.offset`, all `Object`-kind for now. This lays the groundwork for
     * the leaf-migration wave that will switch hot per-iteration locals to typed
     * `frame.getLong/getBoolean` slots so PE can virtualise them — but in the foundation
     * step, runtime storage still goes through the legacy [net.postchain.rell.base.runtime.Rt_CallFrame]
     * via the bridge.
     *
     * [paramOffsets] is supplied for **user-function** bodies — the inner-entry path
     * ([Tf_FunctionRootNode]) needs them to write `arguments[2 + i]` into each param's frame
     * slot at function entry. For operation/query/guard/constant bodies (only outer entry,
     * never reached via `DirectCallNode`), pass `null` and a plain [Tf_RootNode] is used.
     */
    fun buildBodyTarget(
        body: RR_Statement,
        rrFrame: RR_FrameDescriptor,
        defId: DefinitionId,
        paramOffsets: IntArray? = null,
        paramTypes: Array<RR_Type>? = null,
    ): RootCallTarget {
        // Slot-kind classification has to happen before translating the body — the dispatch
        // helpers (`makeVarReadNode`, the Var/Assign builders) consult `currentSlotKinds` while
        // walking the statement tree.
        val slotKinds = collectSlotKinds(body, rrFrame.size, paramOffsets, paramTypes)
        val priorSlotKinds = currentSlotKinds
        currentSlotKinds = slotKinds
        try {
            val tfBody = translateStmt(body)
            val descriptor = buildFrameDescriptor(rrFrame, defId, slotKinds)
            val paramSlotKinds = paramOffsets?.let { offsets ->
                ByteArray(offsets.size) { slotKinds[offsets[it]] }
            }
            val rootNode = if (paramOffsets != null) {
                Tf_FunctionRootNode(
                    language = null,
                    name = describe(body),
                    body = tfBody,
                    frameDescriptor = descriptor,
                    paramOffsets = paramOffsets,
                    paramSlotKinds = paramSlotKinds!!,
                )
            } else {
                Tf_RootNode(language = null, name = describe(body), body = tfBody, frameDescriptor = descriptor)
            }
            return rootNode.callTarget
        } finally {
            currentSlotKinds = priorSlotKinds
        }
    }

    /**
     * Wraps a single [RR_Expr] (constant initialisers) in a body that emits the value as a
     * [STATUS_RETURN] with the value placed in [TF_RETURN_VALUE_AUX_SLOT]. Keeps the
     * Tf_*RootNode protocol uniform: every body either signals `return` with a value, or
     * completes void.
     */
    fun buildExprAsBodyTarget(expr: RR_Expr, rrFrame: RR_FrameDescriptor, defId: DefinitionId): RootCallTarget {
        // Constant initialisers don't declare locals — the body is a single expression — so
        // the slot-kind table is uniformly Object. We still build the descriptor with the
        // table so [Tf_VirtualFrameStorage] sees a stable `slotKinds` length.
        val slotKinds = ByteArray(rrFrame.size)
        val priorSlotKinds = currentSlotKinds
        currentSlotKinds = slotKinds
        try {
            val exprNode = translateExpr(expr)
            val wrap = ExprAsReturnStmtNode(exprNode)
            val descriptor = buildFrameDescriptor(rrFrame, defId, slotKinds)
            val rootNode = Tf_ExprBodyRootNode(
                language = null,
                name = describeExpr(expr),
                body = wrap,
                frameDescriptor = descriptor,
            )
            return rootNode.callTarget
        } finally {
            currentSlotKinds = priorSlotKinds
        }
    }

    private fun collectSlotKinds(
        body: RR_Statement,
        size: Int,
        paramOffsets: IntArray?,
        paramTypes: Array<RR_Type>?,
    ): ByteArray {
        val kinds = ByteArray(size) // initialised to TF_SLOT_KIND_OBJECT (= 0)
        if (paramOffsets != null && paramTypes != null) {
            for (i in paramOffsets.indices) {
                kinds.assignSlotKind(paramOffsets[i], TF_SLOT_KIND_OBJECT)
            }
        }
        collectFromStmt(body, kinds)
        return kinds
    }

    /** Recursive descent over [RR_Statement] populating [kinds] from each declarator. */
    private fun collectFromStmt(stmt: RR_Statement, kinds: ByteArray) {
        when (stmt) {
            is RR_Statement.Var -> collectFromDeclarator(stmt.declarator, kinds)
            is RR_Statement.Block -> for (s in stmt.stmts) collectFromStmt(s, kinds)
            is RR_Statement.If -> {
                collectFromStmt(stmt.trueStmt, kinds)
                collectFromStmt(stmt.falseStmt, kinds)
            }

            is RR_Statement.While -> collectFromStmt(stmt.body, kinds)
            is RR_Statement.For -> {
                collectFromDeclarator(stmt.varDeclarator, kinds)
                collectFromStmt(stmt.body, kinds)
            }

            is RR_Statement.When -> for (s in stmt.stmts) collectFromStmt(s, kinds)
            is RR_Statement.Guard -> collectFromStmt(stmt.body, kinds)
            is RR_Statement.Lambda -> collectFromStmt(stmt.body, kinds)
            // No nested var-decls in the remaining shapes — return/expr/break/continue/etc.
            else -> {}
        }
    }

    /** Recursive descent over [RR_VarDeclarator] populating [kinds] with each Simple slot's kind. */
    private fun collectFromDeclarator(decl: RR_VarDeclarator, kinds: ByteArray) {
        when (decl) {
            is RR_VarDeclarator.Simple -> kinds.assignSlotKind(decl.ptr.offset, TF_SLOT_KIND_OBJECT)
            is RR_VarDeclarator.Tuple -> for (sub in decl.subDeclarators) collectFromDeclarator(sub, kinds)
            RR_VarDeclarator.Wildcard -> {}
        }
    }

    private fun ByteArray.assignSlotKind(offset: Int, kind: Byte) {
        if (offset !in indices) return
        val existing = this[offset]
        this[offset] = when (existing) {
            TF_SLOT_KIND_OBJECT -> kind
            kind -> kind
            else -> TF_SLOT_KIND_OBJECT
        }
    }

    /**
     * Build a Truffle [FrameDescriptor] mirroring [rrFrame].
     *
     * One indexed slot per `RR_VarPtr.offset` — that's `rrFrame.size` slots total. All slots
     * start as [FrameSlotKind.Object] in the foundation: the leaf-migration wave will narrow
     * them to typed kinds (`Long`, `Boolean`) for hot loop locals so Truffle can virtualise
     * the storage and PE can fold reads/writes to primitives.
     *
     * Wave 3 additions:
     * - The descriptor's `info` carries a [Tf_FrameInfo] with `defId` + `rrFrame` so the lazy
     *   [net.postchain.rell.base.runtime.Rt_CallFrame] alloc on inner entry can build a fresh
     *   [net.postchain.rell.base.runtime.Rt_DefinitionContext] without holding a back-pointer
     *   to the root node.
     * - One auxiliary slot ([TF_RT_FRAME_AUX_SLOT] == 0) is reserved on every descriptor; it
     *   caches the resolved [net.postchain.rell.base.runtime.Rt_CallFrame] view so subsequent
     *   [net.postchain.rell.base.runtime.truffle.nodes.tfRtFrame] calls return the same
     *   instance. The aux slot is independent of the indexed-slot range — adding it does not
     *   shift any `RR_VarPtr.offset` ↔ slot-index mapping.
     */
    private fun buildFrameDescriptor(
        rrFrame: RR_FrameDescriptor,
        defId: DefinitionId,
        slotKinds: ByteArray,
    ): FrameDescriptor {
        val builder = FrameDescriptor.newBuilder(rrFrame.size)
        builder.info(Tf_FrameInfo(defId, rrFrame, slotKinds))
        // Add slots one at a time so each slot's reserved kind reflects [slotKinds]. Truffle's
        // batch `addSlots(size, kind)` would force all slots to the same kind. The build-time
        // loop is amortised — descriptor build runs once per definition.
        for (i in 0..<rrFrame.size) {
            val kind = when (slotKinds[i]) {
                TF_SLOT_KIND_LONG -> FrameSlotKind.Long
                TF_SLOT_KIND_BOOLEAN -> FrameSlotKind.Boolean
                else -> FrameSlotKind.Object
            }
            builder.addSlot(kind, null, null)
        }
        val descriptor = builder.build()
        // Reserve aux slot 0 for the Rt_CallFrame lazy cache, slot 1 for the body's
        // return-value channel. findOrAddAuxiliarySlot is neverPartOfCompilation
        // (build-time only) — fine to call here.
        val rtFrameAuxIndex = descriptor.findOrAddAuxiliarySlot(TF_RT_FRAME_AUX_KEY)
        check(rtFrameAuxIndex == TF_RT_FRAME_AUX_SLOT) {
            "expected Rt_CallFrame aux slot at index $TF_RT_FRAME_AUX_SLOT, got $rtFrameAuxIndex"
        }
        val returnAuxIndex = descriptor.findOrAddAuxiliarySlot(TF_RETURN_VALUE_AUX_KEY)
        check(returnAuxIndex == TF_RETURN_VALUE_AUX_SLOT) {
            "expected return-value aux slot at index $TF_RETURN_VALUE_AUX_SLOT, got $returnAuxIndex"
        }
        return descriptor
    }

    private class ExprAsReturnStmtNode(
        @field:Child private var inner: Tf_ExprNode,
    ): Tf_ExprNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            executeStmt(frame)
            return Rt_UnitValue
        }

        override fun executeStmt(frame: VirtualFrame): Int {
            frame.setAuxiliarySlot(TF_RETURN_VALUE_AUX_SLOT, inner.execute(frame))
            return STATUS_RETURN
        }
    }

    private fun describe(stmt: RR_Statement): String = "rell-body:" + stmt::class.simpleName.orEmpty()
    private fun describeExpr(expr: RR_Expr): String = "rell-expr:" + expr::class.simpleName.orEmpty()
}
