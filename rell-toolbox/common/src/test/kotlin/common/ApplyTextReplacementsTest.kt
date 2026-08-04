/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ApplyTextReplacementsTest {
    @Test
    fun `should apply disjoint replacements`() {
        val source = "var x = 5;"
        val result = applyTextReplacements(
            source,
            listOf(TextReplacement(0, 3, "val"), TextReplacement(8, 9, "7"))
        )
        assertThat(result).isEqualTo("val x = 7;")
    }

    @Test
    fun `should place an insertion in front of a replacement starting at the same offset`() {
        val source = "var x = 5;"
        val result = applyTextReplacements(
            source,
            listOf(TextReplacement(0, 0, "    "), TextReplacement(0, 3, "val"))
        )
        assertThat(result).isEqualTo("    val x = 5;")
    }

    @Test
    fun `should drop a replacement overlapping an already applied one`() {
        val source = "var x = 5;"
        val result = applyTextReplacements(
            source,
            listOf(TextReplacement(0, 5, "let y"), TextReplacement(4, 9, "z = 7"))
        )
        assertThat(result).isEqualTo("var z = 7;")
    }

    @Test
    fun `should ignore replacements with out-of-bounds offsets`() {
        val source = "var x = 5;"
        val result = applyTextReplacements(
            source,
            listOf(TextReplacement(-1, 3, "val"), TextReplacement(8, 99, "7"), TextReplacement(5, 4, "?"))
        )
        assertThat(result).isEqualTo(source)
    }
}
