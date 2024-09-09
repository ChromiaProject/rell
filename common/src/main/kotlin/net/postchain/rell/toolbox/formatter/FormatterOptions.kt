package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.editorconfig.EditorConfigParser
import org.ec4j.core.model.EditorConfig
import java.io.File

data class FormatterOptions(
    var maxLineWidth: Int = 120,
    var insertSpaces: Boolean = true,
    var tabSize: Int = 4,
    var newLineString: String = NewLineStyle.LF.newLineString,
) {
    fun updateOptionsFromFile(configFile: File) {
        val editorConfig = EditorConfigParser.parse(configFile)
        if (editorConfig != null) {
            updateFormatterOptions(editorConfig)
        } else {
            val defaultOptions = FormatterOptions()
            maxLineWidth = defaultOptions.maxLineWidth
            insertSpaces = defaultOptions.insertSpaces
            tabSize = defaultOptions.tabSize
        }
    }

    private fun updateFormatterOptions(editorConfig: EditorConfig) {
        editorConfig.sections.forEach { section ->
            section.properties.forEach { property ->
                when (property.key) {
                    "max_line_width" -> {
                        maxLineWidth = property.value.sourceValue.trim().toInt()
                    }

                    "insert_spaces" -> {
                        insertSpaces = property.value.sourceValue.trim() == "true"
                    }

                    "tab_size" -> {
                        tabSize = property.value.sourceValue.trim().toInt()
                    }
                }
            }
        }
    }

    companion object {
        const val DEPRECATED_RELL_FORMAT_FILE_NAME = ".rellformat"
        const val PREFERRED_RELL_FORMAT_FILE_NAME = ".rell_format"
    }
}

enum class NewLineStyle(val newLineString: String) {
    LF("\n"),
    CRLF("\r\n"),
}
