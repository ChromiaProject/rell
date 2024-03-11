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

internal class RellSignatureProviderTest : SingleFileRellDokkaPluginTest() {

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

    @Test
    fun `functions signature with primitive types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("function test(arg: integer, arg2: byte_array) = 13;", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: integer, "), Parameter("arg2: byte_array")
                                ),
                                "): integer",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with nullable types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("function test(arg: text?): integer? = 13;", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: text?")
                                ),
                                "): integer?",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with list types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("function test(arg: list<text>): list<integer> = [12];", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: list<text>")
                                ),
                                "): list<integer>",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with nullable list types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("function test(arg: list<text?>): set<integer> = set<integer>();", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: list<text?>")
                                ),
                                "): set<integer>",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with function arguments types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("function test(arg: (integer, text) -> text) = 2;", listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: (integer,text)->text")
                                ),
                                "): integer",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with object arguments types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("""
            entity my_entity {}
            struct my_struct {}
            enum my_enum {}
            object my_object {}
            function test(arg1: my_entity, arg2: my_struct, arg3: my_enum) = my_object;
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg1: ", A("my_entity"), ", "),
                                        Parameter("arg2: ", A("my_struct"), ", "),
                                        Parameter("arg3: ", A("my_enum"))
                                ),
                                "): ", A("my_object"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with list object arguments types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("""
            struct my_struct {}
            function test(arg: list<my_struct>) = set<my_struct>();
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: list<", A("my_struct"), ">")
                                ),
                                "): set<", A("my_struct"), ">",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `functions signature with map arguments types`() {
        val writerPlugin = TestOutputWriterPlugin()
        singleFileTestInline("""
            struct my_struct {}
            function test(arg: map<text, my_struct>) = arg;
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("test-dapp/main/test.html").firstSignature()
                        .match(
                                "function ", A("test"), "(",
                                Parameters(
                                        Parameter("arg: map<text, ", A("my_struct"), ">")
                                ),
                                "): map<text, ", A("my_struct"), ">",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }
}


