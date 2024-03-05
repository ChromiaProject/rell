package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.RellModule
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.junit.jupiter.api.Test
import signatures.Parameter
import signatures.Parameters
import signatures.firstSignature
import signatures.lastSignature
import signatures.renderedContent
import utils.A
import utils.TestOutputWriterPlugin
import utils.match

internal class SystemLibSignatureTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        this.pluginsConfigurations.add(RellDokkaPluginConfiguration.SYSTEM_CONFIG.toPluginConfig())
        sourceSets {
            RellModule.entries.forEach {
                add(lazy { it.sourceSet(listOf()) })
            }
        }
    }

    @Test
    fun `function has reference to types`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/root/integer/abs.html").firstSignature()
                        .match("pure function ", A("abs"), "(): ", A("integer"), ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `function in other namespace can reference system lib types`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/crypto/eth_privkey_to_address.html").firstSignature()
                        .match(
                                "function ",
                                A("eth_privkey_to_address"),
                                "(",
                                Parameters(
                                        Parameter("privkey: ", A("byte_array")),
                                ),
                                "): ",
                                A("byte_array"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `function in test namespace can reference test lib types`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell.test/op/run_must_fail.html").lastSignature()
                        .match(
                                "function ",
                                A("run_must_fail"),
                                "(",
                                Parameters(
                                        Parameter("expected_message: ", A("text"))
                                ),
                                "): ",
                                A("rell.test.failure"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `function with list typest lib types`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell.test/tx/tx.html").firstSignature()
                        .match(
                                "constructor(",
                                Parameters(
                                        Parameter("ops: ", A("list"), "<", A("rell.test.op"), ">")
                                ),
                                ")",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `Arity is shown properly`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/root/integer/integer.html").firstSignature()
                        .match(
                                "constructor(",
                                Parameters(
                                        Parameter("value: ", A("text"), ", "),
                                        Parameter("[radix: ", A("integer"), "]"),
                                ),
                                ")",
                                ignoreSpanWithTokenStyle = true)
                writerPlugin.writer.renderedContent("-rell/rell.test/op/sign.html").lastSignature()
                        .match(
                                "function ",
                                A("sign"),
                                "(",
                                Parameters(
                                        Parameter("privkeys: ", A("byte_array"), "..."),
                                ),
                                "): ", A("rell.test.tx"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `Links are working for nested inner types (tuple arguments)`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell.test/assert_events.html").firstSignature()
                        .match(
                                "function ", A("assert_events"), "(",
                                Parameters(
                                        Parameter("expected: (", A("text"), ", ", A("gtv"), ")...")
                                ),
                                ")",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `Links are worknig on return types of properties`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell.test/op/args.html").firstSignature()
                        .match(
                                "val ", A("args"), ": ",
                                A("list"), "<", A("gtv"), ">",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `Links are worknig on return types of properties2`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell.test.keypairs/alice.html").firstSignature()
                        .match(
                                "val ", A("alice"), ": ",
                                A("rell.test.keypair"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `Meta type (special) constructor does not look too ugly`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/rell/meta/meta.html").firstSignature()
                        .match(
                                "constructor(",
                                Parameters(
                                        Parameter("type: T")
                                ), ")",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `function parameters are displayed correctly`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/root/try_call.html").lastSignature() // TODO: Why the [rell]-prefix
                        .match(
                                "function ", A("try_call"), "(",
                                Parameters(
                                        Parameter("fn: () -> T, "), Parameter("default: T")
                                ), "): T",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `special functions empty and exists displayed correctly`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/root/empty.html").firstSignature()
                        .match(
                                "function ", A("empty"), "(",
                                Parameters(
                                        Parameter("arg: T?")
                                ), "): ", A("boolean"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }

    @Test
    fun `print function can print anything`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/root/print.html").firstSignature()
                        .match(
                                "function ", A("print"), "(",
                                Parameters(
                                        Parameter("values: anything...")
                                ), ")",
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }
}
