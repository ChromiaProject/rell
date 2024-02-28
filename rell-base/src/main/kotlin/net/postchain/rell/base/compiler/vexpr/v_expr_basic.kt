/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_VarUid
import net.postchain.rell.base.compiler.base.def.C_GlobalConstantFunctionHeader
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue_Complex
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprVarFacts
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.model.Rt_StructValue
import net.postchain.rell.base.model.Rt_TupleValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immSetOf
import net.postchain.rell.base.utils.toImmList

class V_ErrorExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val resType: R_Type,
        private val message: String
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)

    override fun toRExpr0() = R_ErrorExpr(type, message)
}

class V_ConstantValueExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val value: Rt_Value,
    private val valueType: R_Type = value.type(),
    private val dependsOnAtExprs: Set<R_AtExprId> = immSetOf(),
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(valueType, dependsOnAtExprs = dependsOnAtExprs)
    override fun toRExpr0() = R_ConstantValueExpr(type, value)
    override fun constantValue(ctx: V_ConstantValueEvalContext) = value
}

class V_IfExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val resType: R_Type,
        private val condExpr: V_Expr,
        private val trueExpr: V_Expr,
        private val falseExpr: V_Expr,
        private val resVarFacts: C_ExprVarFacts
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, condExpr, trueExpr, falseExpr)
    override fun varFacts0() = resVarFacts

    override fun toRExpr0(): R_Expr {
        val rCond = condExpr.toRExpr()
        val rTrue = trueExpr.toRExpr()
        val rFalse = falseExpr.toRExpr()
        return R_IfExpr(resType, rCond, rTrue, rFalse)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbCond = condExpr.toDbExpr()
        val dbTrue = trueExpr.toDbExpr()
        val dbFalse = falseExpr.toDbExpr()
        val cases = listOf(Db_WhenCase(listOf(dbCond), dbTrue))
        return Db_WhenExpr(resType, null, cases, dbFalse)
    }
}

class V_TupleExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val tupleType: R_TupleType,
        private val exprs: List<V_Expr>
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(tupleType, exprs, canBeDbExpr = false)

    override fun toRExpr0(): R_Expr {
        val rExprs = exprs.map { it.toRExpr() }
        return R_TupleExpr(tupleType, rExprs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                val values2 = values.map { it.value() }
                return Rt_TupleValue(tupleType, values2)
            }
        }
        return C_DbAtWhatValue_Complex(exprs, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val values = exprs.mapNotNull { it.constantValue(ctx) }
        if (values.size != exprs.size) return null
        return Rt_TupleValue(tupleType, values)
    }
}

class V_TypeAdapterExpr(
        exprCtx: C_ExprContext,
        private val resType: R_Type,
        private val expr: V_Expr,
        private val adapter: C_TypeAdapter
): V_Expr(exprCtx, expr.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, expr)

    override fun toRExpr0(): R_Expr {
        val rExpr = expr.toRExpr()
        return adapter.adaptExprR(rExpr)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbExpr = expr.toDbExpr()
        return adapter.adaptExprDb(dbExpr)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val value = expr.constantValue(ctx)
        value ?: return null
        val rAdapter = adapter.toRAdapter()
        return rAdapter.adaptValue(value)
    }
}

class V_CreateExprAttr(val attr: R_Attribute, val expr: V_Expr) {
    fun toRAttr(): R_CreateExprAttr {
        val rExpr = expr.toRExpr()
        return R_CreateExprAttr(attr, rExpr)
    }
}

class V_StructExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val struct: R_Struct,
        explicitAttrs: List<V_CreateExprAttr>,
        implicitAttrs: List<V_CreateExprAttr>
): V_Expr(exprCtx, pos) {
    private val allAttrs = let {
        val impIdxs = implicitAttrs.map { it.attr.index }.toSet()
        val expIdxs = explicitAttrs.map { it.attr.index }.toSet()
        val dupIdxs = impIdxs.intersect(expIdxs)
        require(dupIdxs.isEmpty()) { dupIdxs }

        val allIdxs = impIdxs + expIdxs
        for (attr in struct.attributesList) {
            check(attr.index in allIdxs) { attr }
        }

        implicitAttrs.forEach {
            require(it.attr.hasExpr) { it.attr }
        }

        (explicitAttrs + implicitAttrs).toImmList()
    }

    override fun exprInfo0() = V_ExprInfo.simple(
            struct.type,
            allAttrs.map { it.expr },
            canBeDbExpr = false
    )

    override fun toRExpr0(): R_Expr {
        val rAttrs = allAttrs.map { it.toRAttr() }
        return R_StructExpr(struct, rAttrs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val exprs = allAttrs.map { it.expr }

        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                checkEquals(values.size, allAttrs.size)

                val b = Rt_StructValue.Builder(struct.type)

                for ((i, attr) in allAttrs.withIndex()) {
                    val value = values[i].value()
                    b.set(attr.attr, value)
                }

                return b.build()
            }
        }

        return C_DbAtWhatValue_Complex(exprs, evaluator)
    }
}

class V_GlobalConstantExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val name: R_Name,
        private val resType: R_Type,
        private val varId: C_VarUid,
        private val constId: R_GlobalConstantId,
        private val header: C_GlobalConstantFunctionHeader
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)

    override fun toRExpr0() = R_GlobalConstantExpr(resType, constId)

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val cBody = header.constBody
        return cBody?.constantValue(ctx)
    }

    override fun varId() = varId
    override fun globalConstantId() = constId

    override fun implicitTargetAttrName() = name
}

class V_ParameterDefaultValueExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val resType: R_Type,
        private val callFilePos: R_FilePos,
        private val initFrameGetter: C_LateGetter<R_CallFrame>,
        private val exprGetter: C_LateGetter<R_Expr>
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("param_default_value", null)

    override fun toRExpr0(): R_Expr {
        return R_ParameterDefaultValueExpr(resType, callFilePos, initFrameGetter, exprGetter)
    }
}

class V_AttributeDefaultValueExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val attr: R_Attribute,
        private val createFilePos: R_FilePos?,
        private val initFrameGetter: C_LateGetter<R_CallFrame>
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(attr.type)

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("attr_default_value:${attr.name}",
            "using default value for attribute '${attr.name}' (not supported yet)")

    override fun toRExpr0(): R_Expr {
        return R_AttributeDefaultValueExpr(attr, createFilePos, initFrameGetter)
    }
}
