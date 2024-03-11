package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import org.junit.jupiter.api.Test
import signatures.Parameter
import signatures.Parameters
import signatures.firstSignature
import signatures.renderedContent
import utils.A
import utils.TestOutputWriterPlugin
import utils.match

internal class RellSignatureProviderTest: SingleFileRellDokkaPluginTest() {

    @Test
    fun `constants signature`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("val test = 32;", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "val ", A("test"), ": integer",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `operations signature`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("operation test() {}", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "operation ", A("test"), "()",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `query signature`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("query test() = 13;", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "query ", A("test"), "(): integer",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

}