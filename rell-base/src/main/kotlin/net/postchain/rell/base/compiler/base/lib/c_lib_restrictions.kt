/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lmodel.L_AbstractMember
import net.postchain.rell.base.lmodel.L_MemberHeader
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.capitalizeEx

abstract class C_MemberRestrictions {
    abstract fun isRestricted(compilerOptions: C_CompilerOptions): Boolean
    abstract fun access(msgCtx: C_MessageContext, pos: S_Pos)

    companion object {
        val NULL: C_MemberRestrictions = C_MemberRestrictions_Null

        fun makeLib(
            member: L_AbstractMember,
            declarationType: C_DeclarationType,
            deprecated: C_Deprecated?,
        ): C_MemberRestrictions {
            return makeLib0(member.fullName, declarationType, member.header, deprecated)
        }

        fun makeLib0(
            fullName: R_FullName,
            declarationType: C_DeclarationType,
            memberHeader: L_MemberHeader,
            deprecated: C_Deprecated?,
        ): C_MemberRestrictions {
            val defName = C_DefinitionName(fullName)
            return make0(defName, declarationType, deprecated, memberHeader.since)
        }

        fun makeUser(
            defName: C_DefinitionName,
            declarationType: C_DeclarationType,
            deprecated: C_Deprecated?,
        ): C_MemberRestrictions {
            return make0(defName, declarationType, deprecated, null)
        }

        fun makeAnnotation(name: String, since: R_LangVersion): C_MemberRestrictions {
            return C_MemberRestrictions_Normal("@$name", C_DeclarationType.ANNOTATION, null, since)
        }

        private fun make0(
            defName: C_DefinitionName,
            declarationType: C_DeclarationType,
            deprecated: C_Deprecated?,
            since: R_LangVersion?,
        ): C_MemberRestrictions {
            return if (deprecated == null && since == null) NULL else {
                C_MemberRestrictions_Normal(defName.appLevelName, declarationType, deprecated, since)
            }
        }
    }
}

private object C_MemberRestrictions_Null: C_MemberRestrictions() {
    override fun isRestricted(compilerOptions: C_CompilerOptions) = false

    override fun access(msgCtx: C_MessageContext, pos: S_Pos) {
        // Do nothing.
    }
}

private class C_MemberRestrictions_Normal(
    private val memberName: String,
    private val declarationType: C_DeclarationType,
    private val deprecated: C_Deprecated?,
    private val since: R_LangVersion?,
): C_MemberRestrictions() {
    override fun isRestricted(compilerOptions: C_CompilerOptions): Boolean {
        if (deprecated != null && (deprecated.error || compilerOptions.deprecatedError)) {
            return true
        }

        val version = compilerOptions.compatibility
        if (since != null && version != null && version < since) {
            return true
        }

        return false
    }

    override fun access(msgCtx: C_MessageContext, pos: S_Pos) {
        if (deprecated != null) {
            deprecatedMessage(msgCtx, pos, memberName, deprecated)
        }

        val version = msgCtx.globalCtx.compilerOptions.compatibility
        if (since != null && version != null && version < since) {
            val kindMsg = declarationType.capitalizedMsg
            val code = "lib:$declarationType:[$memberName]"
            val msg = "$kindMsg '$memberName' is"
            C_FeatureRestrictions.error(msgCtx, pos, since, version, code toCodeMsg msg)
        }
    }

    private fun deprecatedMessage(msgCtx: C_MessageContext, pos: S_Pos, nameMsg: String, deprecated: C_Deprecated) {
        val typeStr = declarationType.msg.capitalizeEx()
        val depCode = deprecated.detailsCode()
        val depStr = deprecated.detailsMessage()
        val code = "deprecated:$declarationType:[$nameMsg]$depCode"
        val msg = "$typeStr '$nameMsg' is deprecated$depStr"

        val error = deprecated.error || msgCtx.globalCtx.compilerOptions.deprecatedError
        val msgType = if (error) C_MessageType.ERROR else C_MessageType.WARNING

        msgCtx.message(msgType, pos, code, msg)
    }
}
