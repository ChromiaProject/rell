/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.repl.ReplInterpreterProjExt
import net.postchain.rell.base.sql.SqlInitProjExt
import net.postchain.rell.base.utils.Rt_UnitTestBlockRunner
import net.postchain.rell.base.utils.toImmList

public class PostchainReplInterpreterProjExt(
    private val sqlInitProjExt: SqlInitProjExt,
    private val runnerConfig: Rt_BlockRunnerConfig,
    private val compileConfig: RellApiCompile.Config = RellApiCompile.Config.DEFAULT,
): ReplInterpreterProjExt {
    override fun getSqlInitProjExt(): SqlInitProjExt = sqlInitProjExt

    override fun createBlockRunner(sourceDir: C_SourceDir, modules: List<ModuleName>): Rt_UnitTestBlockRunner {
        val keyPair = Lib_RellTest.BLOCK_RUNNER_KEYPAIR
        val compileConfigNullCliEnv = compileConfig.toBuilder().cliEnv(RellCliEnv.NULL).build()

        val runnerStrategy =
            Rt_DynamicBlockRunnerStrategy(sourceDir, keyPair, modules.toImmList(), compileConfigNullCliEnv)
        return Rt_PostchainUnitTestBlockRunner(keyPair, runnerConfig, runnerStrategy)
    }
}
