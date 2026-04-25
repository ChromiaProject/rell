/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.expr.Db_BinaryOp
import net.postchain.rell.base.model.expr.Db_UnaryOp
import net.postchain.rell.base.runtime.Rt_StdlibEnv.Companion.forCompilation
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmMap

/**
 * Standard library environment — sys functions, DB operators.
 * Not serialized.
 *
 * DB binary/unary operator registries are process-global (classload-time registration, tables
 * are deterministic and type-keyed — no compile-specific state).
 *
 * Sys functions are per-compilation: meta-bodies capture `Rt_Type`/`FilePos`/other compile
 * context. Each compilation builds its own [Rt_StdlibEnv] by merging the classload-time DB ops
 * with the sys functions collected by that compilation's [Rt_ResolverRuntime]. Sharing sys-fn
 * registrations across compilations causes closure-capture leaks between unrelated tests.
 *
 * Binary/unary op evaluation is handled by standalone functions in rt_ops.kt —
 * no runtime dispatch map needed at call time, but the DB tables are still used by SQL gen.
 */
class Rt_StdlibEnv(
    val sysFunctions: ImmMap<String, R_SysFunction>,
    val dbBinaryOps: ImmMap<String, Db_BinaryOp>,
    val dbUnaryOps: ImmMap<String, Db_UnaryOp>,
) {
    companion object {
        private val globalBuilder = object {
            private val dbBinaryOps = java.util.concurrent.ConcurrentHashMap<String, Db_BinaryOp>()
            private val dbUnaryOps = java.util.concurrent.ConcurrentHashMap<String, Db_UnaryOp>()

            fun registerDbBinaryOp(key: String, op: Db_BinaryOp) {
                dbBinaryOps.putIfAbsent(key, op)
            }

            fun registerDbUnaryOp(key: String, op: Db_UnaryOp) {
                dbUnaryOps.putIfAbsent(key, op)
            }

            fun dbOps(): Pair<ImmMap<String, Db_BinaryOp>, ImmMap<String, Db_UnaryOp>> =
                dbBinaryOps.toImmMap() to dbUnaryOps.toImmMap()
        }

        fun registerDbBinaryOp(key: String, op: Db_BinaryOp) = globalBuilder.registerDbBinaryOp(key, op)
        fun registerDbUnaryOp(key: String, op: Db_UnaryOp) = globalBuilder.registerDbUnaryOp(key, op)

        /**
         * Build a [Rt_StdlibEnv] pairing the process-global DB op tables with the given
         * compilation-local sys-function map. [sysFns] comes from [Rt_ResolverRuntime.collectedSysFns].
         */
        fun forCompilation(sysFns: Map<String, R_SysFunction>): Rt_StdlibEnv {
            val (binOps, unOps) = globalBuilder.dbOps()
            return Rt_StdlibEnv(sysFns.toImmMap(), binOps, unOps)
        }

        /**
         * Empty sys-function env with process-global DB op tables. Useful as a fallback where
         * the compilation-local env isn't available (e.g. deserialization paths that haven't
         * been wired yet) — callers should prefer [forCompilation] when the compilation-local
         * map is available.
         */
        fun global(): Rt_StdlibEnv = forCompilation(emptyMap())
    }
}
