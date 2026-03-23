/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class PythonOperation(opDef: R_OperationDefinition) : PythonFunction(
    CamelCaseClassName.fromRellOperation(opDef),
        opDef.mountName,
        opDef.params(),
        opDef.docSymbol,
        opDef.getTypeByReflection(),
       "_operation"
), Operation {
    override val imports: List<String> = super.imports(PyFunctionImplementations.OPERATION)

    override fun formatReturnObject(): String = buildString {
        append("return Operation(")
        append("""op_name="${sanitizedFunName()}", """)
        append("""args=""")
        append(if (params.isNotEmpty()) formatReturnObjectArgs() else "[]")
        append(")")
    }

    override fun formatReturnObjectArgs(): String =
        params.joinToString(", ", "[", "]") {
            it.name.str
        }

    override fun formatReturnType() = "Operation"
}

private fun R_OperationDefinition.getTypeByReflection() =
    R_OperationDefinition::class.memberProperties.find { it.name == "type"}!!
        .let {
            it.isAccessible = true
            (it.get(this) as R_Type)
        }