/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.model.R_LangVersion

object RellVersions {
    const val VERSION_STR = "0.13.9"
    val VERSION = R_LangVersion.of(VERSION_STR)

    val SUPPORTED_VERSIONS: Set<R_LangVersion> =
            listOf(
                "0.10.0", "0.10.1", "0.10.2", "0.10.3", "0.10.4", "0.10.5", "0.10.6", "0.10.7", "0.10.8", "0.10.9",
                "0.10.10", "0.10.11",
                "0.11.0",
                "0.12.0",
                "0.13.0", "0.13.1", "0.13.2", "0.13.3", "0.13.4", "0.13.5", "0.13.6", "0.13.7", "0.13.8", "0.13.9",
            )
            .map { R_LangVersion.of(it) }
            .toImmSet()

    const val MODULE_SYSTEM_VERSION_STR = "0.10.0"
}
