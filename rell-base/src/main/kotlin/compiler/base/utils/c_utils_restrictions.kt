/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_GlobalContext
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.model.R_LangVersion

/** [default] is the status if compatibility version is not set, which is the case when the compiler (not source)
 * version is not specified (allowed for source versions < 0.13.11) */
class C_FeatureSwitch(
    private val since: R_LangVersion,
    private val default: Boolean = true,
) {
    constructor(version: String, default: Boolean = true): this(R_LangVersion.of(version), default)

    fun isActive(version: R_LangVersion?) = isActive(since, version, default)
    fun isActive(compilerOptions: C_CompilerOptions) = isActive(compilerOptions.compatibility)
    fun isActive(globalCtx: C_GlobalContext) = isActive(globalCtx.compilerOptions)
    fun isActive(exprCtx: C_ExprContext) = isActive(exprCtx.globalCtx)

    companion object {
        fun isActive(since: R_LangVersion, version: R_LangVersion?, default: Boolean = true): Boolean {
            return if (version != null) version >= since else default
        }
    }
}

class C_FeatureRestrictions(
    private val since: R_LangVersion,
    private val codeMsg: C_CodeMsg,
    private val suppressor: (C_CompilerOptions) -> Boolean,
) {
    fun access(msgCtx: C_MessageContext, pos: S_Pos) {
        access(msgCtx, pos, "feature", msgCtx.globalCtx.compilerOptions)
    }

    fun access(msgMgr: C_MessageManager, pos: S_Pos, kind: String, compilerOptions: C_CompilerOptions) {
        val version = compilerOptions.compatibility
        if (version != null && version < since && !suppressor(compilerOptions)) {
            error(msgMgr, pos, since, version, "$kind:${codeMsg.code}" toCodeMsg codeMsg.msg)
        }
    }

    companion object {
        fun make(since: String, code: String, msg: String): C_FeatureRestrictions {
            return make(since, code toCodeMsg msg)
        }

        fun make(
            since: String,
            codeMsg: C_CodeMsg,
            suppressor: (C_CompilerOptions) -> Boolean = { false },
        ): C_FeatureRestrictions {
            val rSince = R_LangVersion.of(since)
            return C_FeatureRestrictions(rSince, codeMsg, suppressor)
        }

        fun error(
            msgMgr: C_MessageManager,
            pos: S_Pos,
            since: R_LangVersion,
            version: R_LangVersion,
            codeMsg: C_CodeMsg,
        ) {
            msgMgr.error(pos, "version:${codeMsg.code}:$since:$version",
                "${codeMsg.msg} supported since version $since (current version: $version)")
        }
    }
}
