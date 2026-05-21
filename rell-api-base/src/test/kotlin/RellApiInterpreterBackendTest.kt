/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.rell.base.model.rr.RR_App
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the reflective contract [RellApiInterpreterBackend] relies on to wire the Truffle
 * execution backend (`-Drell.execution.backend=truffle`). If this test fails, the dev switch is
 * silently broken: either the `runtime-truffle` `runtimeOnly` dependency dropped off the
 * classpath, or `Tf_Backend.forCompilation` was renamed, lost `@JvmStatic`, or changed parameters.
 */
class RellApiInterpreterBackendTest {
    @Test
    fun testTruffleBackendFactoryReflectivelyResolvable() {
        val backendClass = Class.forName("net.postchain.rell.base.runtime.truffle.Tf_Backend")
        val factory = backendClass.getMethod("forCompilation", RR_App::class.java, Map::class.java)
        assertTrue(Modifier.isStatic(factory.modifiers), "Tf_Backend.forCompilation must be @JvmStatic")
    }
}
