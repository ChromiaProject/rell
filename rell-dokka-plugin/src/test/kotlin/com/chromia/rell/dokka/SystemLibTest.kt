/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * End-to-end test on the Rell system-library mode. Asserts that the generator produces a
 * directory tree with the expected packages and that known stdlib types/functions appear.
 */
class SystemLibTest {
    @TempDir
    lateinit var tempDir: Path

    private fun generate(): Path {
        val target = (tempDir / "out")
        val builder = RellDokkaPluginConfigurationBuilder.SYSTEM.targetFolder(target.toFile())
        RellDokkaGenerator(builder).generate()
        return target
    }

    @Test
    fun `produces a site root index and per-package indexes`() {
        val out = generate()
        assertThat(out / "index.html").exists()
        assertThat(out / "navigation.html").exists()
        assertThat(out / "scripts/pages.json").exists()
        // The system lib's outer module slug.
        val moduleSlug = SYSTEM_MODULE_SLUG
        assertThat(out / "$moduleSlug/index.html").exists()
        assertThat(out / "$moduleSlug/[root]/index.html").exists()
        assertThat(out / "$moduleSlug/rell.test/index.html").exists()
        assertThat(out / "$moduleSlug/crypto/index.html").exists()
    }

    @Test
    fun `documents core stdlib types`() {
        val out = generate()
        val moduleSlug = SYSTEM_MODULE_SLUG
        val rootIndex = (out / "$moduleSlug/[root]/index.html").readText()
        assertThat(rootIndex).contains("integer")
        assertThat(rootIndex).contains("text")
        assertThat(rootIndex).contains("byte_array")
    }

    @Test
    fun `blacklisted types are absent from output`() {
        val out = generate()
        val moduleSlug = SYSTEM_MODULE_SLUG
        // `guid`, `signer`, `comparable`, `immutable` are filtered at the build stage —
        // compiler-internal abstract base types not part of the user-facing surface.
        assertThat((out / "$moduleSlug/[root]/guid/index.html").exists()).isFalse()
        assertThat((out / "$moduleSlug/[root]/signer/index.html").exists()).isFalse()
        assertThat((out / "$moduleSlug/[root]/comparable/index.html").exists()).isFalse()
        assertThat((out / "$moduleSlug/[root]/immutable/index.html").exists()).isFalse()
    }

    @Test
    fun `search index covers stdlib defs`() {
        val out = generate()
        val pages = (out / "scripts/pages.json").readText()
        // Anchor on the qname keys (wrapped in quotes) so we don't false-positive on a `"name"`
        // hit that happens to share a substring with one of these.
        assertThat(pages).contains("\"integer\"")
        assertThat(pages).contains("\"text\"")
        assertThat(pages).contains("\"byte_array\"")
        assertThat(pages).contains("\"crypto\"")
        assertThat(pages).contains("\"rell.test\"")
    }

    @Test
    fun `overloaded stdlib functions render as one page with multiple signature blocks`() {
        val out = generate()
        val moduleSlug = SYSTEM_MODULE_SLUG
        // `text.sub` has two overloads in the stdlib: sub(start) and sub(start, end).
        val subPage = out / "$moduleSlug/[root]/text/sub.html"
        assertThat(subPage).exists()
        val html = subPage.readText()
        // Both signatures live on the same page — count occurrences of the function keyword
        // followed by the function name. Two signature blocks → two matches.
        val sigBlocks = Regex("class=\"signature\"").findAll(html).count()
        assertThat(sigBlocks >= 2).isTrue()
        // The overload separator HR appears between blocks.
        assertThat(html).contains("overload-sep")
        // And the search index lists `text.sub` exactly once (one search record per group, not
        // per overload). Class members are recorded as `"<Type>.<member>"` in Search.kt.
        val pages = (out / "scripts/pages.json").readText()
        val subRecords = Regex("\"name\":\"text\\.sub\"").findAll(pages).count()
        assertThat(subRecords).isEqualTo(1)
    }

    @Test
    fun `rell test namespace is generated`() {
        val out = generate()
        val moduleSlug = SYSTEM_MODULE_SLUG
        assertThat(out / "$moduleSlug/rell.test/index.html").exists()
        val text = (out / "$moduleSlug/rell.test/index.html").readText()
        assertThat(text).contains("assert_equals")
    }
}

/**
 * Output directory slug for the system lib. Pinned to the legacy Dokka `DModule` name
 * "Rell System Library" (not the longer page title) for URL compatibility — see
 * `RellDokkaPluginConfiguration.SYSTEM_MODULE_NAME`.
 */
internal const val SYSTEM_MODULE_SLUG: String = "-rell -system -library"

private fun assertk.Assert<Boolean>.isFalse() = given { value -> kotlin.test.assertFalse(value) }
private fun assertk.Assert<Boolean>.isTrue() = given { value -> kotlin.test.assertTrue(value) }
