/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class JavascriptQuery(queryDef: R_QueryDefinition) : JavascriptFunction(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        queryDef.docSymbol,
        false,
    "QueryObject"
), Query {
    override val imports = listOf("")
    override val moduleName: String
        get() = className.module

    override fun formatBody(): String = buildString {
        append("return { name: \"$mountName\"")
        if (params.isNotEmpty()) {
            append(", args: ${formatReturnObjectArgs()}")
        }
        append(" };")
    }
    override fun formatReturnType() = "QueryObject"
    override fun formatReturnObjectArgs(): String {
        return params.joinToString(", ", "{ ", " }") { "${it.name.str}: ${parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type)}"}
    }
}