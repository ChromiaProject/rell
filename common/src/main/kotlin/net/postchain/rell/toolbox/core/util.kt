package net.postchain.rell.toolbox.core

import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.utils.ide.IdeApi.getRellVersionInfo


data class RellAbout(val rellVersion: String, val aboutText: String)

object RellVersionInfo {

    fun getAbout() = RellAbout(getRellVersion(), getAboutText())

    private fun getAboutText(): String {
        val versionInfo = getRellVersionInfo() ?: throw IllegalStateException("Rell version info is not available")
        return """
            Rell ${versionInfo[Rt_RellVersionProperty.RELL_VERSION]}
            Postchain ${versionInfo[Rt_RellVersionProperty.POSTCHAIN_VERSION]}
            
            branch: ${versionInfo[Rt_RellVersionProperty.RELL_BRANCH]}
            commit: ${versionInfo[Rt_RellVersionProperty.RELL_COMMIT_ID]} (${versionInfo[Rt_RellVersionProperty.RELL_COMMIT_TIME]})
        """.trimIndent()
    }

    private fun getRellVersion(): String {
        val versionInfo = getRellVersionInfo()
        return versionInfo?.get(Rt_RellVersionProperty.RELL_VERSION)
            ?: throw IllegalStateException("Rell version info is not available")
    }
}

data class Location(val uri: String, val range: Range)

data class Range(val start: Position, val end: Position) {
    override fun toString() = if (start.line == end.line)
        "[${start.line}:${start.character}-${end.character}]"
    else
        "[${start.line}:${start.character}]-[${end.line}:${end.character}]"
}

data class Position(val line: Int, val character: Int)

data class TextEdit(val range: Range, val newText: String)


fun offsetToPosition(content: String, offset: Int): Position {
    val contentLength = content.length
    if (offset < 0 || offset > contentLength) {
        throw IndexOutOfBoundsException("Offset $offset is out of bounds for range [0, $contentLength]")
    }
    var line = 0
    var column = 0

    for (i in content.indices) {
        val ch = content[i]
        if (i == offset) break
        if (ch == NL) {
            line++
            column = 0
        } else {
            column++
        }
    }
    return Position(line, column)
}

const val NL = '\n'

fun positionToOffset(content: String, position: Position): Int {
    var line = 0
    var column = 0
    for (i in content.indices) {
        val ch = content[i]
        if (position.line == line && position.character == column) {
            return i
        }
        if (ch == NL) {
            line++
            column = 0
        } else {
            column++
        }
    }
    if (position.line == line && position.character == column) {
        return content.length
    }
    throw IndexOutOfBoundsException("Position $position out of bounds")
}

class TextReplacement(val startOffset: Int, val stopOffset: Int, val text: String)

fun applyTextReplacements(source: String, replacements: List<TextReplacement>): String {
    val result = StringBuilder(source)

    // Apply replacements in reverse order to avoid issues with changing offsets
    val sortedReplacements = replacements.sortedByDescending { it.startOffset }

    for (replacement in sortedReplacements) {
        if (replacement.startOffset < 0 || replacement.startOffset > source.length ||
            replacement.stopOffset < replacement.startOffset || replacement.stopOffset > source.length
        ) {
            // Skip invalid replacements
            continue
        }

        result.replace(replacement.startOffset, replacement.stopOffset, replacement.text)
    }

    return result.toString()
}