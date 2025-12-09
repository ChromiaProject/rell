/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.fn.C_FunctionCallParameters
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.expr.R_FunctionCallTarget
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocType
import java.util.*

class R_FunctionType(val params: ImmList<R_Type>, val result: R_Type): R_CompositeType(calcName(params, result)) {
    internal val callParameters by lazy { C_FunctionCallParameters.fromTypes(this.params) }

    private val isError = result.isError() || params.any { it.isError() }

    override fun equals0(other: R_Type) = other is R_FunctionType && params == other.params && result == other.result
    override fun hashCode0() = Objects.hash(params, result)

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun isReference() = true
    override fun isError() = isError
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type is R_FunctionType
                && params.size == type.params.size
                && (result == R_UnitType || result.isAssignableFrom(type.result))
                && params.indices.all { type.params[it].isAssignableFrom(params[it]) }
    }

    override fun strCode(): String = name

    override fun toMetaGtv() = mapOf(
        "type" to "function".toGtv(),
        "params" to params.map { it.toMetaGtv() }.toGtv(),
        "result" to result.toMetaGtv(),
    ).toGtv()

    override fun getLibType0(): C_LibType {
        val mResult = result.mType
        val mParams = params.map { it.mType }
        val mType = M_Types.function(mResult, mParams)
        return C_LibType.make(mType)
    }

    override fun getTypeMeta0(): R_TypeMeta = Meta()

    override fun getTypeArgs(): ImmList<R_Type> {
        return immListOf(result) + params
    }

    override fun explicitComponentTypes() = immListOf<R_Type>()

    override fun docType(): DocType {
        val resultType = result.docType()
        val paramTypes = params.mapToImmList { it.docType() }
        return DocType.function(resultType, paramTypes)
    }

    private inner class Meta: R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type? {
            checkEquals(args.size, params.size + 1)
            val resResult = args[0]
            val resParams = args.drop(1).toImmList()
            return R_FunctionType(resParams, resResult)
        }
    }

    companion object {
        private fun calcName(params: List<R_Type>, result: R_Type): String {
            val paramsStr = params.joinToString(",") { it.name }
            return "($paramsStr)->${result.name}"
        }
    }
}

class Rt_FunctionValue(
    private val type: R_Type,
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

    override fun type() = type
    override fun asFunction() = this

    override fun strCode(showTupleFieldNames: Boolean): String {
        return STR_RECURSION_DETECTOR.calculate(this) {
            val argsStr = mapping.args.joinToString(",") { if (it.wild) "*" else exprValues[it.index].strCode() }
            "fn[${target.strCode(baseValue)}($argsStr)]"
        } ?: "fn[...]"
    }

    override fun str(format: StrFormat) = "${target.str(baseValue, format)}(*)"

    fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, mapping.wildCount)
        val combinedArgs = mapping.args.map { if (it.wild) args[it.index] else exprValues[it.index] }
        return target.call(callCtx, baseValue, combinedArgs)
    }

    fun combine(newType: R_Type, newMapping: R_PartialCallMapping, newArgs: List<Rt_Value>): Rt_Value {
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
