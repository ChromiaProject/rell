package com.chromia.rell.dokka.reflection

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_ExtendableFunctionUid
import net.postchain.rell.base.model.expr.R_FunctionExtensions
import net.postchain.rell.base.model.expr.R_FunctionExtensionsTable
import net.postchain.rell.base.runtime.Rt_Value
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun R_Expr.getValueByReflection() =
    javaClass.kotlin.memberProperties.find { it.name == "value" }?.let {
        it.isAccessible = true
        it.get(this) as Rt_Value
    }

fun R_FunctionExtensionsTable.getFunctionExtensionsByReflection() =
        R_FunctionExtensionsTable::class.memberProperties.find { it.name == "list" }!!.let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            it.get(this) as List<R_FunctionExtensions>
        }

fun R_ExtendableFunctionUid.getNameByReflection() =
        R_ExtendableFunctionUid::class.memberProperties.find { it.name == "name" }!!.let {
            it.isAccessible = true
            it.get(this) as String
        }

fun R_FunctionBase.getDefNameByReflection() =
        R_FunctionBase::class.memberProperties.find { it.name == "defName" }!!.let {
            it.isAccessible = true
            it.get(this) as R_DefinitionName
        }

fun R_GlobalConstantDefinition.getTypeByReflection() =
        R_GlobalConstantDefinition::class.memberProperties.find { it.name == "bodyGetter" }!!.let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (it.get(this) as C_LateGetter<R_GlobalConstantBody>).get().type
        }

fun R_FunctionDefinition.getParamsByReflection() = params()

fun R_RoutineDefinition.getTypeByReflection() = when (this) {
    is R_OperationDefinition -> type
    is R_QueryDefinition -> getTypeByReflection()
    is R_FunctionDefinition -> getTypeByReflection()
}

fun R_FunctionBase.getHeaderByReflection() =
    R_FunctionBase::class.memberFunctions.find { it.name == "getHeader" }!!
                .let {
                    it.isAccessible = true
                    it.call(this) as R_FunctionHeader
                }

fun R_GlobalConstantDefinition.toMetaGtvByReflection() =
    R_GlobalConstantDefinition::class.memberFunctions.find { it.name == "toMetaGtv" }!!
                .let {
                    it.isAccessible = true
                    it.call(this) as Gtv
                }

private fun R_FunctionDefinition.getTypeByReflection() =
        R_FunctionDefinition::class.memberProperties.find { it.name == "fnBase" }!!
                .let {
                    it.isAccessible = true
                    (it.get(this) as R_FunctionBase).getHeaderByReflection().type
                }

private fun R_QueryDefinition.getTypeByReflection() = this.type()
