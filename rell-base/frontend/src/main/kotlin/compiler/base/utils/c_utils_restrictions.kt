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

fun C_FeatureSwitch.isActive(globalCtx: C_GlobalContext) = isActive(globalCtx.compilerOptions)
fun C_FeatureSwitch.isActive(exprCtx: C_ExprContext) = isActive(exprCtx.globalCtx)

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
