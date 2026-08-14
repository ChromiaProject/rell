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
    private val retroactive: Boolean = false,
) {
    constructor(version: String, default: Boolean = true, retroactive: Boolean = false):
            this(R_LangVersion.of(version), default, retroactive)

    fun isActive(version: R_LangVersion?) = isActive(since, version, default)

    fun isActive(compilerOptions: C_CompilerOptions): Boolean {
        // A retroactive switch was added long after the behaviour it controls; code compiled before the switch
        // existed kept the new behaviour whatever version it declared, and must keep it, or a running blockchain
        // would change behaviour under its feet. See RellVersions.RETROACTIVE_GATES_VERSION.
        return if (retroactive && compilerOptions.predatesRetroactiveGates()) true else {
            isActive(compilerOptions.compatibility)
        }
    }

    companion object {
        fun isActive(since: R_LangVersion, version: R_LangVersion?, default: Boolean = true): Boolean =
            if (version != null) version >= since else default
    }
}
