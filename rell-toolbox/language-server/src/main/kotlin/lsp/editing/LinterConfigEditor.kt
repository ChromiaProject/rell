/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import java.io.File

object LinterConfigEditor {
    /**
     * Sets `ruleId=false` in [configFile], creating the file when missing. Idempotent: an existing
     * assignment of the key is rewritten in place instead of appending a duplicate. A section
     * header is added to a section-less file because the parser ignores properties outside a
     * section.
     */
    fun disableRule(configFile: File, ruleId: String) {
        val content = if (configFile.isFile) configFile.readText() else ""
        val lines = content.lines()
        val assignment = Regex("""\s*${Regex.escape(ruleId)}\s*=.*""")

        val updated = if (lines.any { it.matches(assignment) }) {
            lines.joinToString("\n") { if (it.matches(assignment)) "$ruleId=false" else it }
        } else {
            buildString {
                append(content)
                if (content.isNotEmpty() && !content.endsWith("\n")) append('\n')
                if (lines.none { it.trim().startsWith("[") }) append("[*.rell]\n")
                append("$ruleId=false\n")
            }
        }
        configFile.writeText(updated)
    }
}
