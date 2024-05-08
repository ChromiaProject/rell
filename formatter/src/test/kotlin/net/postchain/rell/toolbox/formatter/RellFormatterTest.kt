package net.postchain.rell.toolbox.formatter

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import java.net.URI
import org.junit.jupiter.api.Test

class RellFormatterTest {

    @Test
    fun `Format whole test suite`() {
        val testFolderUri = javaClass.classLoader.getResource("formatting-test-suite").toURI()

        val files =
            File(testFolderUri).walkTopDown().filter { it.isFile && it.name.endsWith(".rell") }
                .map { it.path to it.readText() }.toMap()

        val formatterOptions = FormatterOptions()
        //TODO: Should reformat all formatted files to use tabs
        formatterOptions.insertSpaces = true
        var i = 1
        val nrOfTestCases = files.size / 2
        files.forEach {
            if (!it.key.contains("formatted")) {
                println("File: ${it.key}")
                val formattedText = RellFormatter.formatString(it.value, formatterOptions)
                val expectedText = files[it.key.replace(".rell", "_formatted.rell")]
                assertThat(formattedText).isEqualTo(expectedText)
                println("Completed Test Cases: ${i} / $nrOfTestCases")
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
}
