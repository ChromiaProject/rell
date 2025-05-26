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
        "_operation"
), Operation {
    override val imports: List<String> = super.imports(PyFunctionImplementations.OPERATION)

    override fun formatReturnObject(): String = /* TODO: */ "// <Operation 'return'>"

    override fun formatReturnType() = /* TODO: */ "// <return>"
}