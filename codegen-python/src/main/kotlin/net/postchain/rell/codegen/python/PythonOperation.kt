package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation

class PythonOperation(opDef: R_OperationDefinition) : PythonFunction(
    CamelCaseClassName.fromRellOperation(opDef),
        opDef.mountName,
        opDef.params(),
        opDef.docSymbol,
        opDef.type,
       ""
), Operation {
    override val imports: List<String> = super.imports(PyFunctionImplementations.OPERATION)

    override fun formatReturnObject(): String = buildString {
        append("return Operation(")
        append("""op_name="${mountName.str().replace('.', '_')}", """)
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