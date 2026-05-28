/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.*

/**
 * Builds the signature HTML fragment shown at the top of each detail page. Output is a single
 * pre-escaped HTML string the caller injects into `<code>` with `unsafe { +html }`.
 *
 * Signature shape mirrors Rell source:
 *  - `function name(params): return-type`
 *  - `operation name(params)` (no return-type)
 *  - `query name(params): return-type`
 *  - `@extendable function …`, `@extend(target) function …`
 *  - `entity Name`, `object Name`, `struct Name`, `enum Name`, `type Name<T> : super`
 *  - `val NAME: integer = 123`
 */
internal class SignatureRender(private val typeRender: TypeRender) {
    fun render(def: Doc_Def): String = when (def) {
        is Doc_Function -> renderFunction(def)
        is Doc_Property -> renderProperty(def)
        is Doc_Class -> renderClass(def)
        is Doc_TypeAlias -> renderAlias(def)
    }

    private fun renderFunction(f: Doc_Function): String = buildString {
        if (f.mountName != null) {
            append(kw("@mount")).append("(")
            append("<span class=\"sig-str\">&quot;").append(escapeHtml(f.mountName)).append("&quot;</span>")
            append(")\n")
        }
        if (f.extendable) append(kw("@extendable")).append(' ')
        if (f.extendTargetQname != null) {
            append(kw("@extend")).append("(")
                .append("<span class=\"sig-ref\">").append(escapeHtml(f.extendTargetQname)).append("</span>")
                .append(") ")
        }
        // `pure` and `static` are stdlib-internal modifiers, not Rell user syntax — render as
        // metadata chips, matching the treatment for hidden/abstract/type on classes.
        val metas = buildList {
            if (f.pure) add("pure")
            if (f.static) add("static")
        }
        if (metas.isNotEmpty()) {
            append("<span class=\"sig-meta-row\">")
            for (m in metas) append("<span class=\"sig-meta\">").append(escapeHtml(m)).append("</span>")
            append("</span>")
            append(' ')
        }
        append(kw(f.kind.keyword)).append(' ')
        append("<span class=\"sig-name\">").append(escapeHtml(f.name)).append("</span>")
        if (f.typeParams.isNotEmpty()) {
            append("&lt;")
            f.typeParams.forEachIndexed { i, tp ->
                if (i > 0) append(", ")
                append(tparam(tp.name))
            }
            append("&gt;")
        }
        append("(")
        f.params.forEachIndexed { i, p ->
            if (i > 0) append(", ")
            append(renderParam(p))
        }
        append(")")
        if (f.returnType != null && f.kind != Doc_FunctionKind.OPERATION
            && f.kind != Doc_FunctionKind.CONSTRUCTOR
            && f.kind != Doc_FunctionKind.SPECIAL_CONSTRUCTOR) {
            append(": ")
            append(typeRender.renderHtml(f.returnType))
        }
    }

    private fun renderParam(p: Doc_Param): String = buildString {
        if (p.zeroOne) append(punct("["))
        append("<span class=\"sig-param\">").append(escapeHtml(p.name)).append("</span>")
        append(": ")
        append(typeRender.renderHtml(p.type))
        if (p.vararg) append("...")
        if (p.zeroOne) append(punct("]"))
    }

    private fun renderProperty(p: Doc_Property): String = buildString {
        append(kw(if (p.mutable) "var" else "val")).append(' ')
        append("<span class=\"sig-name\">").append(escapeHtml(p.name)).append("</span>")
        append(": ")
        append(typeRender.renderHtml(p.type))
        if (p.defaultValueText != null) {
            append(" = ").append("<span class=\"sig-lit\">").append(escapeHtml(p.defaultValueText)).append("</span>")
        }
    }

    private fun renderClass(c: Doc_Class): String = buildString {
        // Pseudo-keywords (`hidden`, `abstract`, `type`) are compiler-internal markers, not part
        // of Rell user syntax. Render them as small uppercase chips so a reader doesn't mistake
        // them for actual source. The kind keyword stays inline as a real keyword for the kinds
        // that *are* Rell syntax (entity / object / struct / enum); `type` joins the chip row.
        val metas = buildList {
            if (c.hidden) add("hidden")
            if (c.abstract) add("abstract")
            if (c.kind == Doc_ClassKind.TYPE) add("type")
        }
        if (metas.isNotEmpty()) {
            append("<span class=\"sig-meta-row\">")
            for (m in metas) append("<span class=\"sig-meta\">").append(escapeHtml(m)).append("</span>")
            append("</span>")
            append(' ')
        }
        if (c.kind != Doc_ClassKind.TYPE) append(kw(c.kind.keyword)).append(' ')
        append("<span class=\"sig-name\">").append(escapeHtml(c.name)).append("</span>")
        if (c.typeParams.isNotEmpty()) {
            append("&lt;")
            c.typeParams.forEachIndexed { i, tp ->
                if (i > 0) append(", ")
                append(tparam(tp.name))
            }
            append("&gt;")
        }
        if (c.superTypes.isNotEmpty()) {
            append(": ")
            c.superTypes.forEachIndexed { i, st ->
                if (i > 0) append(", ")
                append(typeRender.renderHtml(st))
            }
        }
    }

    /** The opening punctuation of a block, e.g. ` {`. Exposed so the struct-declaration renderer
     * in Pages can build the header line in the same token style as the signature. */
    fun openBrace(): String = " " + punct("{")

    fun closeBrace(): String = punct("}")

    /**
     * Inline `name: type` form for one attribute, with the Rell field modifiers (`mutable`, `key`,
     * `index`) as leading keywords and any default value. Used by the struct-declaration body and by
     * the attribute listing on class pages.
     */
    fun renderAttribute(p: Doc_Property): String = buildString {
        if (p.mutable) append(kw("mutable")).append(' ')
        if (p.key) append(kw("key")).append(' ')
        if (p.index) append(kw("index")).append(' ')
        append("<span class=\"sig-param\">").append(escapeHtml(p.name)).append("</span>")
        append(": ")
        append(typeRender.renderHtml(p.type))
        if (p.defaultValueText != null) {
            append(" = ").append("<span class=\"sig-lit\">").append(escapeHtml(p.defaultValueText)).append("</span>")
        }
    }

    private fun renderAlias(a: Doc_TypeAlias): String = buildString {
        append(kw("alias")).append(' ')
        append("<span class=\"sig-name\">").append(escapeHtml(a.name)).append("</span>")
        append(" = ").append("<span class=\"sig-ref\">").append(escapeHtml(a.targetQname)).append("</span>")
    }

    private fun kw(s: String): String = "<span class=\"sig-kw\">${escapeHtml(s)}</span>"
    private fun tparam(s: String): String = "<span class=\"sig-tp\">${escapeHtml(s)}</span>"
    private fun punct(s: String): String = "<span class=\"sig-punct\">${escapeHtml(s)}</span>"
}
