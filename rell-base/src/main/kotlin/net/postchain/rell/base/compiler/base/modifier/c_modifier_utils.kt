/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.modifier

import net.postchain.rell.base.compiler.base.core.C_ComparablePos
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.lib.type.R_TextType

object C_AnnUtils {
    fun checkArgsNone(ctx: C_ModifierContext, name: C_Name, args: List<C_AnnotationArg>): Boolean {
        return if (args.isEmpty()) true else {
            ctx.msgCtx.error(name.pos, "ann:$name:args:${args.size}", "Annotation @$name takes no arguments")
            false
        }
    }

    fun checkArgsOne(ctx: C_ModifierContext, name: C_Name, args: List<C_AnnotationArg>): C_AnnotationArg? {
        val expectedArgs = 1
        return if (args.size == expectedArgs) args[0] else {
            ctx.msgCtx.error(name.pos, "ann:$name:arg_count:${args.size}",
                    "Wrong number of arguments (expected $expectedArgs)")
            null
        }
    }

    fun checkArgsOneString(ctx: C_ModifierContext, name: C_Name, args: List<C_AnnotationArg>): String? {
        val arg = checkArgsOne(ctx, name, args)
        arg ?: return null

        val value = arg.value(ctx)
        value ?: return null

        val type = value.type()
        if (type != R_TextType) {
            ctx.msgCtx.error(name.pos, "ann:$name:arg_type:${type.strCode()}",
                    "Wrong argument type: ${type.str()} instead of ${R_TextType.str()}")
            return null
        }

        val str = value.asString()
        return str
    }

    fun checkModsZeroOne(msgCtx: C_MessageContext, vararg values: C_ModifierValue<*>) {
        val links = values.mapNotNull { it.modLink() }
        if (links.size > 1) {
            errBadCombination(msgCtx, links)
        }
    }

    fun errBadCombination(msgCtx: C_MessageContext, links: List<C_ModifierLink>) {
        require(links.size > 1)
        val sorted = links.sortedBy { C_ComparablePos(it.pos) }
        val codeMsgs = sorted.map { it.key.codeMsg() }
        val listCode = codeMsgs.joinToString(",") { it.code }
        val code = "modifier:bad_combination:$listCode"
        val msg = "${codeMsgs[0].msg.capitalize()} and ${codeMsgs[1].msg} cannot be used at the same time"
        msgCtx.error(sorted[1].pos, code, msg)
    }
}
