/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.stmt

import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VarPtr
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.ImmList

internal sealed class R_StatementResult
internal class R_StatementResult_Return(val value: Rt_Value?): R_StatementResult()
internal data object R_StatementResult_Break: R_StatementResult()
internal data object R_StatementResult_Continue: R_StatementResult()

internal abstract class R_Statement {
    internal abstract fun execute(frame: Rt_CallFrame): R_StatementResult?
}

internal object R_EmptyStatement: R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        return null
    }
}

internal sealed class R_VarDeclarator {
    abstract fun initialize(frame: Rt_CallFrame, value: Rt_Value, overwrite: Boolean)
}

internal class R_SimpleVarDeclarator(
    private val ptr: R_VarPtr,
    private val type: R_Type,
    private val adapter: R_TypeAdapter,
): R_VarDeclarator() {
    override fun initialize(frame: Rt_CallFrame, value: Rt_Value, overwrite: Boolean) {
        val value2 = adapter.adaptValue(value)
        frame.set(ptr, type, value2, overwrite)
    }
}

internal class R_TupleVarDeclarator(
    private val subDeclarators: ImmList<R_VarDeclarator>,
): R_VarDeclarator() {
    override fun initialize(frame: Rt_CallFrame, value: Rt_Value, overwrite: Boolean) {
        val tuple = value.asTuple()
        for ((i, declarator) in subDeclarators.withIndex()) {
            val subValue = tuple[i]
            declarator.initialize(frame, subValue, overwrite)
        }
    }
}

internal data object R_WildcardVarDeclarator: R_VarDeclarator() {
    override fun initialize(frame: Rt_CallFrame, value: Rt_Value, overwrite: Boolean) {
        // Do nothing.
    }
}

internal class R_VarStatement(
    private val declarator: R_VarDeclarator,
    private val expr: R_Expr?,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        if (expr != null) {
            val value = expr.evaluate(frame)
            declarator.initialize(frame, value, false)
        }
        return null
    }
}

internal class R_ReturnStatement(private val expr: R_Expr?): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult {
        val value = expr?.evaluate(frame)
        return R_StatementResult_Return(value)
    }
}

internal class R_BlockStatement(
    private val stmts: ImmList<R_Statement>,
    private val frameBlock: R_FrameBlock,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val res = frame.block(frameBlock) {
            executeStatements(frame, stmts)
        }
        return res
    }

    companion object {
        fun executeStatements(frame: Rt_CallFrame, stmts: List<R_Statement>): R_StatementResult? {
            for (stmt in stmts) {
                val res = stmt.execute(frame)
                if (res != null) {
                    return res
                }
            }
            return null
        }

    }
}

internal class R_ExprStatement(private val expr: R_Expr): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        expr.evaluate(frame)
        return null
    }
}

internal class R_ReplExprStatement(private val expr: R_Expr): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val res = expr.evaluate(frame)
        frame.defCtx.appCtx.replOut?.printValue(res)
        return null
    }
}

internal class R_AssignStatement(
    private val dstExpr: R_DestinationExpr,
    private val expr: R_Expr,
    private val op: R_BinaryOp?,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val dstRef = dstExpr.evaluateRef(frame)
        dstRef ?: return null // Null-safe access (operator ?.).

        val value = if (op != null) {
            val left = dstRef.get()
            val right = expr.evaluate(frame)
            op.evaluate(left, right)
        } else {
            expr.evaluate(frame)
        }

        dstRef.set(value)
        return null
    }
}

internal class R_IfStatement(
    private val expr: R_Expr,
    private val trueStmt: R_Statement,
    private val falseStmt: R_Statement,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val cond = expr.evaluate(frame)
        val b = cond.asBoolean()
        val stmt = if (b) trueStmt else falseStmt
        val res = stmt.execute(frame)
        return res
    }
}

internal class R_WhenStatement(
    private val chooser: R_WhenChooser,
    private val stmts: ImmList<R_Statement>,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val choice = chooser.choose(frame)
        val res = if (choice == null) null else stmts[choice].execute(frame)
        return res
    }
}

internal class R_WhileStatement(
    private val expr: R_Expr,
    private val stmt: R_Statement,
    private val frameBlock: R_FrameBlock,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        while (true) {
            val cond = expr.evaluate(frame)
            val b = cond.asBoolean()
            if (!b) {
                break
            }

            val res = executeBody(frame)

            if (res is R_StatementResult_Return) {
                return res
            } else if (res == R_StatementResult_Break) {
                break
            } else if (res == R_StatementResult_Continue) {
                continue
            }
        }
        return null
    }

    private fun executeBody(frame: Rt_CallFrame): R_StatementResult? {
        return frame.block(frameBlock) {
            stmt.execute(frame)
        }
    }
}

sealed class R_IterableAdapter {
    abstract fun iterable(v: Rt_Value): Iterable<Rt_Value>
}

object R_IterableAdapter_Direct: R_IterableAdapter() {
    override fun iterable(v: Rt_Value) = v.asIterable()
}

object R_IterableAdapter_LegacyMap: R_IterableAdapter() {
    override fun iterable(v: Rt_Value): Iterable<Rt_Value> {
        val map = v.asMapValue()
        return map.asIterable(true)
    }
}

internal class R_ForStatement(
    private val varDeclarator: R_VarDeclarator,
    private val expr: R_Expr,
    private val iterator: R_IterableAdapter,
    private val stmt: R_Statement,
    private val frameBlock: R_FrameBlock,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val value = expr.evaluate(frame)
        val list = iterator.iterable(value)

        val res = frame.block(frameBlock) {
            execute0(frame, list)
        }

        return res
    }

    private fun execute0(frame: Rt_CallFrame, list: Iterable<Rt_Value>): R_StatementResult? {
        var first = true

        for (item in list) {
            varDeclarator.initialize(frame, item, !first)
            first = false

            val res = stmt.execute(frame)

            if (res is R_StatementResult_Return) {
                return res
            } else if (res == R_StatementResult_Break) {
                break
            } else if (res == R_StatementResult_Continue) {
                continue
            }
        }

        return null
    }
}

internal class R_BreakStatement: R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult {
        return R_StatementResult_Break
    }
}

internal class R_ContinueStatement: R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult {
        return R_StatementResult_Continue
    }
}

internal class R_GuardStatement(private val subStmt: R_Statement): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val res = subStmt.execute(frame)
        frame.guardCompleted()
        return res
    }
}

internal class R_LambdaStatement(
    private val args: ImmList<Pair<R_Expr, R_VarPtr>>,
    private val block: R_FrameBlock,
    private val stmt: R_Statement,
): R_Statement() {
    override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        val values = args.map { it to it.first.evaluate(frame) }

        val res = frame.block(block) {
            for ((arg, value) in values) {
                frame.set(arg.second, arg.first.type, value, false)
            }
            stmt.execute(frame)
        }

        return res
    }
}
