/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.L_FunctionFlags
import net.postchain.rell.base.lmodel.L_FunctionHeader
import net.postchain.rell.base.lmodel.L_MemberHeader
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

        return docSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = dec,
            comment = comment,
        )
    }

    fun specialFunction(fullName: R_FullName, memberHeader: L_MemberHeader, isStatic: Boolean): DocSymbol {
        return docSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = DocDeclaration_SpecialFunction(fullName.last, isStatic = isStatic),
            comment = memberHeader.docComment,
        )
    }

    fun constant(fullName: R_FullName, memberHeader: L_MemberHeader, mType: M_Type, rValue: Rt_Value): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        val docValue = C_DocUtils.docValue(rValue)
        val dec = DocDeclaration_Constant(DocModifiers.NONE, fullName.last, docType, docValue)

        return docSymbol(
            kind = DocSymbolKind.CONSTANT,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = dec,
            comment = memberHeader.docComment,
        )
    }

    fun property(fullName: R_FullName, memberHeader: L_MemberHeader, mType: M_Type, pure: Boolean): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        return docSymbol(
            kind = DocSymbolKind.PROPERTY,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = DocDeclaration_Property(fullName.last, docType, pure),
            comment = memberHeader.docComment,
        )
    }

    fun docSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        comment: DocComment?,
        mountName: String? = null,
    ): DocSymbol {
        return DocSymbol(
            kind = kind,
            symbolName = symbolName,
            mountName = mountName,
            declaration = declaration,
            comment = comment,
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
            funComment: DocComment?,
            paramNames: List<R_Name>,
            paramComments: Map<R_Name, DocComment>,
        ): Ld_FunctionParamComments {
            val resParamComments = getCombinedParamComments(fullName, funComment, paramComments, paramNames)
            val resFunComment = getCombinedFunctionComment(funComment, resParamComments)
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
