/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Guards the promise that the new generator's URL layout stays a superset of the legacy Dokka one:
 * every URL the old site published under `/pages/rell/` must still resolve when the new output is
 * dropped at the same root, so existing links and bookmarks (docs.chromia.com, downstream tooling)
 * don't 404.
 *
 * The fixture `legacy-dokka-urls.txt` is the full list of HTML pages the legacy Dokka generator
 * produced for the system library (module dir replaced by the `MOD/` token). Each non-[BLACKLIST]
 * entry must exist in the freshly generated tree — either as a real page or as a redirect stub
 * (attributes → owner anchor, type aliases → flat page).
 *
 * [BLACKLIST] holds the handful of legacy URLs we intentionally drop, with the reason each is safe.
 */
class UrlCompatibilityTest {
    @TempDir
    lateinit var tempDir: Path

    private fun generate(): Path {
        val target = tempDir / "out"
        RellDokkaGenerator(RellDokkaPluginConfigurationBuilder.SYSTEM.targetFolder(target.toFile())).generate()
        return target
    }

    @Test
    fun `every legacy URL resolves in the new output`() {
        val out = generate()
        val legacyUrls = readLegacyUrls()

        val missing = legacyUrls
            .filterNot { it in BLACKLIST }
            .map { it.replaceFirst("MOD/", "$SYSTEM_MODULE_SLUG/") }
            .filterNot { (out / it).exists() }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `blacklisted assert duplicates resolve at their canonical rell test location`() {
        val out = generate()
        // The legacy `[root]/assert_*.html` pages are dropped (they were a duplication of the
        // rell.test assert family); the canonical location must still be present.
        for (url in BLACKLIST) {
            if (!url.endsWith(".html") || "/assert_" !in url) continue
            val name = url.substringAfterLast('/')
            assertThat((out / "$SYSTEM_MODULE_SLUG/rell.test/$name").exists()).isTrue()
        }
    }

    @Test
    fun `attribute pages are redirects to the owner anchor`() {
        val out = generate()
        // `keypair` is a struct with a `pub` attribute. Legacy gave it its own page; we now embed it
        // on the owner page and keep the URL alive as a redirect to that anchor.
        val redirect = (out / "$SYSTEM_MODULE_SLUG/rell.test/keypair/pub.html")
        assertThat(redirect.exists()).isTrue()
        assertThat(redirect.readText()).contains("url=index.html#pub")
        // The owner page carries the matching anchor.
        assertThat((out / "$SYSTEM_MODULE_SLUG/rell.test/keypair/index.html").readText()).contains("id=\"pub\"")
    }

    @Test
    fun `type alias index pages redirect to the flat page`() {
        val out = generate()
        // `name` is a type alias: legacy `name/index.html`, new flat `name.html` + redirect stub.
        val redirect = (out / "$SYSTEM_MODULE_SLUG/[root]/name/index.html")
        assertThat(redirect.exists()).isTrue()
        assertThat(redirect.readText()).contains("url=../name.html")
        assertThat((out / "$SYSTEM_MODULE_SLUG/[root]/name.html").exists()).isTrue()
    }

    private fun readLegacyUrls(): List<String> =
        UrlCompatibilityTest::class.java.classLoader
            .getResourceAsStream("legacy-dokka-urls.txt")!!
            .bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private companion object {
        /**
         * Legacy URLs we intentionally do not reproduce. Each line is a legacy path (with the `MOD/`
         * module-dir token).
         *
         *  - `[root]/comparable`, `[root]/immutable`: compiler-internal abstract base types, filtered
         *    at the build stage (see SystemLibTest `blacklisted types are absent`). Never part of the
         *    user-facing surface.
         *  - `[root]/assert_*`: the legacy generator duplicated the `rell.test` assert family at the
         *    root package. The canonical `rell.test/assert_*.html` pages remain (asserted above).
         */
        val ASSERT_FUNCTIONS = listOf(
            "assert_equals", "assert_events", "assert_fails", "assert_false",
            "assert_ge", "assert_ge_le", "assert_ge_lt", "assert_gt", "assert_gt_le",
            "assert_gt_lt", "assert_le", "assert_lt", "assert_not_equals",
            "assert_not_null", "assert_null", "assert_true",
        )

        val BLACKLIST: Set<String> = buildSet {
            add("MOD/[root]/comparable/index.html")
            add("MOD/[root]/immutable/index.html")
            for (name in ASSERT_FUNCTIONS) add("MOD/[root]/$name.html")
        }
    }
}

private fun assertk.Assert<Boolean>.isTrue() = given { value -> kotlin.test.assertTrue(value) }
private fun assertk.Assert<String>.contains(s: String) = given { value -> kotlin.test.assertTrue(s in value, "expected to contain: $s") }
