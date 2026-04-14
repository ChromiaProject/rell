/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue_Complex
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.model.R_CompositeType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_TypeMeta
import net.postchain.rell.base.model.expr.Db_ComplexAtWhatEvaluator
import net.postchain.rell.base.model.expr.R_BaseExpr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.Rt_AtWhatItem
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.immListOf

internal class V_LazyExpr(
    exprCtx: C_ExprContext,
    private val innerExpr: V_Expr,
): V_Expr(exprCtx, innerExpr.pos) {
    private val resType: R_Type = R_LazyType(innerExpr.type)

    override fun exprInfo0() = V_ExprInfo.simple(resType, innerExpr)

    override fun toRExpr(): R_Expr {
        val rInnerExpr = innerExpr.toRExpr()
        return R_LazyExpr(resType, rInnerExpr)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                checkEquals(values.size, 1)
                return Rt_WhatItemLazyValue(resType, values[0])
            }
        }

        val allExprs = immListOf(innerExpr)
        return C_DbAtWhatValue_Complex(allExprs, evaluator)
    }
}

private class R_LazyExpr(type: R_Type, private val innerExpr: R_Expr): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return Rt_ExprLazyValue(type, innerExpr, frame)
    }
}

private class R_LazyType(private val valueType: R_Type): R_CompositeType("lazy<${valueType.strCode()}>") {
    override fun equals0(other: R_Type) = other is R_LazyType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()

    override fun getTypeMeta0() = META
    override fun getTypeArgs() = immListOf(valueType)
    override fun strCode() = name
    override fun createGtvConversion() = GtvRtConversion_None

    override fun toMetaGtv() = mapOf(
        "type" to "lazy".toGtv(),
        "value" to valueType.toMetaGtv(),
    ).toGtv()

    override fun getLibType0(): C_LibType {
        val b = DocCode.builder()
        b.keyword("lazy")
        b.raw("<")
        valueType.docType().genCode(b)
        b.raw(">")
        val doc = b.build()
        return C_LibType.make(this, doc)
    }

    companion object {
        private val META = R_TypeMeta.make { t -> R_LazyType(t) }
    }
}

private sealed class Rt_LazyValue(
    private val type: R_Type,
): Rt_Value() {
    private var value: Rt_Value? = null

    final override val valueType = Rt_CoreValueTypes.LAZY.type()

    final override fun type() = type
    final override fun strCode(showTupleFieldNames: Boolean) = "lazy[...]"
    final override fun str(format: StrFormat): String = "lazy[...]"

    protected abstract fun calcValue(): Rt_Value

    final override fun asLazyValue(): Rt_Value {
        var res = value
        if (res == null) {
            res = calcValue()
            value = res
        }
        return res
    }
}

private class Rt_ExprLazyValue(
    type: R_Type,
    private val expr: R_Expr,
    private val frame: Rt_CallFrame,
): Rt_LazyValue(type) {
    override fun calcValue(): Rt_Value {
        return expr.evaluate(frame)
    }
}

private class Rt_WhatItemLazyValue(
    type: R_Type,
    private val item: Rt_AtWhatItem,
): Rt_LazyValue(type) {
    override fun calcValue(): Rt_Value {
        return item.value()
    }
}
