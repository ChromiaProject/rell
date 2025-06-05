package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query

class PythonQuery(queryDef: R_QueryDefinition) : PythonFunction(
    CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        queryDef.docSymbol,
        queryDef.type(),
    ""
), Query {

    override val imports: List<String> = super.imports(PyFunctionImplementations.QUERY)

    override fun formatReturnObject(): String = /*TODO: */ "// <Query 'return'>"
    override fun formatReturnObjectArgs(): String = /*TODO */ "<Query return 'args'>"

    override fun formatReturnType(): String = /*TODO: */ "// <Query return 'type'>"
}