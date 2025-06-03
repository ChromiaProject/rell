/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.modifier

import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.module.C_ExtChainName

object C_Annotation_External {
    val FIELD_EXTERNAL_MODULE = C_ModifierField.flagAnnotation("external")
    val FIELD_EXTERNAL_CHAIN = C_ModifierField.valueAnnotation("external", Evaluator_ExternalChain)

    private object Evaluator_ExternalChain: C_ModifierEvaluator<C_ExtChainName>() {
        override fun evaluate(ctx: C_ModifierContext, modLink: C_ModifierLink, args: List<C_AnnotationArg>): C_ExtChainName? {
            val chain = processArgs(ctx, modLink.name, args)
            return if (chain == null) null else C_ExtChainName(chain)
        }

        private fun processArgs(ctx: C_ModifierContext, name: C_Name, args: List<C_AnnotationArg>): String? {
            val str = C_AnnUtils.checkArgsOneString(ctx, name, args)
            if (str != null && str.isEmpty()) {
                ctx.msgCtx.error(name.pos, "ann:external:invalid:$str", "Invalid external chain name: '$str'")
                return null
            }
            return str
        }
    }
}
