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

        fun fromString(str: String): ClassName {
            if (str.contains("?")) return fromString(str.replace("?", "")) // nullables
            if (str.contains("struct<")) return fromString(str.substringAfter("struct<").replace(">", "")) // struct<entity>
            if (!str.contains(":")) {
                return CamelCaseClassName(str, str.snakeToUpperCamelCase(), "")
            }
            val (module, obj) = str.split(":", limit = 2)
            return CamelCaseClassName(
                str,
                obj.snakeToUpperCamelCase(),
                module.substringBefore("[") // external entities
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CamelCaseClassName

        if (name != other.name) return false
        if (module != other.module) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + module.hashCode()
        return result
    }
}
