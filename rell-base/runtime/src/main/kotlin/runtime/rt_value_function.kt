/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.expr.R_FunctionCallTarget
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList

class Rt_FunctionValue(
    private val rtType: Rt_Type,
    private val mapping: R_PartialCallMapping,
    private val target: R_FunctionCallTarget,
    private val baseValue: Rt_Value?,
    exprValues: List<Rt_Value>,
): Rt_Value() {
    private val exprValues = let {
        checkEquals(exprValues.size, mapping.exprCount)
        exprValues.toImmList()
    }

    override val valueType = Rt_CoreValueTypes.FUNCTION.type()

    override fun type() = rtType
    override fun asFunction() = this

    override fun strCode(showTupleFieldNames: Boolean): String {
        return STR_RECURSION_DETECTOR.calculate(this) {
            val argsStr = mapping.args.joinToString(",") { if (it.wild) "*" else exprValues[it.index].strCode() }
            "fn[${functionTargetStrCode(target, baseValue)}($argsStr)]"
        } ?: "fn[...]"
    }

    override fun str(format: StrFormat) = "${functionTargetStr(target, baseValue, format)}(*)"

    fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, mapping.wildCount)
        val combinedArgs = mapping.args.map { if (it.wild) args[it.index] else exprValues[it.index] }
        return callFunctionTarget(target, callCtx, baseValue, combinedArgs)
    }

    fun combine(newType: Rt_Type, newMapping: R_PartialCallMapping, newArgs: List<Rt_Value>): Rt_Value {
        checkEquals(newMapping.args.size, mapping.wildCount)
        checkEquals(newArgs.size, newMapping.exprCount)

        val resExprValues = exprValues + newArgs

        val resArgMappings = mapping.args.mapToImmList { m1 ->
            if (m1.wild) {
                val m2 = newMapping.args[m1.index]
                if (m2.wild) m2 else R_PartialArgMapping(false, mapping.exprCount + m2.index)
            } else {
                m1
            }
        }

        val resMapping = R_PartialCallMapping(resExprValues.size, newMapping.wildCount, resArgMappings)
        return Rt_FunctionValue(newType, resMapping, target, baseValue, resExprValues)
    }

    companion object {
        private val STR_RECURSION_DETECTOR = Rt_ValueRecursionDetector()
    }
}
