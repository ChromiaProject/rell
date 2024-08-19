/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.model.R_LangVersion

object RellVersions {
    const val VERSION_STR = "0.15.0"
    val VERSION = R_LangVersion.of(VERSION_STR)

    val SUPPORTED_VERSIONS: Set<R_LangVersion> =
            listOf(
                "0.6.0", "0.6.1",
                "0.7.0",
                "0.8.0",
                "0.9.0", "0.9.1",
                "0.10.0", "0.10.1", "0.10.2", "0.10.3", "0.10.4", "0.10.5", "0.10.6", "0.10.7", "0.10.8", "0.10.9",
                "0.10.10", "0.10.11",
                "0.11.0",
                "0.12.0",
                "0.13.0", "0.13.1", "0.13.2", "0.13.3", "0.13.4", "0.13.5", "0.13.6", "0.13.7", "0.13.8", "0.13.9",
                "0.13.10", "0.13.11", "0.13.12", "0.13.13", "0.13.14",
                "0.14.0",
                "0.15.0",
            )
            .map { R_LangVersion.of(it) }
            .toImmSet()

    val MAX_SUPPORTED_VERSION: R_LangVersion = SUPPORTED_VERSIONS.max()

    const val MODULE_SYSTEM_VERSION_STR = "0.10.0"

    private val MIN_COMPATIBILITY_VERSION = R_LangVersion.of("0.10.10")

    val MIN_COMPILER_VERSION: R_LangVersion by lazy { R_LangVersion.of("0.13.11") }

    /**
     * To be used in the library to specify a yet unknown next version.
     * Occurrences will be (manually) replaced with an actual version on release.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val SINCE_NOW = VERSION_STR

    init {
        check(VERSION in SUPPORTED_VERSIONS)
        check(MIN_COMPATIBILITY_VERSION in SUPPORTED_VERSIONS)
        check(MIN_COMPILER_VERSION in SUPPORTED_VERSIONS)
        check(R_LangVersion.of(MODULE_SYSTEM_VERSION_STR) in SUPPORTED_VERSIONS)
        check(R_LangVersion.of(SINCE_NOW) == VERSION)
    }

    fun checkCompatibilityVersion(version: R_LangVersion?, exception: (String) -> RuntimeException) {
        val minVer = MIN_COMPATIBILITY_VERSION
        if (version != null && version < minVer) {
            throw exception("Unsupported language version: $version (minimum supported version: $minVer)")
        }
    }

    /** Parses a version and checks that it's a known (supported) version. */
    fun parse(version: String): R_LangVersion {
        val rVersion = R_LangVersion.of(version)
        check(rVersion in SUPPORTED_VERSIONS) { "Unknown version: $rVersion" }
        return rVersion
    }
}
