/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ErrorTracker
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmMap

class DocException(val code: String, msg: String): RuntimeException(msg) {
    companion object {
        val ERROR_TRACKER = ErrorTracker.throwing { code, msg -> DocException(code, msg) }

        fun check(b: Boolean, codeMsgLazy: () -> Pair<String, String>) {
            if (!b) {
                val pair = codeMsgLazy()
                throw DocException(pair.first, pair.second)
            }
        }
    }
}

object DocUtils {
    fun getDocDefinitionByPath(def: DocDefinition, path: List<String>): DocDefinition? {
        var curDef = def
        for (name in path) {
            val nextDef = curDef.getDocMember(name)
            curDef = nextDef ?: return null
        }
        return curDef
    }

    fun getDocDefinitionByName(rApp: R_App, name: String): DocDefinition? {
        val moduleName = R_ModuleName.of(name.substringBefore(":"))
        val path = if (":" !in name) listOf() else name.substringAfter(":").split(".")
        val rModule = rApp.moduleMap.getValue(moduleName)
        return getDocDefinitionByPath(rModule, path)
    }

    fun getDocSymbolByPath(def: DocDefinition, path: List<String>): DocSymbol? {
        val resDef = getDocDefinitionByPath(def, path)
        return resDef?.docSymbol
    }
}

class DocFunctionParamComments(
    val functionComment: DocComment?,
    val paramComments: Map<R_Name, DocComment>,
) {
    companion object {
        val NULL = DocFunctionParamComments(null, immMapOf())

        fun make(
            funName: R_DefinitionName,
            funComment: DocComment?,
            paramNames: List<R_Name>,
            paramComments: Map<R_Name, DocComment>,
            errorTracker: ErrorTracker,
        ): DocFunctionParamComments {
            val resParamComments = getCombinedParamComments(funName, funComment, paramComments, paramNames, errorTracker)
            val resFunComment = getCombinedFunctionComment(funComment, resParamComments, errorTracker)
            return DocFunctionParamComments(resFunComment, resParamComments)
        }

        private fun getCombinedParamComments(
            funName: R_DefinitionName,
            rawFunComment: DocComment?,
            rawParamComments: Map<R_Name, DocComment>,
            params: List<R_Name>,
            errorTracker: ErrorTracker,
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
                if (rName == null) {
                    val msg = "Invalid parameter name in comment: '${item.key}'"
                    errorTracker.error("comment:param:invalid_name:[$funName]:${item.key}", msg)
                } else if (rName !in params) {
                    val msg = "Unknown parameter specified in comment: '$rName' ($funName)"
                    errorTracker.error("comment:param:unknown:[$funName]:$rName", msg)
                }
            }

            return resComments.toImmMap()
        }

        private fun getCombinedFunctionComment(
            rawFunComment: DocComment?,
            paramComments: Map<R_Name, DocComment>,
            errorTracker: ErrorTracker,
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
                b.tag(DocCommentTag.PARAM, DocCommentItem(param.str, comment.description))
            }

            return b.build()
        }
    }
}
