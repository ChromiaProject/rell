package net.postchain.rell.codegen.deps

import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.R_Definition
import net.postchain.rell.model.R_OperationDefinition
import net.postchain.rell.model.R_QueryDefinition

data class CamelCaseClassName(
    override val rellName: String,
    override val name: String,
    override val module: String
) : ClassName {

    companion object {
        fun fromRellDefinition(def: R_Definition): ClassName {
            return CamelCaseClassName(
                def.appLevelName,
                def.simpleName.snakeToUpperCamelCase(),
                def.defId.module.substringBefore("["),
            )
        }

        fun fromRellOperation(op: R_OperationDefinition): ClassName {
            return CamelCaseClassName(
                op.appLevelName,
                op.simpleName.snakeToLowerCamelCase() + "Operation",
                op.defId.module.substringBefore("[")
            )
        }
        fun fromRellQuery(q: R_QueryDefinition): ClassName {
            return CamelCaseClassName(
                q.appLevelName,
                q.simpleName.snakeToLowerCamelCase(),
                q.defId.module.substringBefore("[")
            )
        }
    }
}