package net.postchain.rell.codegen.docs

import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.utils.doc.DocCommentItem
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.codegen.util.rTypeToJsTypeString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

abstract class AbstractDocGenerator {
    open fun formatDoc(docSymbol: DocSymbol?,
                       wrapInDocComments: Boolean = false,
                       params: List<R_FunctionParam> = emptyList(),
                       returnType: String? = null,
                       padding: String = "* "): String {
        val comment = format(docSymbol, params, returnType)
        val paddedComment = padLeft(comment, padding)
        return when {
            wrapInDocComments && comment.isNotBlank() ->
                """
                |/**
                |$paddedComment
                |${padding.trimEnd()}/
                """.trimMargin()
            wrapInDocComments -> ""
            else -> paddedComment
        }
    }

    private fun padLeft(comment: String, padding: String = "* "): String {
        return comment.trimEnd('\n').lines().joinToString("\n") { "$padding$it" }
    }

    open fun format(docSymbol: DocSymbol?, params: List<R_FunctionParam>, returnType: String?): String {
        if (docSymbol == null) return ""
        return buildString {
            docSymbol.comment?.let { comment ->
                appendLine()
                appendLine(sanitizeDescription(comment.description))

                val mappedParams = params.associateBy { it.name.str }
                val mappedTags = comment.tags.mapKeys { it.key.code }
                mappedTags[DocCommentTag.AUTHOR.code]?.let { items ->
                    items.forEach { appendLine(formatAuthorTag(it)) }
                }
                mappedTags[DocCommentTag.SEE.code]?.let { items ->
                    items.forEach { appendLine(formatSeeTag(it)) }
                }
                mappedTags[DocCommentTag.SINCE.code]?.let { items ->
                    items.forEach { appendLine(formatSinceTag(it)) }
                }
                mappedTags[DocCommentTag.PARAM.code]?.let { items ->
                    items.forEach { appendLine(formatParamTag(it, mappedParams[it.key])) }
                } ?: formatDefaultParamTags(this, params)
                mappedTags[DocCommentTag.THROWS.code]?.let { items ->
                    items.forEach { appendLine(formatThrowsTag(it)) }
                }
                mappedTags[DocCommentTag.RETURN.code]?.let { items ->
                    items.forEach { appendLine(formatReturnTag(it, returnType)) }
                } ?: formatDefaultReturnTag(this, returnType, docSymbol)
            } ?: run {
                formatDefaultParamTags(this, params)
                formatDefaultReturnTag(this, returnType, docSymbol)
            }
        }
    }

    open fun sanitizeDescription(description: String): String {
        return description
    }

    open fun formatDefaultParamTags(sb: StringBuilder, params: List<R_FunctionParam>) {
        // Do nothing
    }

    open fun formatDefaultReturnTag(sb: StringBuilder, returnType: String?, docSymbol: DocSymbol) {
        // Do nothing
    }

    open fun formatSinceTag(sinceTag: DocCommentItem): String {
        return "@since ${sinceTag.text}"
    }

    open fun formatSeeTag(seeTag: DocCommentItem): String {
        return "@see ${seeTag.text}"
    }

    open fun formatParamTag(paramTag: DocCommentItem, param: R_FunctionParam?): String {
        val paramName = paramTag.key?.snakeToLowerCamelCase() ?: ""
        return if (param != null) {
            "@param {${rTypeToJsTypeString(param.type)}} $paramName ${paramTag.text}"
        } else {
            "@param $paramName ${paramTag.text}"
        }
    }

    open fun formatReturnTag(returnTag: DocCommentItem, returnType: String?): String {
        return if (returnType != null) {
            "@return {${returnType}} ${returnTag.text}"
        } else {
            "@return ${returnTag.text}"
        }
    }

    open fun formatThrowsTag(throwsTag: DocCommentItem): String {
        return "@throws ${throwsTag.text}"
    }

    open fun formatAuthorTag(authorTag: DocCommentItem): String {
        return "@author ${authorTag.text}"
    }
}
