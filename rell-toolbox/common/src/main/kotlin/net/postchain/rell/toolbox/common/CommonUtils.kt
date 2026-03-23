/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.common

fun offsetToPosition(content: String, offset: Int): Position {
    val contentLength = content.length
    if (offset < 0 || offset > contentLength) {
        throw IndexOutOfBoundsException(
            "Offset $offset is out of bounds for range [0, $contentLength]"
        )
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
    throw IndexOutOfBoundsException(
        "Position $position out of bounds. content length: ${content.length}"
    )
}

fun applyTextReplacements(source: String, replacements: List<TextReplacement>): String {
    val result = StringBuilder(source)

    // Apply replacements in reverse order to avoid issues with changing offsets
    val sortedReplacements = replacements.sortedByDescending { it.startOffset }

    for (replacement in sortedReplacements) {
        if (isValidReplacement(replacement, source)) {
            result.replace(replacement.startOffset, replacement.stopOffset, replacement.text)
        }
    }

    return result.toString()
}

private fun isValidReplacement(replacement: TextReplacement, source: String) =
    when {
        replacement.startOffset < 0 -> false
        replacement.startOffset > source.length -> false
        replacement.stopOffset < replacement.startOffset -> false
        replacement.stopOffset > source.length -> false
        else -> true
    }
