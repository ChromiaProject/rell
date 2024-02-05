/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.L_FunctionFlags
import net.postchain.rell.base.lmodel.L_FunctionHeader
import net.postchain.rell.base.lmodel.L_FunctionParam
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

object Ld_DocSymbols {
    fun function(
        fullName: R_FullName,
        header: L_FunctionHeader,
        flags: L_FunctionFlags,
        deprecated: C_Deprecated?,
        comment: DocComment?,
    ): DocSymbol {
        val docModifiers = DocModifiers(listOfNotNull(
            C_DocUtils.docModifier(deprecated),
            if (flags.isPure) DocModifier.PURE else null,
            if (flags.isStatic) DocModifier.STATIC else null,
        ))

        val docHeader = L_TypeUtils.docFunctionHeader(header.mHeader)
        val docParams = header.params.map { it.docSymbol.declaration }.toImmList()
        val dec = DocDeclaration_Function(docModifiers, fullName.last, docHeader, docParams)

        return DocSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName.moduleName.str(), fullName.qualifiedName.str()),
            mountName = null,
            declaration = dec,
            comment = comment,
        )
    }

    fun specialFunction(fullName: R_FullName, memberHeader: Ld_MemberHeader, isStatic: Boolean): DocSymbol {
        return DocSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName.moduleName.str(), fullName.qualifiedName.str()),
            mountName = null,
            declaration = DocDeclaration_SpecialFunction(fullName.last, isStatic = isStatic),
            comment = memberHeader.docComment(),
        )
    }

    fun constant(fullName: R_FullName, memberHeader: Ld_MemberHeader, mType: M_Type, rValue: Rt_Value): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        val docValue = C_DocUtils.docValue(rValue)
        val dec = DocDeclaration_Constant(DocModifiers.NONE, fullName.last, docType, docValue)

        return DocSymbol(
            kind = DocSymbolKind.CONSTANT,
            symbolName = DocSymbolName.global(fullName.moduleName.str(), fullName.qualifiedName.str()),
            mountName = null,
            declaration = dec,
            comment = memberHeader.docComment(),
        )
    }

    fun property(fullName: R_FullName, memberHeader: Ld_MemberHeader, mType: M_Type, pure: Boolean): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        return DocSymbol(
            kind = DocSymbolKind.PROPERTY,
            symbolName = DocSymbolName.global(fullName.moduleName.str(), fullName.qualifiedName.str()),
            mountName = null,
            declaration = DocDeclaration_Property(fullName.last, docType, pure),
            comment = memberHeader.docComment(),
        )
    }
}

class Ld_FunctionParamComments(
    val functionComment: DocComment?,
    val paramComments: Map<R_Name, DocComment>,
) {
    companion object {
        fun make(
            fullName: R_FullName,
            funMemberHeader: Ld_MemberHeader,
            paramMemberHeaders: Map<R_Name, Ld_MemberHeader>,
        ): Ld_FunctionParamComments {
            val rawFunComment = funMemberHeader.docComment()

            val rawParamComments = paramMemberHeaders
                .mapNotNull {
                    val c = it.value.docComment()
                    if (c == null) null else (it.key to c)
                }
                .toMap()

            val paramNames = paramMemberHeaders.keys.toList()
            val resParamComments = getCombinedParamComments(fullName, rawFunComment, rawParamComments, paramNames)
            val resFunComment = getCombinedFunctionComment(rawFunComment, resParamComments)
            return Ld_FunctionParamComments(resFunComment, resParamComments)
        }

        private fun getCombinedParamComments(
            fullName: R_FullName,
            rawFunComment: DocComment?,
            rawParamComments: Map<R_Name, DocComment>,
            params: List<R_Name>,
        ): Map<R_Name, DocComment> {
            val resComments = mutableMapOf<R_Name, DocComment>()

            for (param in params) {
                val comment1 = rawFunComment?.getItems(DocCommentTag.PARAM, param.str).orEmpty().firstOrNull()?.text
                val comment2 = rawParamComments[param]
                val description = if (comment1.orEmpty().isNotBlank()) comment1 else (comment2?.description ?: comment1)
                if (description != null || comment2?.tags.orEmpty().isNotEmpty()) {
                    resComments[param] = DocComment(description ?: "", comment2?.tags ?: immMapOf())
                }
            }

            for (item in rawFunComment?.tags?.get(DocCommentTag.PARAM) ?: immListOf()) {
                val rName = if (item.key == null) null else R_Name.ofOpt(item.key)
                Ld_Exception.check(rName != null) {
                    val msg = "Invalid parameter name in comment: '${item.key}'"
                    "comment:param:invalid_name:[$fullName]:${item.key}" to msg
                }
                Ld_Exception.check(rName in params) {
                    val msg = "Unknown parameter specified in comment: '$rName' ($fullName)"
                    "comment:param:unknown:[$fullName]:$rName" to msg
                }
            }

            return resComments.toImmMap()
        }

        private fun getCombinedFunctionComment(
            rawFunComment: DocComment?,
            paramComments: Map<R_Name, DocComment>,
        ): DocComment? {
            if (rawFunComment == null && paramComments.isEmpty()) {
                return null
            }

            val b = DocCommentBuilder()

            if (rawFunComment != null) {
                b.description(rawFunComment.description)
                for ((tag, items) in rawFunComment.tags) {
                    if (tag != DocCommentTag.PARAM) {
                        for (item in items) {
                            b.tag(tag, item)
                        }
                    }
                }
            }

            for ((param, comment) in paramComments) {
                b.tag(DocCommentTag.PARAM, DocCommentItem(param.str, comment.description))
            }

            return b.build()
        }
    }
}

object Ld_DocUtils {
    fun functionComment(memberHeader: Ld_MemberHeader, params: List<L_FunctionParam>): DocComment? {
        val rawComment = memberHeader.docComment()
        val paramComments = params
            .mapNotNull {
                val c = it.docSymbol.comment
                if (c == null) null else (it.name to c)
            }
            .toImmMap()

        if (rawComment == null && paramComments.isEmpty()) {
            return null
        }

        val resParams = mutableListOf<DocCommentItem>()

        for (param in params) {
            val funComment = rawComment?.getItems(DocCommentTag.PARAM, param.name.str).orEmpty().firstOrNull()?.text
            val paramComment = paramComments[param.name]?.description
            check(funComment == null || paramComment == null) // TODO handle
            val resComment = funComment ?: paramComment
            if (resComment != null) {
                resParams.add(DocCommentItem(param.name.str, resComment))
            }
        }

        val b = DocCommentBuilder()
        if (rawComment != null) {
            b.description(rawComment.description)
            for ((tag, items) in rawComment.tags) {
                if (tag != DocCommentTag.PARAM) {
                    for (item in items) {
                        b.tag(tag, item)
                    }
                }
            }
        }

        for (paramItem in resParams) {
            b.tag(DocCommentTag.PARAM, paramItem)
        }

        return b.build()
    }
}
