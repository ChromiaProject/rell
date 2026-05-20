/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.benchmarks

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf

abstract class RellBenchmarkBase {
    lateinit var app: R_App
        protected set

    lateinit var exeCtx: Rt_ExecutionContext
        protected set

    protected fun setUpApp(resourcePath: String): R_App {
        val rellSource = loadRellResource(resourcePath)
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to rellSource)
        val modSel = C_CompilerModuleSelection(immListOf(R_ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) {
            "Compilation errors: ${cRes.errors.joinToString("\n") { "${it.pos} ${it.code}: ${it.text}" }}"
        }

        app = cRes.app!!

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )
        val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, app)
        val sqlCtx = Rt_NullSqlContext.create(app)
        exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)
        return app
    }
}
