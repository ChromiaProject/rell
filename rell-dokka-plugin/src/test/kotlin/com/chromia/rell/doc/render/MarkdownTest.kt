/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.chromia.rell.doc.model.*
import org.junit.jupiter.api.Test

class MarkdownTest {
    private fun mkSite(): Doc_Site = Doc_Site(
        title = "Test",
        footerMessage = "",
        modules = listOf(
            Doc_Module(
                name = "Test", slug = "test", docMd = "", system = false,
                packages = listOf(
                    Doc_Package(
                        qname = "main",
                        docMd = "",
                        defs = listOf(
                            Doc_Class(
                                name = "person", qname = "main.person", docMd = "",
                                source = null, deprecated = null, kind = Doc_ClassKind.ENTITY,
                                typeParams = emptyList(), superTypes = emptyList(), members = emptyList(),
                            )
                        ),
                    )
                )
            )
        ),
        customStyleSheets = emptyList(),
        customAssets = emptyList(),
        sourceLinks = emptyList(),
        hiddenPackages = emptySet(),
        system = false,
    )

    @Test
    fun `resolves bracket links to known qualified names`() {
        val site = mkSite()
        val index = SiteIndex.build(site)
        val md = Markdown(index)
        val ctx = PageContext(relativePath = "test/main/index.html", currentPackage = null)
        val html = md.renderHtml("See [main.person] for details.", ctx)
        assertThat(html).contains("href=")
        assertThat(html).contains(">main.person</a>")
    }

    @Test
    fun `leaves unknown bracket text untouched`() {
        val site = mkSite()
        val index = SiteIndex.build(site)
        val md = Markdown(index)
        val ctx = PageContext(relativePath = "test/main/index.html", currentPackage = null)
        val html = md.renderHtml("See [unknown.thing] for details.", ctx)
        assertThat(html).contains("[unknown.thing]")
        assertThat(html).doesNotContain("href=")
    }

    @Test
    fun `summary text strips markdown formatting`() {
        val site = mkSite()
        val md = Markdown(SiteIndex.build(site))
        val summary = md.renderSummaryText("**Bold** _italic_ and `code` with [link](url).")
        assertThat(summary).contains("Bold")
        assertThat(summary).doesNotContain("**")
        assertThat(summary).doesNotContain("`")
    }
}
