/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.rr.RR_App
object RellGtxConfigConstants {
    const val LANG_VERSION_KEY = "version"
    const val COMPILER_VERSION_KEY = "compilerVersion"
    const val SOURCES_KEY = "sources"
    const val FILES_KEY = "files"
}

class RellGtxModuleApp(
    val rrApp: RR_App,
    val compilerOptions: C_CompilerOptions,
    /**
     * Sys-function registrations produced by the compilation that yielded [rrApp]. Must be
     * passed to `Rt_InterpreterImpl.forCompilation` — stdlib meta-bodies (`log()`, `gtv_ext`, ...)
     * capture compile-specific state, so sharing the registry across compilations corrupts
     * closures.
     */
    val compilationSysFns: Map<String, Any> = emptyMap(),
)
