/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

data class LinterFix(
    /** User-facing action title describing what applying the fix does, e.g. "Replace 'var' with 'val'". */
    val title: String,
    val line: Int,
    val charPositionInLine: Int,
    val length: Int,
    val newText: String,
    // End position defaults to the same line, so single-line fixes don't need to spell it out.
    val endLine: Int = line,
    val endCharPositionInLine: Int = charPositionInLine + length,
)
