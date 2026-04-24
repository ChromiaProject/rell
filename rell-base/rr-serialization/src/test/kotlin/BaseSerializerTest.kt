/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import rell.ir.App
import java.nio.ByteBuffer
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class BaseSerializerTest {
    protected fun compileApp(code: String): RR_App = compileAppWithSysFns(code).first

    /** Returns (rrApp, compilationSysFns) - use when the test needs the per-compilation stdlib. */
    protected fun compileAppWithSysFns(code: String): Pair<RR_App, Map<String, Any>> {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val result = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        assertTrue(result.errors.isEmpty(), "Compilation errors: ${result.errors.map { it.code }}")
        return result.rrApp!! to result.compilationSysFns
    }

    protected fun serializeAndParse(code: String): App {
        val bytes = serializeRellApp(compileApp(code))
        assertFalse(bytes.isEmpty(), "Serialized bytes are empty")
        return App.getRootAsApp(ByteBuffer.wrap(bytes))
    }
}
