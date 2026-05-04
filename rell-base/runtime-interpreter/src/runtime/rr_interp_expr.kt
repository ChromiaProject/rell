/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList

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

internal fun Rt_InterpreterImpl.evaluateBinary(expr: RR_Expr.Binary, frame: Rt_CallFrame): Rt_Value {
    val left = evaluateExpr(expr.left, frame)
    // Short-circuit for logical operators.
    val sc = shortCircuitBinaryOp(expr.op, left)
    if (sc != null) return sc
    val right = evaluateExpr(expr.right, frame)
    return try {
        val cmpInfo = expr.cmpInfo
        if (cmpInfo != null) {
            evaluateCmpBinaryOp(cmpInfo, left, right)
        } else {
            evaluateBinaryOp(expr.op, left, right)
        }
    } catch (e: Rt_Exception) {
        val errPos = expr.errPos
        if (errPos != null) frame.error(errPos, e) else throw e
    }
}

internal fun Rt_InterpreterImpl.evaluateUnary(expr: RR_Expr.Unary, frame: Rt_CallFrame): Rt_Value {
    val operand = evaluateExpr(expr.expr, frame)
    return try {
        evaluateUnaryOp(expr.op, operand)
    } catch (e: Rt_Exception) {
        frame.error(expr.errPos, e)
    }
}

internal fun Rt_InterpreterImpl.evaluateWhen(expr: RR_Expr.When, frame: Rt_CallFrame): Rt_Value {
    val idx = evaluateWhenChooser(expr.chooser, frame)
    check(idx >= 0) { "when expression: no matching branch and no else" }
    return evaluateExpr(expr.exprs[idx], frame)
}

/**
 * Returns the matched branch index, or -1 if no branch matched and there is no else.
 * Callers must handle -1: statement whens skip execution; expression whens are an error.
 */
internal fun Rt_InterpreterImpl.evaluateWhenChooser(chooser: RR_WhenChooser, frame: Rt_CallFrame): Int = when (chooser) {
    is RR_WhenChooser.Iterative -> {
        val key = evaluateExpr(chooser.keyExpr, frame)
        var result = chooser.elseIndex
        for (cond in chooser.conditions) {
            val condValue = evaluateExpr(cond.expr, frame)
            if (key == condValue) {
                result = cond.index
                break
            }
        }
        result
    }

    is RR_WhenChooser.Lookup -> {
        val key = evaluateExpr(chooser.keyExpr, frame)
        var result = chooser.elseIndex
        for (i in chooser.keys.indices) {
            if (toRtValue(chooser.keys[i]) == key) {
                result = chooser.values[i]
                break
            }
        }
        result
    }
}

internal fun Rt_InterpreterImpl.evaluateFunctionCall(expr: RR_Expr.FunctionCall, frame: Rt_CallFrame): Rt_Value =
    when (val call = expr.call) {
        is RR_FunctionCall.Full -> {
            val base = expr.base?.let { evaluateExpr(it, frame) }
            // Null-safe function call: if safe=true and base is null, return null without calling.
            if (expr.safe && base == Rt_NullValue) {
                Rt_NullValue
            } else {
                val args = call.args.map { evaluateExpr(it, frame) }

                val mappedArgs = if (isIdentityMapping(call.mapping, args.size))
                    args
                else
                    call.mapping.map { args[it] }

                callTarget(call.target, base, mappedArgs, frame, callPos = call.callPos)
            }
        }

        is RR_FunctionCall.Partial -> {
            val base = expr.base?.let { evaluateExpr(it, frame) }
            // Null-safe partial application: if safe=true and base is null, return null.
            if (expr.safe && base == Rt_NullValue) {
                Rt_NullValue
            } else {
                val values = call.args.map { evaluateExpr(it, frame) }
                val rtType = resolveType(call.returnType)
                val mappingArgs = call.mappingValues.mapToImmList { idx ->
                    if (idx < 0) {
                        R_PartialArgMapping(true, -(idx + 1))
                    } else {
                        R_PartialArgMapping(false, idx)
                    }
                }
                val mapping = R_PartialCallMapping(values.size, call.wildArgCount, mappingArgs)
                val rTarget = Rt_FunctionCallTarget(this, call.target, frame)
                createFunctionValueFromTarget(rTarget, rtType, mapping, base, values)
            }
        }
    }

internal fun Rt_InterpreterImpl.evaluateMemberAccess(expr: RR_Expr.MemberAccess, frame: Rt_CallFrame): Rt_Value {
    val base = evaluateExpr(expr.base, frame)
    if (expr.safe && base == Rt_NullValue) return Rt_NullValue
    return evaluateMemberCalculator(expr.calculator, base, frame)
}

internal fun Rt_InterpreterImpl.evaluateMemberCalculator(
    calc: RR_MemberCalculator,
    base: Rt_Value,
    frame: Rt_CallFrame,
): Rt_Value = when (calc) {
    is RR_MemberCalculator.StructAttr -> base.asStruct().get(calc.attrIndex)
    is RR_MemberCalculator.TupleAttr -> base.asTuple()[calc.attrIndex]
    is RR_MemberCalculator.VirtualTupleAttr -> base.asVirtualTuple().get(calc.fieldIndex)
    is RR_MemberCalculator.VirtualStructAttr -> base.asVirtualStruct().get(calc.attrDefIndex)
    is RR_MemberCalculator.DataAttribute -> {
        evaluateDataAttributeAccess(base, calc, frame)
    }

    is RR_MemberCalculator.DataAttributeExpr -> frame.block(calc.lambdaBlock) {
        frame.setUnchecked(calc.lambdaVarPtr, base, false)
        evaluateExpr(calc.expr, frame)
    }

    is RR_MemberCalculator.SysFunction -> {
        val fn = checkNotNull(
            stdlib.sysFunctions[calc.fnName] ?: Rt_StdlibEnv.global().sysFunctions[calc.fnName],
        ) {
            "Member function not found: ${calc.fnName}"
        }
        R_SysFunctionUtils.call(frame.callCtx(), fn, sysFnDisplayName(calc.fnName), listOf(base))
    }

    is RR_MemberCalculator.ExprEval -> evaluateExpr(calc.expr, frame)

    is RR_MemberCalculator.FunctionCall -> {
        when (val call = calc.call) {
            is RR_FunctionCall.Full -> callTarget(
                call.target,
                base,
                call.args.map { evaluateExpr(it, frame) },
                frame,
                callPos = call.callPos,
            )

            is RR_FunctionCall.Partial -> {
                val values = call.args.map { evaluateExpr(it, frame) }
                val rtType = resolveType(call.returnType)
                val mappingArgs = call.mappingValues.mapToImmList { idx ->
                    if (idx < 0) {
                        R_PartialArgMapping(true, -(idx + 1))
                    } else {
                        R_PartialArgMapping(false, idx)
                    }
                }
                val mapping = R_PartialCallMapping(values.size, call.wildArgCount, mappingArgs)
                val rTarget = Rt_FunctionCallTarget(this, call.target, frame)
                createFunctionValueFromTarget(rTarget, rtType, mapping, base, values)
            }
        }
    }
}

internal fun Rt_InterpreterImpl.evaluateDataAttributeAccess(
    base: Rt_Value,
    calc: RR_MemberCalculator.DataAttribute,
    frame: Rt_CallFrame,
): Rt_Value {
    val entityDef = rrApp.allEntities[calc.entityDefIndex]

    // Special case: "rowid" is not a regular attribute — it's the row identity column
    if (calc.attrName == "rowid") {
        val rowid = if (base is Rt_RowidValue) base.value else base.asObjectId()
        return Rt_RowidValue.get(rowid)
    }

    val attr = checkNotNull(entityDef.strAttributes[calc.attrName]) {
        "Attribute not found: ${calc.attrName} on entity ${entityDef.rName}"
    }
    val rtResultType = resolveType(attr.type)

    // base may be an Rt_EntityValue (normal path) or Rt_RowidValue (from at-expression DB result).
    val rowid = if (base is Rt_RowidValue) base.value else base.asObjectId()
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    val alias = "A00"

    val pSql = ParameterizedSql(
        "SELECT ${renderJooq(DbSqlGen.columnFieldExt(alias, attr.sqlMapping))}" +
                " FROM ${renderName(table)} $alias" +
                " WHERE ${renderJooq(DbSqlGen.columnFieldExt(alias, entityDef.sqlMapping.rowidColumn))} = ?",
        listOf(Rt_IntValue.get(rowid)).toImmList(),
    )

    val results = buildList {
        frame.userSqlExec.executeQuery(pSql) { row ->
            this += checkNotNull(rtResultType.sqlAdapter) { "No SQL adapter for type: ${rtResultType.name}" }
                .fromSql(row, 1, false)
        }

        if (size != 1) {
            val msg = if (isEmpty()) {
                "Object not found in the database: ${base.str()} (was deleted?)"
            } else {
                "Found more than one object ${base.str()} in the database: $size"
            }
            throw Rt_Exception.common("expr_entity_attr_count:${size}", msg)
        }
    }

    return results.first()
}

internal fun Rt_InterpreterImpl.evaluateTypeAdapter(expr: RR_Expr.TypeAdapter, frame: Rt_CallFrame): Rt_Value {
    val value = evaluateExpr(expr.expr, frame)
    return applyTypeAdapter(expr.adapter, value)
}

internal fun Rt_InterpreterImpl.evaluateAttributeDefaultValue(
    expr: RR_Expr.AttributeDefaultValue,
    frame: Rt_CallFrame,
): Rt_Value {
    // Evaluate the resolved default value expression in a fresh frame.
    val initFrame =
        createFrame(frame.exeCtx, expr.initFrame, dbUpdateAllowed = frame.defCtx.dbUpdateAllowed, expr.defId)

    return try {
        evaluateExpr(expr.innerExpr, initFrame)
    } catch (e: Rt_Exception) {
        val createFilePos = expr.createFilePos
        if (createFilePos != null) frame.error(ErrorPos(createFilePos), e, true) else throw e
    }
}
