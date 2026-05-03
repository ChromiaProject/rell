/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_LangVersion

/** [default] is the status if compatibility version is not set, which is the case when the compiler (not source)
 * version is not specified (allowed for source versions < 0.13.11) */
class C_FeatureSwitch(
    private val since: R_LangVersion,
    private val default: Boolean = true,
) {
    constructor(version: String, default: Boolean = true): this(R_LangVersion.of(version), default)

    fun isActive(version: R_LangVersion?) = isActive(since, version, default)
    fun isActive(compilerOptions: C_CompilerOptions) = isActive(compilerOptions.compatibility)

    companion object {
        fun isActive(since: R_LangVersion, version: R_LangVersion?, default: Boolean = true): Boolean =
            if (version != null) version >= since else default
    }
}
