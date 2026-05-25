/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.chromia.rell.doc.model.*
import org.junit.jupiter.api.Test

class SignatureRenderTest {
    private fun mkRender(): SignatureRender {
        val site = Doc_Site(
            title = "T", footerMessage = "", system = false,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module(
                    name = "T", slug = "t", docMd = "", system = false,
                    packages = listOf(Doc_Package(qname = "main", docMd = "", defs = emptyList())),
                )
            )
        )
        val typeRender = TypeRender(SiteIndex.build(site), PageContext("t/main/index.html", null))
        return SignatureRender(typeRender)
    }

    private fun fn(
        name: String = "f",
        kind: Doc_FunctionKind = Doc_FunctionKind.FUNCTION,
        params: List<Doc_Param> = emptyList(),
        returnType: Doc_Type? = Doc_Type.Named("integer", null),
        typeParams: List<Doc_TypeParam> = emptyList(),
        pure: Boolean = false,
        static: Boolean = false,
        extendable: Boolean = false,
        extendTargetQname: String? = null,
        mountName: String? = null,
    ) = Doc_Function(
        name = name, qname = "main.$name", docMd = "", source = null, deprecated = null,
        kind = kind, params = params, returnType = returnType, typeParams = typeParams,
        pure = pure, static = static, extendable = extendable, extendTargetQname = extendTargetQname,
        mountName = mountName,
    )

    @Test
    fun `mount name renders as @mount prefix`() {
        val out = mkRender().render(fn(mountName = "my.mount"))
        assertThat(out).contains("@mount")
        assertThat(out).contains("&quot;my.mount&quot;")
    }

    @Test
    fun `extendable keyword precedes function`() {
        val out = mkRender().render(fn(extendable = true))
        assertThat(out).contains("@extendable")
    }

    @Test
    fun `extend with target qname is rendered`() {
        val out = mkRender().render(fn(extendTargetQname = "other.target"))
        assertThat(out).contains("@extend")
        assertThat(out).contains("other.target")
    }

    @Test
    fun `pure and static modifiers appear`() {
        val out = mkRender().render(fn(pure = true, static = true))
        assertThat(out).contains("pure")
        assertThat(out).contains("static")
    }

    @Test
    fun `type params surround signature with lt gt`() {
        val out = mkRender().render(fn(typeParams = listOf(Doc_TypeParam("A"), Doc_TypeParam("B"))))
        assertThat(out).contains("&lt;")
        assertThat(out).contains("A")
        assertThat(out).contains("B")
        assertThat(out).contains("&gt;")
    }

    @Test
    fun `operation has no return type suffix`() {
        val out = mkRender().render(fn(kind = Doc_FunctionKind.OPERATION, returnType = Doc_Type.Named("integer", null)))
        assertThat(out).doesNotContain(": <a")
        assertThat(out).doesNotContain(": <span class=\"type-name\">integer")
    }

    @Test
    fun `constructor and special constructor suppress return type`() {
        val r = mkRender()
        for (k in listOf(Doc_FunctionKind.CONSTRUCTOR, Doc_FunctionKind.SPECIAL_CONSTRUCTOR)) {
            val out = r.render(fn(kind = k, returnType = Doc_Type.Named("text", null)))
            assertThat(out).doesNotContain(": <span class=\"type-name\">text")
        }
    }

    @Test
    fun `param zeroOne wraps with brackets and vararg adds dots`() {
        val out = mkRender().render(
            fn(
                params = listOf(
                    Doc_Param(name = "x", type = Doc_Type.Named("integer", null), docMd = "", zeroOne = true),
                    Doc_Param(name = "rest", type = Doc_Type.Named("text", null), docMd = "", vararg = true),
                )
            )
        )
        // ZeroOne wraps the param in [ ] (rendered as sig-punct spans).
        assertThat(out).contains("sig-punct")
        assertThat(out).contains("[")
        assertThat(out).contains("]")
        assertThat(out).contains("...")
    }

    @Test
    fun `property with default value text renders sig-lit`() {
        val prop = Doc_Property(
            name = "MAX", qname = "main.MAX", docMd = "", source = null, deprecated = null,
            type = Doc_Type.Named("integer", null), defaultValueText = "42",
        )
        val out = mkRender().render(prop)
        assertThat(out).contains("val")
        assertThat(out).contains("sig-lit")
        assertThat(out).contains("42")
    }

    @Test
    fun `mutable property keyword is var`() {
        val prop = Doc_Property(
            name = "counter", qname = "main.counter", docMd = "", source = null, deprecated = null,
            type = Doc_Type.Named("integer", null), mutable = true,
        )
        val out = mkRender().render(prop)
        assertThat(out).contains("var")
    }

    @Test
    fun `class with hidden abstract type params and supertypes renders all`() {
        val cls = Doc_Class(
            name = "C", qname = "main.C", docMd = "", source = null, deprecated = null,
            kind = Doc_ClassKind.TYPE,
            typeParams = listOf(Doc_TypeParam("T"), Doc_TypeParam("U")),
            superTypes = listOf(Doc_Type.Named("Base", null), Doc_Type.Named("Other", null)),
            members = emptyList(),
            hidden = true, abstract = true,
        )
        val out = mkRender().render(cls)
        assertThat(out).contains("@hidden")
        assertThat(out).contains("@abstract")
        assertThat(out).contains("type")
        assertThat(out).contains("T")
        assertThat(out).contains("U")
        assertThat(out).contains("Base")
        assertThat(out).contains("Other")
    }

    @Test
    fun `alias renders alias keyword with target`() {
        val alias = Doc_TypeAlias(
            name = "T", qname = "main.T", docMd = "", source = null, deprecated = null,
            targetQname = "main.user",
        )
        val out = mkRender().render(alias)
        assertThat(out).contains("alias")
        assertThat(out).contains("main.user")
    }
}
