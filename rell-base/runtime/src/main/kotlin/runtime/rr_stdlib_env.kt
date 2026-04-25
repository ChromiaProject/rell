/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.immMapOf

/**
 * Standard library environment — sys functions only.
 *
 * Sys functions are per-compilation: meta-bodies capture `Rt_Type`/`FilePos`/other compile
 * context. Each compilation builds its own [Rt_StdlibEnv] from the sys functions collected
 * by that compilation's [Rt_ResolverRuntime]. Sharing sys-fn registrations across compilations
 * causes closure-capture leaks between unrelated tests.
 *
 * Binary/unary op evaluation is handled by standalone functions in rt_ops.kt; SQL operator
 * strings are baked into the RR tree at resolution time, so no runtime registry is needed.
 */
class Rt_StdlibEnv(val sysFunctions: ImmMap<String, R_SysFunction>) {
    companion object {
        /**
         * Empty sys-function env. Useful as a fallback where the compilation-local env isn't
         * available (e.g. deserialization paths that haven't been wired yet) — callers should
         * construct [Rt_StdlibEnv] directly with the compilation-local map when available.
         */
        fun global(): Rt_StdlibEnv = Rt_StdlibEnv(immMapOf())
    }
}
