/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx.testutils

import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.gtx.*
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.repl.ReplInterpreterProjExt
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlInitProjExt
import net.postchain.rell.base.testutils.RellTestProjExt
import net.postchain.rell.base.utils.Rt_UnitTestBlockRunner
import net.postchain.rell.gtx.PostchainBaseUtils

object PostchainRellTestProjExt: RellTestProjExt() {
    private val sqlInitProjExt: SqlInitProjExt = PostchainSqlInitProjExt

    override fun getSqlInitProjExt() = sqlInitProjExt

    override fun getReplInterpreterProjExt(): ReplInterpreterProjExt {
        val runnerConfig = createBlockRunnerConfig()
        return PostchainReplInterpreterProjExt(sqlInitProjExt, runnerConfig)
    }

    override fun initSysAppTables(sqlExec: SqlExecutor) {
        val sqlAccess = RellApiGtxUtils.createDatabaseAccess()
        sqlExec.connection { con ->
            PostchainBaseUtils.initializeApp(sqlAccess, con)
        }
    }

    override fun createUnitTestBlockRunner(
        sourceDir: C_SourceDir,
        app: R_App,
        moduleArgs: Map<R_ModuleName, Gtv>,
    ): Rt_UnitTestBlockRunner {
        val keyPair = Lib_RellTest.BLOCK_RUNNER_KEYPAIR
        val blkRunConfig = createBlockRunnerConfig()

        val modules = app.modules.filter { !it.test && !it.abstract && !it.external }.map { it.name }
        val compileConfig = RellApiCompile.Config.Builder().moduleArgs0(moduleArgs).build()
        val blkRunStrategy = Rt_DynamicBlockRunnerStrategy(sourceDir, keyPair, modules, compileConfig)

        return Rt_PostchainUnitTestBlockRunner(keyPair, blkRunConfig, blkRunStrategy)
    }

    private fun createBlockRunnerConfig(): Rt_BlockRunnerConfig {
        return Rt_BlockRunnerConfig(
            wrapCtErrors = false,
            wrapRtErrors = false,
            forceTypeCheck = true,
        )
    }
}
