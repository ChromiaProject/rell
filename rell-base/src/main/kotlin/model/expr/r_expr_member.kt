/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.utils.checkEquals

internal class R_MemberExpr(
    private val base: R_Expr,
    private val calculator: R_MemberCalculator,
    private val safe: Boolean,
): R_BaseExpr(C_Utils.effectiveMemberType(calculator.type, safe)) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        if (safe && baseValue == Rt_NullValue) {
            return Rt_NullValue
        }
        if (base.type != R_NullType) {
            check(baseValue != Rt_NullValue)
        }
        check(baseValue != Rt_UnitValue)
        val value = calculator.calculate(frame, baseValue)
        return value
    }
}

internal abstract class R_MemberCalculator(val type: R_Type) {
    abstract fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value
}

internal class R_MemberCalculator_Error(type: R_Type, private val msg: String): R_MemberCalculator(type) {
    override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
        throw RellInterpreterCrashException(msg)
    }
}

internal class R_MemberCalculator_TupleAttr(
    type: R_Type,
    private val attrIndex: Int,
): R_MemberCalculator(type) {
    override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
        val values = baseValue.asTuple()
        return values[attrIndex]
    }
}

internal class R_MemberCalculator_VirtualTupleAttr(
    type: R_Type,
    private val fieldIndex: Int,
): R_MemberCalculator(type) {
    override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
        val tuple = baseValue.asVirtualTuple()
        val res = tuple.get(fieldIndex)
        return res
    }
}

internal class R_MemberCalculator_VirtualStructAttr(
    type: R_Type,
    private val attr: R_Attribute,
): R_MemberCalculator(type) {
    override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
        val structValue = baseValue.asVirtualStruct()
        return structValue.get(attr.index)
    }
}

internal class R_MemberCalculator_DataAttribute(
    type: R_Type,
    private val atBase: Db_AtExprBase,
    private val lambda: R_LambdaBlock,
): R_MemberCalculator(type) {
    override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
        val list = lambda.execute(frame, baseValue) {
            val redFrom = atBase.toRedFrom(frame)
            val redBase = redFrom.toRedBase(frame)
            redBase.execute(frame, Rt_AtExprExtras.NULL)
        }

        if (list.size != 1) {
            val msg = if (list.isEmpty()) {
                "Object not found in the database: ${baseValue.str()} (was deleted?)"
            } else {
                "Found more than one object ${baseValue.str()} in the database: ${list.size}"
            }
            throw Rt_Exception.common("expr_entity_attr_count:${list.size}", msg)
        }

        checkEquals(list[0].size, 1)
        val res = list[0][0]
        return res
    }
}
