/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HrefsTest {
    @Test
    fun `same-directory navigation does not prefix`() {
        // Both pages at `main/`, target is a sibling file in the same directory.
        assertThat(Hrefs.relativeFrom("main/index.html", "main/person.html")).isEqualTo("person.html")
    }

    @Test
    fun `descending into a subdirectory keeps no parent traversal`() {
        assertThat(Hrefs.relativeFrom("main/index.html", "main/person/name.html")).isEqualTo("person/name.html")
    }

    @Test
    fun `ascending to root prefixes with dot-dot for each level above`() {
        assertThat(Hrefs.relativeFrom("main/person/name.html", "index.html")).isEqualTo("../../index.html")
        assertThat(Hrefs.relativeFrom("main/person/index.html", "navigation.html")).isEqualTo("../../navigation.html")
    }

    @Test
    fun `sibling packages traverse up then down`() {
        assertThat(Hrefs.relativeFrom("main/index.html", "lib.lib1/index.html")).isEqualTo("../lib.lib1/index.html")
    }
}
