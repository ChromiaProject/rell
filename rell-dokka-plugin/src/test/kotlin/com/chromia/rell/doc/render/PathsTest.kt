/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class PathsTest {

    @Test
    fun `fileSlug lowercases and dash-prefixes uppercase letters`() {
        assertThat(Paths.fileSlug("model")).isEqualTo("model")
        assertThat(Paths.fileSlug("My Dapp")).isEqualTo("-my -dapp")
        assertThat(Paths.fileSlug("Rell System Library API Reference"))
            .isEqualTo("-rell -system -library -a-p-i -reference")
        assertThat(Paths.fileSlug("INT_DIGITS")).isEqualTo("-i-n-t_-d-i-g-i-t-s")
    }

    @Test
    fun `urlEncodeName escapes hash for anonymous functions`() {
        assertThat(Paths.urlEncodeName("function#0")).isEqualTo("function%230")
        assertThat(Paths.urlEncodeName("plain")).isEqualTo("plain")
    }
}
