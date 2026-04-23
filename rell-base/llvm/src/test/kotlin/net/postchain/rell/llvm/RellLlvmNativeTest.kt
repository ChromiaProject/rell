/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.serialization.serializeRellApp
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RellLlvmNativeTest {
    @Test
    fun `summarizeApp returns module list from FlatBuffers-serialized RR_App`() {
        // Single root module — same pattern as IntegrationSerializerTest. The root module has an
        // empty `parts` list, so its rendered name is the empty string and the summary contains
        // a bare "- \n" line for it.
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to "function noop() {}")
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val result = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        assertTrue(result.errors.isEmpty(), "Compilation errors: ${result.errors.map { it.code }}")
        val rrApp = checkNotNull(result.rrApp) { "compileApp returned null RR_App" }

        val bytes = serializeRellApp(rrApp)
        val summary = RellLlvmNative.summarizeApp(bytes)

        assertContains(summary, "llvm=")
        assertContains(summary, "modules=1")
        assertContains(summary, "- \n")
    }
}
