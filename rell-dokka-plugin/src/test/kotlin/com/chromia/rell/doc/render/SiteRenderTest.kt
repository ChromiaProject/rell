/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.chromia.rell.doc.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SiteRenderTest {

    @TempDir lateinit var tempDir: Path

    private fun render(site: Doc_Site, outName: String = "out"): Path {
        val outDir = tempDir.resolve(outName)
        SiteRender(outDir).render(site)
        return outDir
    }

    private fun cls(name: String) = Doc_Class(
        name = name, qname = "main.$name", docMd = "", source = null, deprecated = null,
        kind = Doc_ClassKind.ENTITY, typeParams = emptyList(), superTypes = emptyList(),
        members = emptyList(),
    )

    @Test
    fun `custom style sheets and assets are copied`() {
        val styles = (tempDir / "user.css").apply { writeText("body{}") }
        val asset = (tempDir / "logo.png").apply { writeText("png") }
        val site = Doc_Site(
            title = "T", footerMessage = "", system = false,
            customStyleSheets = listOf(styles), customAssets = listOf(asset),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module(
                    name = "T", slug = "t", docMd = "", system = false,
                    packages = listOf(Doc_Package("main", "", listOf(cls("user")))),
                )
            )
        )
        val out = render(site)
        assertThat(out.resolve("styles/user.css")).exists()
        assertThat(out.resolve("images/logo.png")).exists()
        assertThat(out.resolve("styles/site.css")).exists()
    }

    @Test
    fun `non-existent custom assets are silently skipped`() {
        val site = Doc_Site(
            title = "T", footerMessage = "", system = false,
            customStyleSheets = listOf(tempDir / "missing.css"),
            customAssets = listOf(tempDir / "missing.png"),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module("T", "t", "", listOf(Doc_Package("main", "", emptyList())), system = false)
            )
        )
        render(site)
    }

    @Test
    fun `multi-module site renders one section per module`() {
        val site = Doc_Site(
            title = "Multi", footerMessage = "", system = false,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module("M1", "m1", docMd = "First module **doc**.", system = false,
                    packages = listOf(Doc_Package("p1", docMd = "Pkg doc", defs = listOf(cls("a"))))),
                Doc_Module("M2", "m2", docMd = "", system = false,
                    packages = listOf(Doc_Package("p2", docMd = "", defs = listOf(cls("b"))))),
            )
        )
        val out = render(site)
        val index = (out / "index.html").readText()
        assertThat(index).contains("M1")
        assertThat(index).contains("M2")
        assertThat(index).contains("First module")
        assertThat(out.resolve("m1/index.html")).exists()
        assertThat(out.resolve("m2/index.html")).exists()
    }

    @Test
    fun `empty site emits index but no module dirs`() {
        val site = Doc_Site(
            title = "E", footerMessage = "", system = false,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = emptyList(),
        )
        val out = render(site)
        assertThat(out / "index.html").exists()
    }

    @Test
    fun `system module index uses Namespaces label and SYSTEM LIBRARY kicker`() {
        val site = Doc_Site(
            title = "Sys", footerMessage = "", system = true,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module("SysMod", "sys-mod", docMd = "Top-level docs", system = true,
                    packages = listOf(Doc_Package("crypto", docMd = "Crypto pkg", defs = listOf(cls("hash"))))),
            )
        )
        val out = render(site)
        val moduleIndex = out.resolve("sys-mod/index.html").readText()
        assertThat(moduleIndex).contains("Namespaces")
        assertThat(moduleIndex).contains("SYSTEM LIBRARY")
        assertThat(moduleIndex).contains("Top-level docs")
    }

    @Test
    fun `package and def docMd flow into rendered HTML`() {
        val function = Doc_Function(
            name = "f", qname = "main.f", docMd = "Function body **doc**.",
            source = null, deprecated = null, kind = Doc_FunctionKind.FUNCTION,
            params = emptyList(), returnType = Doc_Type.Named("integer", null), typeParams = emptyList(),
        )
        val prop = Doc_Property(
            name = "C", qname = "main.C", docMd = "", source = null, deprecated = null,
            type = Doc_Type.Named("integer", null), defaultValueText = "1",
        )
        val site = Doc_Site(
            title = "Test", footerMessage = "Foot", system = false,
            customStyleSheets = emptyList(), customAssets = emptyList(),
            sourceLinks = emptyList(), hiddenPackages = emptySet(),
            modules = listOf(
                Doc_Module("Test", "test", docMd = "Module-level doc.", system = false,
                    packages = listOf(Doc_Package("main", docMd = "Package summary.", defs = listOf(function, prop))))
            )
        )
        val out = render(site)
        val pkgIndex = out.resolve("test/main/index.html").readText()
        assertThat(pkgIndex).contains("Package summary")
        val fHtml = out.resolve("test/main/f.html").readText()
        assertThat(fHtml).contains("Function body")
        assertThat(fHtml).contains("Foot")
    }

    @Test
    fun `source link resolves absolute path under localDirectory and relative paths`() {
        val link = Doc_SourceLink(
            localDirectory = tempDir / "src",
            remoteUrl = URI("https://example.com/r").toURL(),
            remoteLineSuffix = "#L",
        )
        link.localDirectory.createDirectories()
        val absFile = link.localDirectory.resolve("a/b.rell").apply {
            parent.createDirectories()
            writeText("")
        }
        assertThat(resolveSourceLink(Doc_Source(absFile.toString(), 7), listOf(link)))
            .isEqualTo("https://example.com/r/a/b.rell#L7")
        assertThat(resolveSourceLink(Doc_Source("rel/c.rell", 1), listOf(link)))
            .isEqualTo("https://example.com/r/rel/c.rell#L1")
    }

    @Test
    fun `source link without line suffix omits anchor`() {
        val link = Doc_SourceLink(
            localDirectory = tempDir / "src2",
            remoteUrl = URI("https://example.com/r/").toURL(),
            remoteLineSuffix = null,
        )
        assertThat(resolveSourceLink(Doc_Source("x.rell", 99), listOf(link)))
            .isEqualTo("https://example.com/r/x.rell")
    }

    @Test
    fun `source link without matching prefix returns null`() {
        val link = Doc_SourceLink(
            localDirectory = tempDir / "nowhere",
            remoteUrl = URI("https://example.com/r").toURL(),
            remoteLineSuffix = "#L",
        )
        link.localDirectory.createDirectories()
        val outside = tempDir.resolve("other/y.rell").apply {
            parent.createDirectories()
            writeText("")
        }
        assertThat(resolveSourceLink(Doc_Source(outside.toString(), null), listOf(link))).isNull()
    }

    @Test
    fun `empty source links returns null`() {
        assertThat(resolveSourceLink(Doc_Source("x.rell", 1), emptyList())).isNull()
    }
}
