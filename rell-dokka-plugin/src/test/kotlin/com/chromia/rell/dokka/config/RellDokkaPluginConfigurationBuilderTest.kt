/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import assertk.assertThat
import assertk.assertions.*
import net.postchain.rell.api.base.RellCliEnv
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

class RellDokkaPluginConfigurationBuilderTest {

    @Test
    fun `SYSTEM builder is system mode with no project root`() {
        val cfg = RellDokkaPluginConfigurationBuilder.SYSTEM.targetFolder(File("out")).build()
        assertThat(cfg.system).isTrue()
        assertThat(cfg.projectRoot).isNull()
        assertThat(cfg.title).isEqualTo(RellDokkaPluginConfiguration.SYSTEM_TITLE)
    }

    @Test
    fun `addSourceLink URI overload delegates to URL overload`() {
        val cfg = RellDokkaPluginConfigurationBuilder(
            title = "t", modules = listOf("main"), projectRoot = File("/tmp/x"),
        )
            .targetFolder(File("out"))
            .addSourceLink("/tmp/x", URI("https://example.com/repo"), "#L")
            .build()
        assertThat(cfg.sourceLinks).hasSize(1)
        assertThat(cfg.sourceLinks[0].remoteUrl.toString()).isEqualTo("https://example.com/repo")
        assertThat(cfg.sourceLinks[0].remoteLineSuffix).isEqualTo("#L")
    }

    @Test
    fun `cliEnv is forwarded into configuration`() {
        val cfg = RellDokkaPluginConfigurationBuilder(
            title = "t", modules = listOf("main"), projectRoot = File("/tmp/x"),
        )
            .targetFolder(File("out"))
            .cliEnv(RellCliEnv.NULL)
            .build()
        assertThat(cfg.cliEnv).isNotNull()
    }

    @Test
    fun `non-empty includes customStyleSheets customAssets all map to Paths`() {
        val cfg = RellDokkaPluginConfigurationBuilder(
            title = "t", modules = listOf("main"), projectRoot = File("/tmp/x"),
        )
            .targetFolder(File("out"))
            .includes(listOf(File("/a/b.md")))
            .customStyleSheets(listOf("/a/site.css"))
            .customAssets(listOf("/a/logo.png"))
            .build()
        assertThat(cfg.includes).hasSize(1)
        assertThat(cfg.customStyleSheets).hasSize(1)
        assertThat(cfg.customAssets).hasSize(1)
        assertThat(cfg.includes[0].toString()).isEqualTo("/a/b.md")
        assertThat(cfg.customStyleSheets[0].toString()).isEqualTo("/a/site.css")
        assertThat(cfg.customAssets[0].toString()).isEqualTo("/a/logo.png")
    }
}
