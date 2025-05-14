/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.modifier

import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.toImmList
import org.apache.commons.lang3.StringUtils

object C_Annotation_Mount {
    val FIELD = C_ModifierField.valueAnnotation("mount", Evaluator)

    private object Evaluator: C_ModifierEvaluator<C_RawMountAnnotationValue>() {
        override fun evaluate(ctx: C_ModifierContext, modLink: C_ModifierLink, args: List<C_AnnotationArg>): C_RawMountAnnotationValue? {
            val mountPath = processArgs(ctx, modLink, args)
            mountPath ?: return null
            return C_RawMountAnnotationValue(modLink, mountPath)
        }

        private fun processArgs(ctx: C_ModifierContext, modLink: C_ModifierLink, args: List<C_AnnotationArg>): C_MountPath? {
            val str = C_AnnUtils.checkArgsOneString(ctx, modLink.name, args)
            if (str == null) {
                return null
            }

            val res = C_MountPath.parse(str)
            if (res == null) {
                ctx.msgCtx.error(modLink.pos, "ann:mount:invalid:$str", "Invalid mount name: '$str'")
            }

            return res
        }
    }
}

class C_MountAnnotationTarget(modLink: C_ModifierLink, val emptyMountAllowed: Boolean) {
    val pos = modLink.pos
    val type = modLink.target.type
    val name = modLink.target.name?.rName
}

class C_RawMountAnnotationValue(
        private val modLink: C_ModifierLink,
        private val path: C_MountPath
) {
    fun process(emptyMountAllowed: Boolean): C_MountAnnotationValue {
        val target = C_MountAnnotationTarget(modLink, emptyMountAllowed)
        return C_MountAnnotationValue(target, path)
    }
}

class C_MountAnnotationValue(
        private val target: C_MountAnnotationTarget,
        private val path: C_MountPath
) {
    fun calculateMountName(msgCtx: C_MessageContext, parentMountName: R_MountName): R_MountName {
        val mountName = path.apply(msgCtx, target, parentMountName)
        mountName ?: return parentMountName

        if (!target.emptyMountAllowed && mountName.isEmpty()) {
            msgCtx.error(target.pos, "ann:mount:empty:${target.type}",
                    "Cannot use empty mount name for ${target.type.description}")
            return parentMountName
        }

        return mountName
    }
}

class C_MountPath private constructor(
        private val str: String,
        private val up: Int?,
        private val path: List<R_Name>,
        private val tail: Boolean
) {
    fun apply(
            msgCtx: C_MessageContext,
            target: C_MountAnnotationTarget,
            parentMountName: R_MountName
    ): R_MountName? {
        var base = listOf<R_Name>()
        if (up != null) {
            if (up > parentMountName.parts.size) {
                msgCtx.error(target.pos, "ann:mount:up:${parentMountName.parts.size}:$up",
                        "Cannot go up by $up on current mount path '$parentMountName'")
                return null
            }
            base = parentMountName.parts.subList(0, parentMountName.parts.size - up)
        }

        if (!target.emptyMountAllowed && up != null && path.isEmpty() && !tail) {
            msgCtx.error(target.pos, "ann:mount:invalid:$str:${target.type}",
                    "Mount path '$str' is invalid for ${target.type.description}")
            return null
        }

        val combined = (base + path).toImmList()
        if (!tail) {
            return R_MountName(combined)
        }

        if (target.name == null) {
            msgCtx.error(target.pos, "ann:mount:tail:no_name:$str:${target.type}",
                    "Mount path '$str' is invalid for ${target.type.description}")
            return null
        }

        val full = (combined + target.name).toImmList()
        return R_MountName(full)
    }

    override fun toString() = str

    companion object {
        fun parse(s: String): C_MountPath? {
            var parts = StringUtils.splitPreserveAllTokens(s, '.').toList()
            if (parts.isEmpty()) return C_MountPath(s, null, listOf(), false)

            var up: Int? = null
            if (parts[0] == "") {
                up = 0
                parts = parts.subList(1, parts.size)
            } else if (parts[0].matches(Regex("\\^+"))) {
                up = parts[0].length
                parts = parts.subList(1, parts.size)
            }

            var tail = false
            if (parts.isNotEmpty() && parts[parts.size - 1] == "") {
                tail = true
                parts = parts.subList(0, parts.size - 1)
            }

            val path = mutableListOf<R_Name>()
            for (part in parts) {
                val rName = R_Name.ofOpt(part)
                if (rName == null) return null
                path.add(rName)
            }

            if (up != null && up == 0 && path.isEmpty()) {
                return null
            }

            return C_MountPath(s, up, path, tail)
        }
    }
}
