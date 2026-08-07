/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.model.rr.RR_Expr
import net.postchain.rell.base.model.rr.RR_Statement
import net.postchain.rell.base.model.rr.RR_Type

/**
 * JVM mirror of the native `ListTypeTable` walk (see `rell_runtime.h` §"ListTypeTable" and
 * `lower_expr.cpp::lowerListLiteralExpr`).
 *
 * The native lowerer interns each `ListLiteralExpr` it lowers, claiming a dense [Int] id in a simple
 * PRE-ORDER position — at the `ListLiteral` node, BEFORE its element exprs. To rebuild the dense
 * `Array<RR_Type>` of list-literal types that `Llvm_SysBridge.listType` indexes, this walker visits
 * the function body in the IDENTICAL pre-order — recursing into children in the same field order the
 * lowering files use — and collects each `RR_Expr.ListLiteral.type` the first time it is reached.
 *
 * WALK-ORDER PARITY (consensus-critical): the two id spaces must agree. Both walks descend the SAME
 * RR tree (the native side over the serialized App, this side over the in-memory `RR_App`). For a
 * function the native lowerer compiles SUCCESSFULLY, every `ListLiteral` it reaches is interned in
 * structural pre-order; this walker reproduces that order by a structural pre-order DFS that visits
 * each node's sub-exprs/sub-stmts in the same order the lowering recursion does. (If the native side
 * SOFT-FAILS, the function falls back to the interpreter and these ids are never consumed, so any
 * order divergence inside a soft-failed subtree is harmless.)
 *
 * The count this produces MUST equal the native `outListTypeCount` ([RellLlvmNative
 * .compileFunctionExtended]); [Llvm_Backend] asserts that equality before trusting the array.
 */
object Llvm_ListTypeWalker {

    /** Collect every `RR_Expr.ListLiteral.type` in `body`, in native-lowering pre-order. */
    fun collect(body: RR_Statement): List<RR_Type> {
        val out = ArrayList<RR_Type>()
        walkStmt(body, out)
        return out
    }

    private fun walkStmt(stmt: RR_Statement, out: MutableList<RR_Type>) {
        when (stmt) {
            is RR_Statement.Empty -> {}
            is RR_Statement.Var -> stmt.expr?.let { walkExpr(it, out) }
            is RR_Statement.Return -> stmt.expr?.let { walkExpr(it, out) }
            is RR_Statement.Block -> stmt.stmts.forEach { walkStmt(it, out) }
            is RR_Statement.Expr -> walkExpr(stmt.expr, out)
            is RR_Statement.ReplExpr -> walkExpr(stmt.expr, out)
            is RR_Statement.Assign -> {
                // lowerAssign (lower_stmt.cpp): dst is a VarExpr-to-slot (no ListLiteral child), then
                // the RHS is lowered. Mirror: dst then src. (dstExpr carries no list literal in the
                // inline-lowerable case, but walk it for structural completeness.)
                walkExpr(stmt.dstExpr, out); walkExpr(stmt.expr, out)
            }
            is RR_Statement.If -> { walkExpr(stmt.cond, out); walkStmt(stmt.trueStmt, out); walkStmt(stmt.falseStmt, out) }
            is RR_Statement.When -> { walkChooser(stmt.chooser, out); stmt.stmts.forEach { walkStmt(it, out) } }
            is RR_Statement.While -> { walkExpr(stmt.cond, out); walkStmt(stmt.body, out) }
            is RR_Statement.For -> { walkExpr(stmt.expr, out); walkStmt(stmt.body, out) }
            is RR_Statement.Break -> {}
            is RR_Statement.Continue -> {}
            is RR_Statement.Guard -> walkStmt(stmt.body, out)
            // Constructs the native lowerer always soft-fails (Lambda/Update/Delete) — descend anyway
            // for completeness; their ids are never consumed because the whole function soft-fails.
            is RR_Statement.Lambda -> { stmt.argExprs.forEach { walkExpr(it, out) }; walkStmt(stmt.body, out) }
            is RR_Statement.Update -> {}
            is RR_Statement.Delete -> {}
        }
    }

    private fun walkChooser(chooser: net.postchain.rell.base.model.rr.RR_WhenChooser, out: MutableList<RR_Type>) {
        when (chooser) {
            is net.postchain.rell.base.model.rr.RR_WhenChooser.Iterative -> {
                walkExpr(chooser.keyExpr, out)
                chooser.conditions.forEach { walkExpr(it.expr, out) }
            }
            is net.postchain.rell.base.model.rr.RR_WhenChooser.Lookup -> walkExpr(chooser.keyExpr, out)
        }
    }

    private fun walkExpr(expr: RR_Expr, out: MutableList<RR_Type>) {
        when (expr) {
            // PRE-ORDER: record THIS list literal's type BEFORE recursing into its elements (mirroring
            // the native lowerListLiteralExpr, which interns before lowering element exprs).
            is RR_Expr.ListLiteral -> {
                out.add(expr.type)
                expr.exprs.forEach { walkExpr(it, out) }
            }
            is RR_Expr.ListSubscript -> { walkExpr(expr.base, out); walkExpr(expr.index, out) }
            is RR_Expr.TupleLiteral -> expr.exprs.forEach { walkExpr(it, out) }
            is RR_Expr.StructCreate -> expr.attrs.forEach { walkExpr(it.expr, out) }
            is RR_Expr.Binary -> { walkExpr(expr.left, out); walkExpr(expr.right, out) }
            is RR_Expr.Unary -> walkExpr(expr.expr, out)
            is RR_Expr.If -> { walkExpr(expr.cond, out); walkExpr(expr.trueExpr, out); walkExpr(expr.falseExpr, out) }
            is RR_Expr.When -> { walkChooser(expr.chooser, out); expr.exprs.forEach { walkExpr(it, out) } }
            is RR_Expr.Elvis -> { walkExpr(expr.left, out); walkExpr(expr.right, out) }
            is RR_Expr.NotNull -> walkExpr(expr.expr, out)
            is RR_Expr.FunctionCall -> {
                // lowerFunctionCall (lower_call.cpp) evaluates call.args in source order. A FunctionValue
                // base (expr.base) is only present for a value-call (FnTarget_FunctionValue), which the
                // native side soft-fails — so its ids are never consumed; still walk args for parity.
                expr.call.args.forEach { walkExpr(it, out) }
            }
            is RR_Expr.MemberAccess -> {
                // lowerMember lowers the receiver (base) FIRST, then the calculator. For a member that
                // is itself a call (a.f(args)), the calculator's call args are lowered after the base.
                walkExpr(expr.base, out)
                val calc = expr.calculator
                if (calc is net.postchain.rell.base.model.rr.RR_MemberCalculator.FunctionCall) {
                    calc.call.args.forEach { walkExpr(it, out) }
                }
            }
            is RR_Expr.StatementExpr -> walkStmt(expr.stmt, out)
            is RR_Expr.TypeAdapter -> walkExpr(expr.expr, out)
            // Leaf / interpreter-only exprs carry no native-lowered ListLiteral child (or the whole
            // function soft-fails before their ids could matter). No recursion needed.
            else -> {}
        }
    }
}
