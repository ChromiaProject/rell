/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.*

internal fun Rt_InterpreterImpl.executeWhenStmt(stmt: RR_Statement.When, frame: Rt_CallFrame): Rt_StatementResult? {
    val idx = evaluateWhenChooser(stmt.chooser, frame)
    return if (idx >= 0) executeStmt(stmt.stmts[idx], frame) else null
}

internal fun Rt_InterpreterImpl.executeStatements(stmts: List<RR_Statement>, frame: Rt_CallFrame): Rt_StatementResult? {
    for (stmt in stmts) {
        val res = executeStmt(stmt, frame)
        if (res != null) return res
    }

    return null
}

internal fun Rt_InterpreterImpl.initializeDeclarator(
    decl: RR_VarDeclarator,
    frame: Rt_CallFrame,
    value: Rt_Value,
): Unit = when (decl) {
    is RR_VarDeclarator.Simple -> {
        val adapter = decl.adapter
        val adapted = if (adapter != null) applyTypeAdapter(adapter, value) else value
        frame.setUnchecked(decl.ptr, adapted, false)
    }

    is RR_VarDeclarator.Tuple -> {
        val tuple = value.asTuple()
        for ((i, sub) in decl.subDeclarators.withIndex()) {
            initializeDeclarator(sub, frame, tuple[i])
        }
    }

    is RR_VarDeclarator.Wildcard -> {
        /* discard */
    }
}

internal fun Rt_InterpreterImpl.assignTo(
    dstExpr: RR_Expr,
    frame: Rt_CallFrame,
    value: Rt_Value,
    op: RR_BinaryOp?,
) {
    when (dstExpr) {
        is RR_Expr.Var -> {
            val finalValue = if (op != null) {
                val old = frame.get(dstExpr.ptr)
                evaluateBinaryOp(op, old, value)
            } else value
            frame.setUnchecked(dstExpr.ptr, finalValue, true)
        }

        is RR_Expr.StructMember -> {
            val baseValue = evaluateExpr(dstExpr.base, frame)
            // Safe navigation: if base is null, skip the assignment (matches R_ model's ?. behavior)
            if (baseValue == Rt_NullValue) return
            val struct = baseValue.asStruct()
            val finalValue = if (op != null) {
                val old = struct.get(dstExpr.attrIndex)
                evaluateBinaryOp(op, old, value)
            } else value
            // Apply size-constraint validation from the RR struct definition (for the pure-RR path).
            val rrType = struct.type.rrType
            if (rrType is RR_Type.Struct) {
                val structDef = rrApp.allStructs[rrType.defIndex]
                val rrAttr = structDef.struct.attributesList[dstExpr.attrIndex]
                rrAttr.sizeConstraint?.let { checkSizeConstraint(it, finalValue) }
            }
            @Suppress("ExplicitCollectionElementAccessMethod")
            struct.set(dstExpr.attrIndex, finalValue)
        }

        is RR_Expr.ListSubscript -> {
            val list = evaluateExpr(dstExpr.base, frame).asList()
            val idx = evaluateExpr(dstExpr.index, frame).asInteger()
            if (idx < 0 || idx >= list.size) frame.error(
                dstExpr.errPos,
                "list:index:${list.size}:$idx",
                "List index out of bounds: $idx (size ${list.size})",
            )
            val finalValue = if (op != null) {
                val old = list[idx.toInt()]
                evaluateBinaryOp(op, old, value)
            } else value
            list[idx.toInt()] = finalValue
        }

        is RR_Expr.MapSubscript -> {
            val map = evaluateExpr(dstExpr.base, frame).asMutableMap()
            val key = evaluateExpr(dstExpr.key, frame)
            val finalValue = if (op != null) {
                val old = map[key] ?: frame.error(
                    dstExpr.errPos,
                    "fn_map_get_novalue:${key.strCode()}",
                    "Key not in map: ${key.str()}",
                )
                evaluateBinaryOp(op, old, value)
            } else value
            map[key] = finalValue
        }

        is RR_Expr.MemberAccess -> {
            // Handle safe member access assignment (e.g., r?.x = value, r?.x++)
            val baseValue = evaluateExpr(dstExpr.base, frame)
            if (dstExpr.safe && baseValue == Rt_NullValue) return

            when (val calc = dstExpr.calculator) {
                is RR_MemberCalculator.StructAttr -> {
                    val struct = baseValue.asStruct()
                    val finalValue = if (op != null) {
                        val old = struct.get(calc.attrIndex)
                        evaluateBinaryOp(op, old, value)
                    } else value
                    @Suppress("ExplicitCollectionElementAccessMethod")
                    struct.set(calc.attrIndex, finalValue)
                }

                else -> error("Cannot assign to member access with calculator: ${calc::class.simpleName}")
            }
        }

        else -> error("Cannot assign to expression: ${dstExpr::class.simpleName}")
    }
}
