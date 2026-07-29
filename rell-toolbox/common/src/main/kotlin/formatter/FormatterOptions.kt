/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

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
        // The same instance is mutated across reloads, so a key removed from the file must fall
        // back to its default instead of keeping the previously parsed value.
        resetToDefaults()
        EditorConfigParser.parse(configFile)?.let { updateFormatterOptions(it) }
    }

    private fun resetToDefaults() {
        val defaults = FormatterOptions()
        maxLineWidth = defaults.maxLineWidth
        insertSpaces = defaults.insertSpaces
        tabSize = defaults.tabSize
        newLineString = defaults.newLineString
    }

    private fun updateFormatterOptions(editorConfig: EditorConfig) {
        for (section in editorConfig.sections) {
            for ((key, value) in section.properties) {
                when (key) {
                    "max_line_width" -> {
                        maxLineWidth = value.sourceValue.trim().toInt()
                    }

                    "insert_spaces" -> {
                        insertSpaces = value.sourceValue.trim() == "true"
                    }

                    "tab_size" -> {
                        tabSize = value.sourceValue.trim().toInt()
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
