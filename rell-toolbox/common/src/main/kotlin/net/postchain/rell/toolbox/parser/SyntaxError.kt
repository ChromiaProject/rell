/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

data class SyntaxError(
    val message: String?,
    val line: Int,
    val charPositionInLine: Int,
    val sourceName: String?
)
