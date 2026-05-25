/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.chromia.rell.doc.model.*
import org.junit.jupiter.api.Test

class TypeRenderTest {
    private fun mkRender(): TypeRender {
        val site = Doc_Site(
            title = "T", footerMessage = "", system = false,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module(
                    name = "T", slug = "t", docMd = "", system = false,
                    packages = listOf(
                        Doc_Package(
                            qname = "main", docMd = "",
                            defs = listOf(
                                Doc_Class(
                                    name = "user", qname = "main.user", docMd = "",
                                    source = null, deprecated = null, kind = Doc_ClassKind.ENTITY,
                                    typeParams = emptyList(), superTypes = emptyList(),
                                    members = emptyList(),
                                )
                            )
                        )
                    )
                )
            )
        )
        val ctx = PageContext(relativePath = "t/main/index.html", currentPackage = null)
        return TypeRender(SiteIndex.build(site), ctx)
    }

    @Test
    fun `Named with unknown qname falls back to span`() {
        val out = mkRender().renderHtml(Doc_Type.Named(text = "unknown_type", qname = "no.such.qname"))
        assertThat(out).contains("<span class=\"type-name\">unknown_type</span>")
        assertThat(out).doesNotContain("type-link")
    }

    @Test
    fun `Named with null qname is non-linkable`() {
        val out = mkRender().renderHtml(Doc_Type.Named(text = "anonymous", qname = null))
        assertThat(out).contains("<span class=\"type-name\">anonymous</span>")
    }

    @Test
    fun `Named with resolvable qname becomes anchor`() {
        val out = mkRender().renderHtml(Doc_Type.Named(text = "main.user", qname = "main.user"))
        assertThat(out).contains("<a class=\"type-link\"")
        assertThat(out).contains(">user</a>")
    }

    @Test
    fun `Named with generic args wraps lt gt`() {
        val out = mkRender().renderHtml(
            Doc_Type.Named(
                text = "list", qname = null,
                args = listOf(Doc_Type.Arg.Invariant(Doc_Type.Named("integer", null))),
            )
        )
        assertThat(out).contains("&lt;")
        assertThat(out).contains("integer")
        assertThat(out).contains("&gt;")
    }

    @Test
    fun `Arg variants render with prefixes`() {
        val r = mkRender()
        val inv = r.renderHtml(
            Doc_Type.Named(
                "map", null,
                args = listOf(
                    Doc_Type.Arg.Invariant(Doc_Type.Named("text", null)),
                    Doc_Type.Arg.SubOf(Doc_Type.Named("integer", null)),
                    Doc_Type.Arg.SuperOf(Doc_Type.Named("decimal", null)),
                    Doc_Type.Arg.Star,
                ),
            )
        )
        assertThat(inv).contains("text")
        assertThat(inv).contains("-")
        assertThat(inv).contains("+")
        assertThat(inv).contains("*")
    }

    @Test
    fun `Nullable appends question mark`() {
        val out = mkRender().renderHtml(Doc_Type.Nullable(Doc_Type.Named("text", null)))
        assertThat(out).contains("?")
    }

    @Test
    fun `Tuple with named fields prints name colon`() {
        val out = mkRender().renderHtml(
            Doc_Type.Tuple(
                listOf(
                    Doc_Type.Tuple.Field(name = "a", type = Doc_Type.Named("integer", null)),
                    Doc_Type.Tuple.Field(name = "b", type = Doc_Type.Named("text", null)),
                )
            )
        )
        assertThat(out).contains("a:")
        assertThat(out).contains("b:")
    }

    @Test
    fun `Single-field positional tuple keeps trailing comma`() {
        val out = mkRender().renderHtml(
            Doc_Type.Tuple(listOf(Doc_Type.Tuple.Field(name = null, type = Doc_Type.Named("integer", null))))
        )
        // (integer,) — the trailing comma disambiguates from grouping parentheses.
        assertThat(out).contains(",)")
    }

    @Test
    fun `Tuple field name is escaped`() {
        val out = mkRender().renderHtml(
            Doc_Type.Tuple(
                listOf(Doc_Type.Tuple.Field(name = "<a>", type = Doc_Type.Named("integer", null)))
            )
        )
        assertThat(out).contains("&lt;a&gt;")
    }

    @Test
    fun `Function with multiple params separates with comma`() {
        val out = mkRender().renderHtml(
            Doc_Type.Function(
                params = listOf(Doc_Type.Named("integer", null), Doc_Type.Named("text", null)),
                result = Doc_Type.Named("boolean", null),
            )
        )
        assertThat(out).contains("(")
        assertThat(out).contains(", ")
        assertThat(out).contains("-&gt;")
        assertThat(out).contains("boolean")
    }

    @Test
    fun `Zero-param function renders empty params`() {
        val out = mkRender().renderHtml(
            Doc_Type.Function(params = emptyList(), result = Doc_Type.Named("unit", null))
        )
        assertThat(out).contains("() -&gt;")
    }

    @Test
    fun `TypeParam is wrapped in sig-tp span`() {
        val out = mkRender().renderHtml(Doc_Type.TypeParam("T"))
        assertThat(out).contains("<span class=\"sig-tp\">T</span>")
    }

    @Test
    fun `Raw text is escaped`() {
        val out = mkRender().renderHtml(Doc_Type.Raw("a < b & c > \"q\" 'p'"))
        assertThat(out).contains("&lt;")
        assertThat(out).contains("&amp;")
        assertThat(out).contains("&gt;")
        assertThat(out).contains("&quot;")
        assertThat(out).contains("&#39;")
    }

    @Test
    fun `escapeHtml handles every special character`() {
        assertThat(escapeHtml("<&>\"'x")).isEqualTo("&lt;&amp;&gt;&quot;&#39;x")
    }

    @Test
    fun `escapeAttr matches escapeHtml`() {
        assertThat(escapeAttr("<\"x")).isEqualTo("&lt;&quot;x")
    }
}
