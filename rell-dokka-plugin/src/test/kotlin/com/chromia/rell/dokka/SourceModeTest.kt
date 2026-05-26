/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.exists
import assertk.assertions.isTrue
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.toPath

/**
 * End-to-end test that drives the generator against the `my-rell-dapp` resource fixture and
 * asserts the file tree + key declarations land where expected.
 */
class SourceModeTest {
    private lateinit var projectRoot: Path

    @TempDir lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        projectRoot = this.javaClass.classLoader.getResource("my-rell-dapp")!!.toURI().toPath()
    }

    private fun generate(
        title: String = "model",
        entryPointModules: List<String> = listOf("main"),
        additionalModules: List<String> = emptyList(),
        filteredModules: List<String> = emptyList(),
    ): Path {
        val target = tempDir / "out"
        val builder = RellDokkaPluginConfigurationBuilder(
            title = title,
            modules = entryPointModules,
            projectRoot = (projectRoot / "src").toFile(),
        )
            .targetFolder(target.toFile())
            .customStyleSheets(emptyList())
            .customAssets(emptyList())
            .footerMessage("Footer Message")
            .includes(emptyList())
            .filteredModules(filteredModules)
            .additionalModules(additionalModules)
        RellDokkaGenerator(builder).generate()
        return target
    }

    @Test
    fun `index html exists and references included modules`() {
        val out = generate(entryPointModules = listOf("main"))
        val indexHtml = out / "index.html"
        assertThat(indexHtml).exists()
        val content = indexHtml.readText()
        assertThat(content).contains("main")
    }

    @Test
    fun `definitions from entry point and additional modules are reachable`() {
        val out = generate(
            entryPointModules = listOf("main"),
            additionalModules = listOf("lib.lib1"),
        )
        // model/<modulePackage>/<def>.html for top-level defs
        assertThat(out / "model/main/person/index.html").exists()
        assertThat(out / "model/main/set_name.html").exists()
        assertThat(out, "model/main/hello_world.html").exists()
        assertThat(out, "model/main/my_name/index.html").exists()
        assertThat(out, "model/lib.lib1/color/index.html").exists()
        assertThat(out, "model/lib.lib1/user/index.html").exists()
        assertThat(out, "model/lib.lib1/get_message.html").exists()
        assertThat(out, "model/lib.lib1/create_user.html").exists()
        assertThat(out, "model/lib.lib1/get_all_users.html").exists()
        assertThat(out, "model/lib.lib1.nested/product/index.html").exists()
        assertThat(out, "model/lib.lib1.nested/get_product_info.html").exists()
    }

    @Test
    fun `navigation html lists the included modules`() {
        val out = generate(
            entryPointModules = listOf("main"),
            additionalModules = listOf("lib.lib1"),
        )
        val nav = (out / "navigation.html").readText()
        assertThat(nav).contains("anchor-label=\"main\"")
        assertThat(nav).contains("anchor-label=\"lib.lib1\"")
        assertThat(nav).contains("anchor-label=\"lib.lib1.nested\"")
    }

    @Test
    fun `filtered modules removed from navigation`() {
        val out = generate(
            entryPointModules = listOf("main"),
            additionalModules = listOf("lib.lib1"),
            filteredModules = listOf("main"),
        )
        val nav = (out / "navigation.html").readText()
        // `main` was filtered out by the user; only `lib.lib1*` remain reachable in the nav.
        assertThat(nav.contains("anchor-label=\"lib.lib1\"")).isTrue()
    }

    @Test
    fun `footer message reaches the index page`() {
        val out = generate(entryPointModules = listOf("main"))
        val indexHtml = (out / "index.html").readText()
        assertThat(indexHtml).contains("Footer Message")
    }

    @Test
    fun `search index contains only project symbols, not stdlib`() {
        val out = generate(
            entryPointModules = listOf("main"),
            additionalModules = listOf("lib.lib1"),
        )
        val pages = (out / "scripts/pages.json").readText()

        // Project symbols are present.
        assertThat(pages).contains("\"main.person\"")
        assertThat(pages).contains("\"main.set_name\"")
        assertThat(pages).contains("\"main.hello_world\"")
        assertThat(pages).contains("\"lib.lib1.user\"")
        assertThat(pages).contains("\"lib.lib1.color\"")
        assertThat(pages).contains("\"lib.lib1.nested.product\"")

        // Stdlib must not leak into a project's index. Sample a few well-known stdlib qnames that
        // would appear if `app.modules` ever started shipping the system library through the
        // source-mode walk.
        for (sym in listOf("\"integer\"", "\"byte_array\"", "\"crypto\"", "\"op_context\"", "\"chain_context\"")) {
            assertThat(pages, name = "pages.json").doesNotContain(sym)
        }
    }

    @Test
    fun `extendable function is documented and its concrete extension links back`() {
        val out = generate(
            entryPointModules = listOf("ext_demo"),
        )
        // The @extendable function is documented on its own page.
        val onEventPage = out / "model/ext_demo/on_event.html"
        assertThat(onEventPage).exists()
        val onEventHtml = onEventPage.readText()
        assertThat(onEventHtml).contains("@extendable")

        // The concrete @extend(on_event) function is also documented, with the @extend link.
        val loggerPage = out / "model/ext_demo/logger.html"
        assertThat(loggerPage).exists()
        val loggerHtml = loggerPage.readText()
        assertThat(loggerHtml).contains("@extend")
        assertThat(loggerHtml).contains("ext_demo.on_event")
    }
}
