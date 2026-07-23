/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

class RellFormatterTest {

    @Test
    fun `Format whole test suite`() {
        val testFolderUri = javaClass.classLoader.getResource("formatting-test-suite")?.toURI()!!

        val files = File(testFolderUri)
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".rell") }
            .associate { it.path to it.readText() }

        val formatterOptions = FormatterOptions()
        // TODO: Should reformat all formatted files to use tabs
        formatterOptions.insertSpaces = true
        var i = 1
        val nrOfTestCases = files.size / 2
        for ((key, value) in files) {
            if (!key.contains("formatted")) {
                println("File: $key")
                val formattedText = RellFormatter.formatString(value, formatterOptions)
                val expectedText = files[key.replace(".rell", "_formatted.rell")]
                assertThat(formattedText).isEqualTo(expectedText)
                println("Completed Test Cases: $i / $nrOfTestCases")
                i++
            }
        }
    }

    @Test
    fun `Format with formatter options of short max line length`() {
        val testFolder = javaClass.classLoader.getResource("formatting-options")
        val originalFile = File(URI("$testFolder/original_file.rell"))
        val formatterOptions = FormatterOptions()
        formatterOptions.maxLineWidth = 20

        val formattedText = RellFormatter.formatString(originalFile.readText(), formatterOptions)
        assertThat(formattedText).isEqualTo(File(URI("$testFolder/max_line_20.rell")).readText())
    }

    @Test
    fun `Format with formatter options of default options`() {
        val testFolder = javaClass.classLoader.getResource("formatting-options")
        val originalFile = File(URI("$testFolder/original_file.rell"))
        val formatterOptions = FormatterOptions()

        val formattedText = RellFormatter.formatString(originalFile.readText(), formatterOptions)
        assertThat(formattedText).isEqualTo(File(URI("$testFolder/max_line_120.rell")).readText())
        assertThat(formattedText).isEqualTo(originalFile.readText())
    }

    @Test
    fun `Format with formatter options with insert spaces`() {
        val testFolder = javaClass.classLoader.getResource("formatting-options")
        val originalFile = File(URI("$testFolder/original_file.rell"))
        val formatterOptions = FormatterOptions()
        formatterOptions.insertSpaces = true

        val formattedText = RellFormatter.formatString(originalFile.readText(), formatterOptions)
        assertThat(formattedText).isEqualTo(File(URI("$testFolder/with_spaces.rell")).readText())
    }

    @Test
    fun `Format with formatter options with insert spaces and tab size 2`() {
        val testFolder = javaClass.classLoader.getResource("formatting-options")
        val originalFile = File(URI("$testFolder/original_file.rell"))
        val formatterOptions = FormatterOptions()
        formatterOptions.insertSpaces = true
        formatterOptions.tabSize = 2

        val formattedText = RellFormatter.formatString(originalFile.readText(), formatterOptions)
        assertThat(formattedText).isEqualTo(File(URI("$testFolder/with_spaces_2_tab_size.rell")).readText())
    }

    // Regression test: under ANTLR error recovery, WhenExprContext.whenExprLastCase() (and the
    // whenCondition()/valueBlock()/expression() of any case) can come back null for an incomplete
    // when-expr. WhenExprFormatter used to dereference these unguarded and throw a
    // NullPointerException, crashing format-on-save on a file being actively edited.
    @Test
    fun `Format incomplete when-expr does not throw`() {
        val formatterOptions = FormatterOptions()
        RellFormatter.formatString("function f() { val x = when {} }", formatterOptions)
    }

    @Test
    fun `Format when-expr with stray semicolon after value-block arm does not throw`() {
        val formatterOptions = FormatterOptions()

        RellFormatter.formatString(
            """
                function f(): integer {
                    return when {
                        2 -> { val y = 5; y };
                        else -> 0;
                    };
                }
            """.trimIndent(),
            formatterOptions,
        )
    }
}
