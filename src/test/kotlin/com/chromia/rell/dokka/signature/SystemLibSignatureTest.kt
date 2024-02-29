package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.config.RellConfig
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import signatures.Parameter
import signatures.Parameters
import signatures.firstSignature
import signatures.lastSignature
import signatures.renderedContent
import utils.A
import utils.Span
import utils.TestOutputWriterPlugin
import utils.match

internal class SystemLibSignatureTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        this.pluginsConfigurations.add(RellConfig.SYSTEM.toPluginConfig())
        sourceSets {
            RellConfig.SYSTEM_SOURCE_SETS.forEach {
                add(lazy { it })
            }
        }
    }

    @Test
    fun `function has reference to types`() {
        val writerPlugin = TestOutputWriterPlugin()
        testFromData(configuration, cleanupOutput = false, pluginOverrides = listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                writerPlugin.writer.renderedContent("-rell/<root>/integer/abs.html").firstSignature()
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
    @Disabled // Does not work across source sets
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
                writerPlugin.writer.renderedContent("-rell/<root>/integer/integer.html").firstSignature()
                        .match(
                                "constructor(",
                                Parameters(
                                        Parameter("value: ", A("text"), ", "),
                                        Parameter("[radix: ", A("integer"), "]"),
                                ),
                                "): ", A("integer"),
                                ignoreSpanWithTokenStyle = true)
                writerPlugin.writer.renderedContent("-rell/rell.test/op/sign.html").lastSignature()
                        .match(
                                "function ",
                                A("sign"),
                                "(",
                                Parameters(
                                        Parameter("privkeys: ", A("byte_array"), "..."), // Should be A()
                                ),
                                "): ", A("rell.test.tx"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }
}
