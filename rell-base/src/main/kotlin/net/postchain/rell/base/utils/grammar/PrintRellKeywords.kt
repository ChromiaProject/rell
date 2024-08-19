/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import net.postchain.rell.base.compiler.parser.S_Grammar

fun main() {
    val tokenizer = S_Grammar.tokenizer
    for (t in tokenizer.tkKeywords.values.sortedBy { it.pattern }) {
        println(t.pattern)
    }
}
