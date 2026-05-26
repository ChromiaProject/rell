/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.exists
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
        val moduleSlug = "-rell -system -library -a-p-i -reference"
        assertThat(out / "$moduleSlug/index.html").exists()
        assertThat(out / "$moduleSlug/[root]/index.html").exists()
        assertThat(out / "$moduleSlug/rell.test/index.html").exists()
        assertThat(out / "$moduleSlug/crypto/index.html").exists()
    }

    @Test
    fun `documents core stdlib types`() {
        val out = generate()
        val moduleSlug = "-rell -system -library -a-p-i -reference"
        val rootIndex = (out / "$moduleSlug/[root]/index.html").readText()
        assertThat(rootIndex).contains("integer")
        assertThat(rootIndex).contains("text")
        assertThat(rootIndex).contains("byte_array")
    }

    @Test
    fun `blacklisted types are absent from output`() {
        val out = generate()
        val moduleSlug = "-rell -system -library -a-p-i -reference"
        // `guid` and `signer` are filtered out at the build stage.
        assertThat((out / "$moduleSlug/[root]/guid/index.html").exists()).isFalse()
        assertThat((out / "$moduleSlug/[root]/signer/index.html").exists()).isFalse()
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
    fun `rell test namespace is generated`() {
        val out = generate()
        val moduleSlug = "-rell -system -library -a-p-i -reference"
        assertThat(out / "$moduleSlug/rell.test/index.html").exists()
        val text = (out / "$moduleSlug/rell.test/index.html").readText()
        assertThat(text).contains("assert_equals")
    }
}

private fun assertk.Assert<Boolean>.isFalse() = given { value -> kotlin.test.assertFalse(value) }
