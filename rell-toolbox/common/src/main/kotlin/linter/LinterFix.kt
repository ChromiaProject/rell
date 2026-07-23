/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

data class LinterFix(
    val line: Int,
    val charPositionInLine: Int,
    val length: Int,
    val newText: String,
    // End position defaults to the same line, so single-line fixes don't need to spell it out.
    val endLine: Int = line,
    val endCharPositionInLine: Int = charPositionInLine + length,
)
