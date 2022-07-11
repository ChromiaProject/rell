package net.postchain.rell.codegen.deps

import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.R_Definition

data class CamelCaseClassName(
    override val rellName: String,
    override val className: String,
    override val moduleName: String
) : ClassName {

    companion object {
        fun fromRellDefinition(enum: R_Definition): ClassName {
            return CamelCaseClassName(
                enum.appLevelName,
                enum.simpleName.snakeToUpperCamelCase(),
                enum.defId.module.substringBefore("["),
            )
        }
    }
}