/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_InterpreterImpl

/**
 * Turns a compiled [RR_App] (with its compilation-local sys-function registry) into an
 * [Rt_Interpreter]. Pluggable, so a caller can inject an alternative execution backend.
 */
public typealias RellInterpreterFactory = (rrApp: RR_App, compilationSysFns: Map<String, Any>) -> Rt_Interpreter

/**
 * Selects the Rell execution backend.
 *
 * The default backend is the tree-walking interpreter ([Rt_InterpreterImpl]). The experimental
 * Truffle backend can be opted in — without recompiling or reconfiguring any consumer — via the
 * `-D[SYSTEM_PROPERTY]=truffle` JVM system property.
 *
 * Backend selection is reflective: neither this module nor any API consumer compile-depends on
 * `runtime-truffle`. The module only has to be on the runtime classpath, where it rides along as a
 * `runtimeOnly` dependency of `rell-api-base`.
 *
 * This is a developer/testing switch; it is intentionally kept out of the public, documented API.
 */
@InternalRellApi
public object RellApiInterpreterBackend {
    /** JVM system property selecting the backend: `interpreter` (default) or `truffle`. */
    private const val SYSTEM_PROPERTY: String = "rell.execution.backend"

    public val DEFAULT: RellInterpreterFactory = RellApiInterpreterBackend::create

    /**
     * Creates an interpreter for [rrApp], picking the backend per [SYSTEM_PROPERTY].
     */
    public fun create(rrApp: RR_App, compilationSysFns: Map<String, Any>): Rt_Interpreter =
        when (val backend = System.getProperty(SYSTEM_PROPERTY).orEmpty().trim().lowercase()) {
            "", "interpreter" -> Rt_InterpreterImpl.forCompilation(rrApp, compilationSysFns)
            "truffle" -> {
                val backendClass = try {
                    Class.forName("net.postchain.rell.base.runtime.truffle.Tf_Backend")
                } catch (e: ClassNotFoundException) {
                    throw IllegalStateException(
                        "Truffle backend selected via -D${SYSTEM_PROPERTY}=truffle, but class is not found",
                        e,
                    )
                }
                val factory = backendClass.getMethod("forCompilation", RR_App::class.java, Map::class.java)
                factory.invoke(null, rrApp, compilationSysFns) as Rt_Interpreter
            }
            else -> throw IllegalArgumentException(
                "Unsupported $SYSTEM_PROPERTY value: '$backend' (expected 'interpreter' or 'truffle')",
            )
        }

}
