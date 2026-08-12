/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.chromia

import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions

/**
 * The compatibility version one project is analysed with, derived from its `compile.rellVersion`.
 *
 * The bundled compiler is always the one that runs; the declared version only selects which
 * version-gated language and library features it accepts, exactly as `C_CompilerOptions.compatibility`
 * does for `chr`. A declared version outside the range the compiler can honour is clamped into it
 * rather than refused, so every project gets analysis.
 */
data class RellCompatibility(
    /** The raw `compile.rellVersion` value, or null when the project declares none. */
    val declared: String?,
    /** The version to pass to the compiler as `C_CompilerOptions.compatibility`. */
    val version: R_LangVersion,
    val status: Status,
) {
    enum class Status {
        /** [declared] names a version the compiler can honour, and [version] is it. */
        DECLARED,

        /** No `compile.rellVersion`: the bundled compiler's own version applies. */
        ABSENT,

        /** [declared] is not a `major.minor.patch` version string. */
        MALFORMED,

        /** [declared] predates the oldest version the compiler can emulate. */
        TOO_OLD,

        /** [declared] is newer than the bundled compiler. */
        TOO_NEW,
    }

    companion object {
        /** The oldest compatibility version the compiler accepts. */
        val MIN_VERSION: R_LangVersion = RellVersions.MIN_COMPATIBILITY_VERSION

        /** The bundled compiler's own version — the newest compatibility mode it can offer. */
        val MAX_VERSION: R_LangVersion = RellVersions.VERSION

        fun resolve(declared: String?): RellCompatibility {
            val trimmed = declared?.trim()?.takeIf { it.isNotEmpty() }
                ?: return RellCompatibility(null, MAX_VERSION, Status.ABSENT)

            val parsed = try {
                R_LangVersion.of(trimmed)
            } catch (_: IllegalArgumentException) {
                return RellCompatibility(declared, MAX_VERSION, Status.MALFORMED)
            }

            return when {
                parsed < MIN_VERSION -> RellCompatibility(declared, MIN_VERSION, Status.TOO_OLD)
                parsed > MAX_VERSION -> RellCompatibility(declared, MAX_VERSION, Status.TOO_NEW)
                else -> RellCompatibility(declared, parsed, Status.DECLARED)
            }
        }
    }
}
