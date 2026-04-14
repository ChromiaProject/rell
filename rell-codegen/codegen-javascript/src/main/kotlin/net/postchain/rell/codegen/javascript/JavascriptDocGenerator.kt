/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.codegen.docs.AbstractDocGenerator
import net.postchain.rell.codegen.util.rTypeToJsTypeString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

object JavascriptDocGenerator : AbstractDocGenerator() {
    override fun formatDefaultParamTags(sb: StringBuilder, params: List<R_FunctionParam>) {
        params.forEach { param ->
            sb.appendLine("@param {${rTypeToJsTypeString(param.type, allowSet = true)}} ${param.name.str.snakeToLowerCamelCase()}")
        }
    }

    //TODO: We could make the return type to be more useful for QueryObject to also add the value type do the
    // comment. For example `QueryObject<string>`. See if we can use rTypeToJsTypeString(...)
    override fun formatDefaultReturnTag(sb: StringBuilder, returnType: String?, docSymbol: DocSymbol) {
        if (returnType != null && docSymbol.kind in setOf(DocSymbolKind.OPERATION, DocSymbolKind.QUERY)) {
            sb.appendLine("@return {$returnType}")
        }
    }
}