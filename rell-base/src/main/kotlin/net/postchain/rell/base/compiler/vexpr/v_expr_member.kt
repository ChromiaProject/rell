/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.core.C_NameHandle
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

abstract class V_TypeValueMember(val type: R_Type, val ideInfo: C_IdeSymbolInfo) {
    abstract fun implicitAttrName(): C_Name?
    abstract fun vExprs(): List<V_Expr>
    open fun varStatesDelta(): C_VarStatesDelta = C_VarStatesDelta.forExpressions(vExprs())
    open fun globalConstantRestriction(): V_GlobalConstantRestriction? = null
    open fun safeCallable() = true

    abstract fun calculator(): R_MemberCalculator
    abstract fun destination(base: V_Expr): C_Destination

    open fun canBeDbExpr(safe: Boolean): Boolean = false
    open fun dbExpr(base: Db_Expr): Db_Expr? = null
    open fun dbExprWhat(base: V_Expr, safe: Boolean): C_DbAtWhatValue? = null

    open fun varPathItem(): C_VarPathItem? = null

    open fun member(
        ctx: C_ExprContext,
        memberNameHand: C_NameHandle,
        member: C_TypeValueMember,
        exprHint: C_ExprHint,
    ): V_TypeValueMember? = null
}

class V_TypeValueMember_Error(
    type: R_Type,
    ideInfo: C_IdeSymbolInfo,
    private val pos: S_Pos,
    private val msg: String,
): V_TypeValueMember(type, ideInfo) {
    override fun implicitAttrName() = null
    override fun vExprs() = immListOf<V_Expr>()
    override fun calculator() = R_MemberCalculator_Error(type, msg)
    override fun destination(base: V_Expr) = throw C_Errors.errBadDestination(pos)
}

class V_ValueMemberExpr private constructor(
    exprCtx: C_ExprContext,
    private val base: V_Expr,
    private val actualType: R_Type,
    private val member: V_TypeValueMember,
    private val memberPos: S_Pos,
    private val safe: Boolean,
    private val baseNulled: C_VarNulled?,
): V_Expr(exprCtx, base.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(
        actualType,
        subExprs = immListOf(base) + member.vExprs(),
        canBeDbExpr = member.canBeDbExpr(safe),
    )

    override fun varStatesDelta0(): C_ExprVarStatesDelta {
        var resDelta = base.varStatesDelta.always
        if (!safe || baseNulled == C_VarNulled.NO) {
            resDelta = resDelta.and(member.varStatesDelta())
        }
        return C_ExprVarStatesDelta.make(always = resDelta)
    }

    override fun implicitTargetAttrName(): R_Name? {
        val isAt = base.isAtExprItem()
        return if (isAt) member.implicitAttrName()?.rName else null
    }

    override fun implicitAtWhatAttrName(): C_Name? {
        val isAt = base.isAtExprItem()
        return if (isAt) member.implicitAttrName() else null
    }

    override fun varKey(): C_VarStateKey? {
        val item = member.varPathItem()
        return varKey(base, item)
    }

    override fun globalConstantRestriction() = member.globalConstantRestriction()

    override fun toRExpr0(): R_Expr {
        val rBase = base.toRExpr()
        val calculator = member.calculator()
        return R_MemberExpr(rBase, calculator, safe)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbBase = base.toDbExpr()
        val dbExpr = member.dbExpr(dbBase)
        return if (dbExpr != null) dbExpr else {
            val rExpr = toRExpr()
            C_ExprUtils.toDbExpr(exprCtx.msgCtx, memberPos, rExpr)
        }
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val res = member.dbExprWhat(base, safe)
        if (res != null) {
            return res
        }

        val calculator = member.calculator()
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                checkEquals(values.size, 1)
                val baseValue = values[0].value()
                return if (safe && baseValue == Rt_NullValue) Rt_NullValue else {
                    calculator.calculate(frame, baseValue)
                }
            }
        }

        return C_DbAtWhatValue_Complex(immListOf(base), evaluator)
    }

    override fun destination(): C_Destination {
        return member.destination(base)
    }

    override fun member0(
        ctx: C_ExprContext,
        selfType: R_Type,
        memberNameHand: C_NameHandle,
        memberValue: C_TypeValueMember,
        safe: Boolean,
        baseNulled: C_VarNulled?,
        exprHint: C_ExprHint,
    ): C_Expr {
        val memberName = memberNameHand.name
        val member2 = member.member(ctx, memberNameHand, memberValue, exprHint)
        member2 ?: return super.member0(ctx, selfType, memberNameHand, memberValue, safe, baseNulled, exprHint)

        val vExpr = make(ctx, base, member2, memberName.pos, safe, baseNulled)
        return C_ValueExpr(vExpr)
    }

    override fun call(ctx: C_ExprContext, pos: S_Pos, args: List<S_CallArgument>, resTypeHint: C_TypeHint): V_Expr {
        return if (safe && member.safeCallable() && member.type !is R_NullableType
            && actualType is R_NullableType && actualType.valueType == member.type)
        {
            callCommon(ctx, pos, args, resTypeHint, member.type, true)
        } else {
            super.call(ctx, pos, args, resTypeHint)
        }
    }

    companion object {
        fun make(
            exprCtx: C_ExprContext,
            base: V_Expr,
            member: V_TypeValueMember,
            memberPos: S_Pos,
            safe: Boolean,
            baseNulled: C_VarNulled?,
        ): V_Expr {
            val type = C_Utils.effectiveMemberType(member.type, safe)
            val vExpr = V_ValueMemberExpr(exprCtx, base, type, member, memberPos, safe, baseNulled)

            val smartKind = "expr" toCodeMsg "expression"
            val forceNotNull = baseNulled == C_VarNulled.NO && safe && member.type !is R_NullableType
            return V_SmartNullableExpr.wrap(exprCtx, vExpr, smartKind, forceNotNull = forceNotNull)
        }

        fun varKey(base: V_Expr, item: C_VarPathItem?): C_VarStateKey? {
            val baseKey = base.varKey()
            return when {
                baseKey == null -> null
                !baseKey.isFull -> baseKey
                item == null -> C_VarStateKey(baseKey.varId, baseKey.path, isFull = false)
                else -> C_VarStateKey(baseKey.varId, baseKey.path + item)
            }
        }
    }
}
