/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.contains
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.toPath

/**
 * Verifies that `addSourceLink(localDirectory, remoteUrl, lineSuffix)` translates into the
 * `<a href="…rell#L<n>">` link rendered next to definitions on their detail pages.
 */
class SourceLinkTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `source link rewrites paths under the configured local directory`() {
        val projectRoot = this.javaClass.classLoader.getResource("my-rell-dapp")!!.toURI().toPath()
        val target = (tempDir / "out")
        val builder = RellDokkaPluginConfigurationBuilder(
            title = "model",
            modules = listOf("main"),
            projectRoot = (projectRoot / "src").toFile(),
        )
            .targetFolder(target.toFile())
            .customStyleSheets(emptyList())
            .customAssets(emptyList())
            .footerMessage("Footer")
            .includes(emptyList())
            .filteredModules(emptyList())
            .additionalModules(emptyList())
            .addSourceLink(
                (projectRoot / "src").absolutePathString(),
                URI("https://gitlab.com/chromaway/rell/-/blob/dev").toURL(),
                "#L",
            )
        RellDokkaGenerator(builder).generate()

        val helloWorld = target / "model/main/hello_world.html"
        val content = helloWorld.readText()
        // `hello_world` lives at main.rell — somewhere within the URL the line-anchored path
        // must appear.
        assertThat(content).contains("https://gitlab.com/chromaway/rell/-/blob/dev/main.rell#L")
    }
}
