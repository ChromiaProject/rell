/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.chromia

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.toolbox.chromia.RellCompatibility.Status
import org.junit.jupiter.api.Test

class RellCompatibilityTest {

    @Test
    fun `no declared version falls back to the bundled compiler`() {
        chk(null, RellVersions.VERSION, Status.ABSENT)
        chk("", RellVersions.VERSION, Status.ABSENT)
        chk("   ", RellVersions.VERSION, Status.ABSENT)
    }

    @Test
    fun `a supported version is used as declared`() {
        chk("0.16.1", R_LangVersion.of("0.16.1"), Status.DECLARED)
        chk("0.13.11", R_LangVersion.of("0.13.11"), Status.DECLARED)
        chk(" 0.15.0 ", R_LangVersion.of("0.15.0"), Status.DECLARED)
    }

    @Test
    fun `the bundled version itself is honoured`() {
        chk(RellVersions.VERSION_STR, RellVersions.VERSION, Status.DECLARED)
    }

    @Test
    fun `a version older than the compiler can emulate is clamped up`() {
        chk("0.10.0", RellCompatibility.MIN_VERSION, Status.TOO_OLD)
        chk("0.6.0", RellCompatibility.MIN_VERSION, Status.TOO_OLD)
    }

    @Test
    fun `a version newer than the bundled compiler is clamped down`() {
        chk("99.0.0", RellVersions.VERSION, Status.TOO_NEW)
    }

    @Test
    fun `a value that is not a version falls back to the bundled compiler`() {
        // Two components is what a bare `rellVersion: 0.16` in YAML yields.
        chk("0.16", RellVersions.VERSION, Status.MALFORMED)
        chk("latest", RellVersions.VERSION, Status.MALFORMED)
        chk("0.16.1-SNAPSHOT", RellVersions.VERSION, Status.MALFORMED)
    }

    @Test
    fun `the declared value is kept verbatim for reporting`() {
        assertThat(RellCompatibility.resolve(" 0.16 ").declared).isEqualTo(" 0.16 ")
        assertThat(RellCompatibility.resolve(null).declared).isEqualTo(null)
    }

    private fun chk(declared: String?, expVersion: R_LangVersion, expStatus: Status) {
        val actual = RellCompatibility.resolve(declared)
        assertThat(actual.version to actual.status).isEqualTo(expVersion to expStatus)
    }
}
