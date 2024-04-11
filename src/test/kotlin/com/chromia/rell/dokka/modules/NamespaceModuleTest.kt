package com.chromia.rell.dokka.modules

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import org.junit.jupiter.api.Test
import utils.TestOutputWriterPlugin

class NamespaceModuleTest: SingleFileRellDokkaPluginTest() {

    @Test
    fun `Namespaces are treated as modules`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            namespace a {
              query test() = "";
            }
            namespace b {
              query test() = "";
            }
        """.trimIndent(), listOf(writerPlugin)) {
           renderingStage = { _,_ ->
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main/a/test.html")).isTrue()
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main/b/test.html")).isTrue()
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main/test.html")).isFalse()
           }
        }
    }
}