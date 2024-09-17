package net.postchain.rell.codegen.kotlin

import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.utils.doc.DocCommentItem
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.docs.AbstractDocGenerator
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

object KotlinDocGenerator : AbstractDocGenerator() {
    override fun formatParamTag(paramTag: DocCommentItem, param: R_FunctionParam?): String {
        return "@param ${paramTag.key?.snakeToLowerCamelCase()} ${paramTag.text}"
    }

    override fun formatReturnTag(returnTag: DocCommentItem, returnType: String?): String {
        return "@return ${returnTag.text}"
    }
}