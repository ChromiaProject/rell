/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.common

import java.net.URI

// Only the last segment of a URI may end up in logs: full paths leak user names and project layout.
fun URI.fileName(): String = path?.trimEnd('/')?.substringAfterLast('/').orEmpty()

fun offsetToPosition(content: String, offset: Int): Position {
    val contentLength = content.length
    if (offset !in 0..contentLength) {
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

internal const val NL = '\n'

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

    // Apply replacements in reverse order to avoid issues with changing offsets. Among replacements
    // starting at the same offset, the widest one goes first: an insertion at that offset must land
    // in front of the text it replaces, not inside it.
    val sortedReplacements = replacements
        .filter { isValidReplacement(it, source) }
        .sortedWith(compareByDescending<TextReplacement> { it.startOffset }.thenByDescending { it.stopOffset })

    // Replacements come from independent producers (formatter and linter rules), so their ranges can
    // overlap. Applying both would splice one into the middle of the other and corrupt the source, so
    // the first one wins and the conflicting ones are dropped.
    var appliedStart = source.length + 1

    for (replacement in sortedReplacements) {
        if (replacement.stopOffset > appliedStart) continue
        result.replace(replacement.startOffset, replacement.stopOffset, replacement.text)
        appliedStart = replacement.startOffset
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
