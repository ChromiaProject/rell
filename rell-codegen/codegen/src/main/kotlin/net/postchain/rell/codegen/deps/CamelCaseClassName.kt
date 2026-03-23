/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.deps

import net.postchain.rell.base.model.R_Definition
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import java.util.Locale

data class CamelCaseClassName(
        override val rellName: String,
        override val className: String,
        override val constantName: String,
        override val module: String,
) : ClassName {

    companion object {
        fun fromRellDefinition(def: R_Definition): ClassName {
            return CamelCaseClassName(
                def.appLevelName,
                def.defName.qualifiedName.replace('.', '_').snakeToUpperCamelCase(),
                def.defName.qualifiedName.replace('.', '_').uppercase(Locale.getDefault()),
                def.defId.module.substringBefore("[")
            )
        }

        fun fromRellOperation(op: R_OperationDefinition): ClassName {
            return CamelCaseClassName(
                op.appLevelName,
                op.defName.qualifiedName.replace('.', '_').snakeToLowerCamelCase() + "Operation",
                op.defName.qualifiedName.replace('.', '_').uppercase(Locale.getDefault()),
                op.defId.module.substringBefore("[")
            )
        }

        fun fromRellQuery(q: R_QueryDefinition): ClassName {
            return CamelCaseClassName(
                q.appLevelName,
                q.defName.qualifiedName.replace('.', '_').snakeToLowerCamelCase(),
                q.defName.qualifiedName.replace('.', '_').uppercase(Locale.getDefault()),
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
                    str.replace(".", "_").uppercase(Locale.getDefault()),
                    ""
                )
            }
            val (module, obj) = str.split(":", limit = 2)
            return CamelCaseClassName(
                str,
                obj.replace(".", "_").snakeToUpperCamelCase(),
                obj.replace(".", "_").uppercase(Locale.getDefault()),
                module.substringBefore("[") // external entities
            )
        }
    }
}
