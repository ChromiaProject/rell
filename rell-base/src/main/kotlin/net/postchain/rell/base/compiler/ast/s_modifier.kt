/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_MountContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.core.C_NamespaceContext
import net.postchain.rell.base.compiler.base.modifier.*
import net.postchain.rell.base.compiler.parser.S_Keywords
import net.postchain.rell.base.utils.doc.DocModifier
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.toImmList

sealed class S_Modifier(val pos: S_Pos) {
    abstract fun compile(ctx: C_ModifierContext, modValues: C_FixedModifierValues): DocModifier
    open fun ideIsTestFile(): Boolean = false
}

class S_KeywordModifier(private val kw: C_Name, private val kind: S_KeywordModifierKind): S_Modifier(kw.pos) {
    override fun compile(ctx: C_ModifierContext, modValues: C_FixedModifierValues): DocModifier {
        return modValues.compileKeyword(ctx, kw, kind)
    }
}

enum class S_KeywordModifierKind(val kw: String) {
    ABSTRACT(S_Keywords.ABSTRACT),
    OVERRIDE(S_Keywords.OVERRIDE),
}

sealed class S_AnnotationArg {
    abstract fun compile(ctx: C_ModifierContext): C_AnnotationArg
}

class S_AnnotationArg_Value(val expr: S_LiteralExpr): S_AnnotationArg() {
    override fun compile(ctx: C_ModifierContext): C_AnnotationArg {
        val value = expr.value()
        return C_AnnotationArg_Value(expr.startPos, value)
    }
}

class S_AnnotationArg_Name(val name: S_QualifiedName): S_AnnotationArg() {
    override fun compile(ctx: C_ModifierContext): C_AnnotationArg {
        val nameHand = name.compile(ctx.symCtx)
        for (partHand in nameHand.parts) {
            partHand.setDefaultIdeInfo(C_IdeSymbolInfo.UNKNOWN)
        }
        return C_AnnotationArg_Name(nameHand)
    }
}

class S_Annotation(val name: S_Name, val args: List<S_AnnotationArg>): S_Modifier(name.pos) {
    override fun compile(ctx: C_ModifierContext, modValues: C_FixedModifierValues): DocModifier {
        val cArgs = args.map { it.compile(ctx) }
        return modValues.compileAnnotation(ctx, name, cArgs)
    }

    override fun ideIsTestFile(): Boolean {
        val rName = name.getRNameSpecial()
        return rName.str == C_Annotations.TEST
    }
}

class S_Modifiers(val modifiers: List<S_Modifier>) {
    val pos = modifiers.firstOrNull()?.pos

    fun compile(modifierCtx: C_ModifierContext, modValues: C_ModifierValues): DocModifiers {
        val fixModValues = modValues.fix()
        val docMods = mutableListOf<DocModifier>()

        for (modifier in modifiers) {
            val docMod = modifier.compile(modifierCtx, fixModValues)
            docMods.add(docMod)
        }

        return DocModifiers(docMods.toImmList())
    }

    fun compile(ctx: C_MountContext, modValues: C_ModifierValues): DocModifiers {
        return compile(ctx.nsCtx, modValues)
    }

    fun compile(ctx: C_NamespaceContext, modValues: C_ModifierValues): DocModifiers {
        val modifierCtx = C_ModifierContext(ctx.msgCtx, ctx.symCtx)
        return compile(modifierCtx, modValues)
    }
}
