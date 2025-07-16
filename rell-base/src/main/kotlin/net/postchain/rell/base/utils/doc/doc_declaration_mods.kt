/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

sealed class DocModifier {
    internal abstract fun genCode(b: DocCode.Builder)

    companion object {
        val PURE: DocModifier = DocModifier_Keyword("pure")
        val STATIC: DocModifier = DocModifier_Keyword("static")

        val DEPRECATED: DocModifier = DocModifier_Annotation(R_Name.of("deprecated"), immListOf())

        private val DEPRECATED_ERROR: DocModifier = DocModifier_Annotation(
            R_Name.of("deprecated"),
            immListOf(DocAnnotationArg.makeRaw("ERROR")),
        )

        fun deprecated(error: Boolean): DocModifier = if (error) DEPRECATED_ERROR else DEPRECATED
    }
}

internal class DocModifier_Keyword(private val kw: String): DocModifier() {
    override fun genCode(b: DocCode.Builder) {
        b.keyword(kw)
        b.raw(" ")
    }
}

internal sealed class DocAnnotationArg {
    abstract fun genCode(b: DocCode.Builder)

    companion object {
        fun makeRaw(s: String): DocAnnotationArg = DocAnnotationArg_Raw(s)
        fun makeName(qualifiedName: R_QualifiedName): DocAnnotationArg = DocAnnotationArg_Name(qualifiedName)
        fun makeValue(value: DocValue?): DocAnnotationArg = DocAnnotationArg_Value(value)
    }
}

private class DocAnnotationArg_Raw(private val s: String): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(s)
    }
}

private class DocAnnotationArg_Name(private val qualifiedName: R_QualifiedName): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        b.link(qualifiedName.str())
    }
}

private class DocAnnotationArg_Value(private val value: DocValue?): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        if (value == null) {
            DocExpr.UNKNOWN.genCode(b)
        } else {
            value.genCode(b)
        }
    }
}

internal class DocModifier_Annotation(
    private val simpleName: R_Name,
    private val args: ImmList<DocAnnotationArg>,
): DocModifier() {
    override fun genCode(b: DocCode.Builder) {
        b.raw("@")
        b.raw(simpleName.str)

        if (args.isNotEmpty()) {
            b.raw("(")
            for ((i, arg) in args.withIndex()) {
                if (i > 0) b.sep(", ")
                arg.genCode(b)
            }
            b.raw(")")
        }

        b.newline()
    }
}

class DocModifiers private constructor(
    private val modifiers: ImmList<DocModifier>,
) {
    fun isDeprecated(): Boolean = DocModifier.DEPRECATED in modifiers

    internal fun appendTo(b: DocCode.Builder) {
        for (mod in modifiers) {
            mod.genCode(b)
        }
    }

    companion object {
        val NONE: DocModifiers = DocModifiers(immListOf())

        fun make(vararg modifiers: DocModifier?): DocModifiers {
            val list = modifiers.filterNotNull().toImmList()
            return make(list)
        }

        fun make(modifiers: ImmList<DocModifier>): DocModifiers {
            return if (modifiers.isEmpty()) NONE else DocModifiers(modifiers)
        }
    }
}
