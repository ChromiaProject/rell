package net.postchain.rell.toolbox.formatter

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.io.File

class RellFormatterTest {
    
    @Test
    fun `Format whole document`() {
        val testFolderUri = javaClass.classLoader.getResource("formatting-test-suite").toURI()

        val files =
            File(testFolderUri).walkTopDown().filter { it.isFile && it.name.endsWith(".rell") }
                .map { it.path to it.readText() }.toMap()

        val formatterRequest = FormatterRequest()
        var i = 1
        val nrOfTestCases = files.size / 2
        files.forEach {
            if (!it.key.contains("formatted")) {
                println("File: ${it.key}")
                val formattedText = RellFormatter.formatString(it.value, formatterRequest)
                val expectedText = files[it.key.replace(".rell", "_formatted.rell")]
                assertThat(formattedText).isEqualTo(expectedText)
                println("Completed Test Cases: ${i} / $nrOfTestCases")
                i++
            }

        }
    }
}
