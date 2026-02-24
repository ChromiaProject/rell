/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import net.postchain.rell.base.compiler.parser.RellTokens

fun main() {
    val tokens = RellTokens.DEFAULT
    for (t in tokens.keywords.sortedBy { it.pattern }) {
        println(t.pattern)
    }
}
