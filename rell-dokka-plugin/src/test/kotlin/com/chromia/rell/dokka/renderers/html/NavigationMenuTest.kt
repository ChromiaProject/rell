package com.chromia.rell.dokka.renderers.html

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test
import utils.TestOutputWriter
import utils.TestOutputWriterPlugin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class NavigationMenuTest : SingleFileRellDokkaPluginTest() {
    private fun TestOutputWriter.navigationHtml(): Element = contents.getValue("navigation.html").let { Jsoup.parse(it) }

    @Test
    fun `anonymous functions - hashtags are escaped`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            @extendable function foo() {}
            @extend(foo) function () {}
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                val searchRecords = writerPlugin.writer.pagesJson()
                val content = writerPlugin.writer.navigationHtml().select("div.toc--part")
                assertThat(content.size).isEqualTo(4)

                content.last()!!.assertNavigationLink(
                        id = "test-dapp-nav-submenu-0-1",
                        text = "function#0()",
                        address = "test-dapp/main/function%230.html",
                )
                val anonymousFunction = searchRecords.find { record ->
                    record.description?.contains("main.function#0") == true
                }
                assertThat(anonymousFunction?.location).isEqualTo("test-dapp/main/function%230.html")
            }
        }
    }

    @Test
    fun `filter out modules from navigation but files exist still`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            @extendable function foo() {}
            @extend(foo) function () {}
        """.trimIndent(), listOf(writerPlugin), listOf("main")) {
            renderingStage = { _, _ ->
                val searchRecords = writerPlugin.writer.pagesJson()
                val content = writerPlugin.writer.navigationHtml().select("div.toc--part")
                assertThat(content.size).isEqualTo(1)

                content.last()!!.assertNavigationLink(
                        id = "test-dapp-nav-submenu",
                        text = "test-dapp",
                        address = "index.html",
                )
                val anonymousFunction = searchRecords.find { record ->
                    record.description?.contains("main.function#0") == true
                }
                assertThat(anonymousFunction?.location).isEqualTo("test-dapp/main/function%230.html")
            }
        }
    }
}

// Partially copied from
// https://github.com/Kotlin/dokka/blob/7a25a0cb7b5da54d7052137d7f68335dc4da008a/dokka-subprojects/plugin-base/src/test/kotlin/renderers/html/NavigationTest.kt#L436
private fun Element.assertNavigationLink(
        id: String, text: String, address: String, isStrikethrough: Boolean = false
) {
    assertEquals(id, this.id())

    val link = this.selectFirst("a")
    assertNotNull(link)
    assertEquals(text, link.text())
    assertEquals(address, link.attr("href"))

    if (isStrikethrough) {
        val textInsideStrikethrough = link.selectFirst("strike")?.text()
        assertEquals(text, textInsideStrikethrough)
    } else {
        assertNull(link.selectFirst("strike"))
    }
}
