/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.fn.C_FunctionCallParameters
import net.postchain.rell.base.compiler.base.lib.C_LibType


import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocType
import java.util.*

class R_FunctionType(val params: ImmList<R_Type>, val result: R_Type): R_CompositeType(calcName(params, result)) {
    val callParameters by lazy { C_FunctionCallParameters.fromTypes(this.params) }

    private val isError = result.isError() || params.any { it.isError() }

    override fun equals0(other: R_Type) = other is R_FunctionType && params == other.params && result == other.result
    override fun hashCode0() = Objects.hash(params, result)

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun isReference() = true
    override fun isError() = isError

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type is R_FunctionType
                && params.size == type.params.size
                && (result == R_UnitType || result.isAssignableFrom(type.result))
                && params.indices.all { type.params[it].isAssignableFrom(params[it]) }
    }

    override fun strCode(): String = name

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
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type {
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
