package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.config.RellConfig
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test
import signatures.Parameter
import signatures.Parameters
import signatures.firstSignature
import signatures.renderedContent
import utils.A
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
                writerPlugin.writer.renderedContent("-rell/rell.test/op/run_must_fail.html").firstSignature()
                        .match(
                                "function ",
                                A("run_must_fail"),
                                "(): ",
                                A("rell.test.failure"),
                                ignoreSpanWithTokenStyle = true)
            }
        }
    }


}