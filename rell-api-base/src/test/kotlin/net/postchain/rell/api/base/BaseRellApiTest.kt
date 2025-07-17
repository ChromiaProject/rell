/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseResourcefulTest
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.TestSnippetsRecorder
import net.postchain.rell.base.utils.immListOf

public abstract class BaseRellApiTest : BaseResourcefulTest() {
    protected val generalSourceDir: C_SourceDir = C_SourceDir.mapDirOf(
        "a.rell" to "module;",
        "b1/module.rell" to "module;",
        "b1/b2.rell" to "module;",
        "c.rell" to "module;",
        "d.rell" to "@test module; import c;",
        "e1/module.rell" to "module;",
        "e1/e2/module.rell" to "@test module;",
        "e1/e2/e3.rell" to "@test module;",
    )

    protected val defaultConfig: RellApiCompile.Config = configBuilder().build()

    protected fun configBuilder(): RellApiCompile.Config.Builder = RellApiCompile.Config.Builder()

    // Important to call this function instead of calling the API directly - to record test snippets.
    protected fun compileApp0(
        config: RellApiCompile.Config,
        options: C_CompilerOptions,
        sourceDir: C_SourceDir,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName> = immListOf(),
    ): RellApiCompilationResult {
        val apiRes = RellApiBaseInternal.compileApp0(config, options, sourceDir, appModules, testModules)
        val modSel = RellApiBaseInternal.makeCompilerModuleSelection(config, appModules, testModules)
        TestSnippetsRecorder.recordParsing(sourceDir, modSel, options, apiRes.cRes)
        return apiRes
    }

    protected fun handleCompilationError(cRes: C_CompilationResult): String? {
        return when {
            cRes.errors.isNotEmpty() -> "CTE:${RellTestUtils.msgsToString(cRes.errors, false)}"
            cRes.app == null -> "ERR:no_app"
            else -> null
        }
    }
}
