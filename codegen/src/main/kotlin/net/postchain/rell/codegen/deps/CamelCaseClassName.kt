package net.postchain.rell.codegen.deps

import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.R_Definition
import net.postchain.rell.model.R_OperationDefinition
import net.postchain.rell.model.R_QueryDefinition
import net.postchain.rell.model.R_Type

data class CamelCaseClassName(
        override val rellName: String,
        override val className: String,
        override val module: String,
) : ClassName {

    companion object {
        fun fromRellDefinition(def: R_Definition): ClassName {
            return CamelCaseClassName(
                def.appLevelName,
                def.cDefName.qualifiedName.parts.joinToString("_").snakeToUpperCamelCase(),
                def.defId.module.substringBefore("[")
            )
        }

        fun fromRellOperation(op: R_OperationDefinition): ClassName {
            return CamelCaseClassName(
                op.appLevelName,
                op.cDefName.qualifiedName.parts.joinToString("_").snakeToLowerCamelCase() + "Operation",
                op.defId.module.substringBefore("[")
            )
        }

        fun fromRellQuery(q: R_QueryDefinition): ClassName {
            return CamelCaseClassName(
                q.appLevelName,
                q.cDefName.qualifiedName.parts.joinToString("_").snakeToLowerCamelCase(),
                q.defId.module.substringBefore("[")
            )
        }

        fun fromRellType(type: R_Type): ClassName = fromRellType(
            type.name
        )

        private fun fromRellType(str: String): ClassName {
            if (str.contains("?")) return fromRellType(str.replace("?", "")) // nullables
            if (str.contains("struct<")) return fromRellType(str.substringAfter("struct<").replace(">", "")) // struct<entity>
            if (!str.contains(":")) {
                return CamelCaseClassName(
                    str,
                    str.replace(".", "_").snakeToUpperCamelCase(),
                    ""
                )
            }
            val (module, obj) = str.split(":", limit = 2)
            return CamelCaseClassName(
                str,
                obj.replace(".", "_").snakeToUpperCamelCase(),
                module.substringBefore("[") // external entities
            )
        }
    }
}
