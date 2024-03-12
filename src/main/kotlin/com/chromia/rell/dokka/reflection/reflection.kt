package com.chromia.rell.dokka.reflection

import com.chromia.rell.dokka.translators.RellModuleVisitor
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_FunctionBase
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_GlobalConstantBody
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_QueryBody
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_RoutineDefinition
import net.postchain.rell.base.model.expr.R_ExtendableFunctionUid
import net.postchain.rell.base.model.expr.R_FunctionExtensions
import net.postchain.rell.base.model.expr.R_FunctionExtensionsTable
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible



fun R_FunctionExtensionsTable.getFunctionExtensionsByReflection() =
        R_FunctionExtensionsTable::class.memberProperties.find { it.name == "list" }!!.let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (it.get(this) as List<R_FunctionExtensions>)
        }

fun R_ExtendableFunctionUid.getNameByReflection() =
        R_ExtendableFunctionUid::class.memberProperties.find { it.name == "name" }!!.let {
            it.isAccessible = true
            (it.get(this) as String)
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

fun R_FunctionDefinition.getParamsByReflection() =
        R_FunctionDefinition::class.memberProperties.find { it.name == "fnBase" }!!.let {
            it.isAccessible = true
            (it.get(this) as R_FunctionBase).getHeader().params
        }

fun R_RoutineDefinition.getTypeByReflection() = when (this) {
    is R_OperationDefinition -> type
    is R_QueryDefinition -> getTypeByReflection()
    is R_FunctionDefinition -> getTypeByReflection()
}

private fun R_FunctionDefinition.getTypeByReflection() =
        R_FunctionDefinition::class.memberProperties.find { it.name == "fnBase" }!!
                .let {
                    it.isAccessible = true
                    (it.get(this) as R_FunctionBase).getHeader().type
                }

private fun R_QueryDefinition.getTypeByReflection() =
        R_QueryDefinition::class.memberProperties.find { it.name == "bodyLate" }!!.let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (it.get(this) as C_LateInit<R_QueryBody>).get().retType
        }

