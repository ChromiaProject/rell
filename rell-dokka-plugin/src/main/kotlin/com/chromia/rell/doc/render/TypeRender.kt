/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.Doc_Type

/**
 * Renders a `Doc_Type` to a pre-escaped HTML fragment.
 *
 * Each linkable named type becomes an `<a class="type-link" href="…">`; non-linkable bits stay
 * inside `<span class="type-name">` (kept as a class so themes can tint them). The output is
 * inserted into pages with `unsafe { +html }`, which is why the renderer escapes everything it
 * doesn't emit as a tag.
 */
internal class TypeRender(private val index: SiteIndex, private val pageCtx: PageContext) {
    fun renderHtml(type: Doc_Type): String = buildString { this@TypeRender.append(this, type) }

    private fun append(out: Appendable, type: Doc_Type) {
        when (type) {
            is Doc_Type.Named -> appendNamed(out, type)
            is Doc_Type.Nullable -> {
                append(out, type.inner)
                out.append("?")
            }
            is Doc_Type.Tuple -> {
                out.append("(")
                for ((i, field) in type.fields.withIndex()) {
                    if (i > 0) out.append(", ")
                    if (field.name != null) {
                        out.append(escapeHtml(field.name)).append(": ")
                    }
                    append(out, field.type)
                }
                if (type.fields.size == 1 && type.fields[0].name == null) out.append(",")
                out.append(")")
            }
            is Doc_Type.Function -> {
                out.append("(")
                for ((i, p) in type.params.withIndex()) {
                    if (i > 0) out.append(", ")
                    append(out, p)
                }
                out.append(") -&gt; ")
                append(out, type.result)
            }
            is Doc_Type.TypeParam -> out.append("<span class=\"sig-tp\">").append(escapeHtml(type.text)).append("</span>")
            is Doc_Type.Raw -> out.append(escapeHtml(type.text))
        }
    }

    private fun appendNamed(out: Appendable, type: Doc_Type.Named) {
        val display = type.text.substringAfterLast('.')
        val displayHtml = escapeHtml(display)
        val href = type.qname?.let { qname ->
            index.resolve(qname)?.let { Hrefs.relativeFrom(pageCtx.relativePath, it.href()) }
        }
        if (href != null) {
            out.append("<a class=\"type-link\" href=\"").append(escapeAttr(href)).append("\">")
                .append(displayHtml).append("</a>")
        } else {
            out.append("<span class=\"type-name\">").append(displayHtml).append("</span>")
        }
        if (type.args.isNotEmpty()) {
            out.append("&lt;")
            type.args.forEachIndexed { i, arg ->
                if (i > 0) out.append(", ")
                appendArg(out, arg)
            }
            out.append("&gt;")
        }
    }

    private fun appendArg(out: Appendable, arg: Doc_Type.Arg) {
        when (arg) {
            is Doc_Type.Arg.Invariant -> append(out, arg.type)
            is Doc_Type.Arg.SubOf -> { out.append("-"); append(out, arg.type) }
            is Doc_Type.Arg.SuperOf -> { out.append("+"); append(out, arg.type) }
            Doc_Type.Arg.Star -> out.append("*")
        }
    }
}

internal fun escapeHtml(s: String): String = buildString(s.length) {
    for (c in s) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}

internal fun escapeAttr(s: String): String = escapeHtml(s)
