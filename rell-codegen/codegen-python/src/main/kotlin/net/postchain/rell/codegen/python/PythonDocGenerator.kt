package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.utils.doc.DocCommentItem
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.docs.AbstractDocGenerator
import net.postchain.rell.codegen.util.camelToSnakeCase
import net.postchain.rell.codegen.util.rTypeToPythonType

object PythonDocGenerator : AbstractDocGenerator() {
    
    override fun formatDoc(
        docSymbol: DocSymbol?,
        wrapInDocComments: Boolean,
        params: List<R_FunctionParam>,
        returnType: String?,
        padding: String
    ): String {
        val comment = format(docSymbol, params, returnType)
        return when {
            wrapInDocComments && comment.isNotBlank() ->
                """
                |${"\"\"\""}
                |${comment.trim()}
                |${"\"\"\""}
                """.trimMargin()
            wrapInDocComments -> ""
            else -> comment
        }
    }

    override fun formatSinceTag(sinceTag: DocCommentItem): String {
        return "Since: ${sinceTag.text}"
    }

    override fun formatSeeTag(seeTag: DocCommentItem): String {
        return "See: ${seeTag.text}"
    }

    override fun formatAuthorTag(authorTag: DocCommentItem): String {
        return "Author: ${authorTag.text}"
    }

    override fun formatParamTag(paramTag: DocCommentItem, param: R_FunctionParam?): String {
        val paramName = paramTag.key?.camelToSnakeCase() ?: ""
        return if (param != null) {
            "\t$paramName (${rTypeToPythonType(param.type)}): ${paramTag.text}"
        } else {
            "\t$paramName: ${paramTag.text}"
        }
    }

    override fun formatReturnTag(returnTag: DocCommentItem, returnType: String?): String {
        return if (returnType != null) {
            "\t$returnType: ${returnTag.text}"
        } else {
            "\t${returnTag.text}"
        }
    }

    override fun formatThrowsTag(throwsTag: DocCommentItem): String {
        val parts = throwsTag.text.split(" ", limit = 2)
        val exceptionName = parts[0]
        val description = if (parts.size > 1) parts[1] else ""
        return "\t$exceptionName: $description"
    }

    override fun format(docSymbol: DocSymbol?, params: List<R_FunctionParam>, returnType: String?): String {
        docSymbol?.comment ?: return ""
        
        return buildString {
            docSymbol.comment?.let { comment ->
                appendLine(sanitizeDescription(comment.description))

                val mappedParams = params.associateBy { it.name.str }
                val mappedTags = comment.tags.mapKeys { it.key.code }

                mappedTags["author"]?.let { items ->
                    appendLine()
                    items.forEach { appendLine(formatAuthorTag(it)) }
                }

                mappedTags["see"]?.let { items ->
                    items.forEach { appendLine(formatSeeTag(it)) }
                }

                mappedTags["since"]?.let { items ->
                    items.forEach { appendLine(formatSinceTag(it)) }
                }

                val hasParamDocs = mappedTags["param"]?.isNotEmpty() == true
                if (hasParamDocs) {
                    appendLine()
                    appendLine("Args:")
                    mappedTags["param"]?.forEach { paramTag ->
                        appendLine(formatParamTag(paramTag, mappedParams[paramTag.key]))
                    }
                }

                val hasReturnDocs = mappedTags["return"]?.isNotEmpty() == true
                if (hasReturnDocs) {
                    appendLine()
                    appendLine("Returns:")
                    mappedTags["return"]?.forEach { returnTag ->
                        appendLine(formatReturnTag(returnTag, returnType))
                    }
                }

                mappedTags["throws"]?.let { items ->
                    if (items.isNotEmpty()) {
                        appendLine()
                        appendLine("Raises:")
                        items.forEach { throwsTag ->
                            appendLine(formatThrowsTag(throwsTag))
                        }
                    }
                }
            }
        }
    }
} 