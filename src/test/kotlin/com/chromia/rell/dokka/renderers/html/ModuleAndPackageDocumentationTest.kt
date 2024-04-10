package com.chromia.rell.dokka.renderers.html

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import com.chromia.rell.dokka.TEST_DAPP_NAME
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import utils.TestOutputWriterPlugin
import java.io.File

class ModuleAndPackageDocumentationTest: SingleFileRellDokkaPluginTest() {

    @Test
    fun `Module and package level docs are applied`(@TempDir dir: File) {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        val docsFile = File(dir, "docs.md")
        docsFile.writeText("""
            # Module $TEST_DAPP_NAME
            dapp level docs
            # Package main
            module level docs
        """.trimIndent())
        singleFileTestInline("""
            query hello_world() = "Hello";
        """.trimIndent(), listOf(writerPlugin), sourceSetBuilder = {
            includes = listOf(docsFile.absolutePath)
        }) {
            this.renderingStage = { _, _ ->
                val indexPage = writerPlugin.writer.contents["index.html"]!!.let { Jsoup.parse(it) }
                assertThat(indexPage.body().select("div.cover").select("p.paragraph").firstOrNull()?.text()).isEqualTo("dapp level docs")
                val mainIndexPage = writerPlugin.writer.contents["test-dapp/main/index.html"]!!.let { Jsoup.parse(it) }
                assertThat(mainIndexPage.body().select("div.cover").select("p.paragraph").firstOrNull()?.text()).isEqualTo("module level docs")
            }
        }
    }
}
