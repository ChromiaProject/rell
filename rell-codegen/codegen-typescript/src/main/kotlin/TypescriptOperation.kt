/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.codegen.typescript.util.parameterTransformer
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class TypescriptOperation(op: R_OperationDefinition) : TypescriptFunction(
    CamelCaseClassName.fromRellOperation(op),
    op.mountName,
    op.params(),
    op.docSymbol,
    false,
    null
), Operation {
    override val imports: List<String> = imports(TsFunctionImplementations.OPERATION)

    override fun formatReturnObjectArgs(): String {
        return params.joinToString(", ", "[", "]") { parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type) }
    }

    override fun formatReturnType(): String = "Operation"

    override fun returnStructure(returnType: R_Type?): String = ""
}
