/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.lmodel.L_TypeDefDocCodeStrategy
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*

class DocException(val code: String, msg: String): RuntimeException(msg) {
    companion object {
        val ERROR_TRACKER = DocCommentErrorTracker.throwing { code, msg -> DocException(code, msg) }
    }
}

object DocUtils {
    private fun getDocDefinitionByPath(def: DocDefinition, path: List<String>): DocDefinition? {
        var curDef = def
        for (name in path) {
            val nextDef = curDef.getDocMember(name)
            curDef = nextDef ?: return null
        }
        return curDef
    }

    fun getDocDefinitionByName(rApp: R_App, name: String): DocDefinition? {
        val moduleName = ModuleName.of(name.substringBefore(":"))
        val path = if (":" !in name) listOf() else name.substringAfter(":").split(".")
        val rModule = rApp.moduleMap.getValue(moduleName)
        return getDocDefinitionByPath(rModule, path)
    }

    fun getDocSymbolByPath(def: DocDefinition, path: List<String>): DocSymbol? {
        val resDef = getDocDefinitionByPath(def, path)
        return resDef?.docSymbol
    }

    fun docTypeGeneric(name: String, vararg args: R_Type): DocType {
        return docTypeGeneric0(name, args.toImmList())
    }

    fun docTypeGeneric0(name: String, args: ImmList<R_Type>): DocType {
        val docArgs = args.mapToImmList {
            when (it) {
                is R_SubType -> DocTypeSet.subOf(it.valueType.docType())
                else -> DocTypeSet.one(it.docType())
            }
        }
        val strategy = L_TypeDefDocCodeStrategy { argDocs ->
            val b = DocCode.builder()
            b.link(name)
            b.raw("<")
            for ((i, arg) in argDocs.withIndex()) {
                if (i > 0) b.sep(", ")
                b.append(arg)
            }
            b.raw(">")
            b.build()
        }
        return DocType.generic(strategy, docArgs)
    }
}

class DocFunctionParamComments(
    val functionComment: DocComment?,
    val paramComments: ImmMap<Name, DocComment>,
) {
    companion object {
        val NULL = DocFunctionParamComments(null, immMapOf())

        fun make(
            funName: DefinitionName,
            funComment: DocComment?,
            paramNames: List<Name>,
            paramComments: Map<Name, DocComment>,
            errorTracker: DocCommentErrorTracker,
        ): DocFunctionParamComments {
            val resParamComments =
                getCombinedParamComments(funName, funComment, paramComments, paramNames, errorTracker)
            val resFunComment = getCombinedFunctionComment(funComment, resParamComments, errorTracker)
            return DocFunctionParamComments(resFunComment, resParamComments)
        }

        private fun getCombinedParamComments(
            funName: DefinitionName,
            rawFunComment: DocComment?,
            rawParamComments: Map<Name, DocComment>,
            params: List<Name>,
            errorTracker: DocCommentErrorTracker,
        ): ImmMap<Name, DocComment> {
            val resComments = mutableMapOf<Name, DocComment>()

            for (param in params) {
                val comment1 = rawFunComment?.getItems(DocCommentTag.PARAM, param.str).orEmpty().firstOrNull()?.text
                val comment2 = rawParamComments[param]
                val description = if (comment1.orEmpty().isNotBlank()) comment1 else (comment2?.description ?: comment1)
                if (description != null || comment2?.tags.orEmpty().isNotEmpty()) {
                    resComments[param] = DocComment(description ?: "", comment2?.tags.orEmpty())
                }
            }

            for (item in rawFunComment?.tags?.get(DocCommentTag.PARAM).orEmpty()) {
                val errPos = item.keyPos ?: item.codePos
                val rName = if (item.key == null) null else Name.ofOpt(item.key)
                if (rName == null) {
                    val msg = "Invalid parameter name in comment: '${item.key}'"
                    errorTracker.error(errPos, "comment:param:invalid_name:[$funName]:${item.key}", msg)
                } else if (rName !in params) {
                    val msg = "Unknown parameter specified in comment: '$rName' ($funName)"
                    errorTracker.error(errPos, "comment:param:unknown:[$funName]:$rName", msg)
                }
            }

            return resComments.toImmMap()
        }

        private fun getCombinedFunctionComment(
            rawFunComment: DocComment?,
            paramComments: Map<Name, DocComment>,
            errorTracker: DocCommentErrorTracker,
        ): DocComment? {
            if (rawFunComment == null && paramComments.isEmpty()) {
                return null
            }

            val b = DocCommentBuilder(errorTracker)

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
                b.tag(DocCommentTag.PARAM, DocCommentItem(param.str, comment.description, DocCommentPos.NONE, null))
            }

            return b.build()
        }
    }
}
