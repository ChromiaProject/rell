/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.api.base.BaseRellApiTest
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import kotlin.test.assertEquals

internal abstract class BaseRellApiRunTestsTest: BaseRellApiTest() {
    protected fun runTestsConfig(
        compileConfig: RellApiCompile.Config.Builder,
        proto: RellApiRunTests.Config = RellApiRunTests.Config.DEFAULT,
    ): RellApiRunTests.Config {
        return proto.toBuilder().compileConfig(compileConfig.build()).build()
    }

    protected fun runTestsDbConfig(): RellApiRunTests.Config {
        val (handle, url) = SqlTestUtils.createTempDbUrl()
        resource(handle)

        return RellApiRunTests.Config.Builder()
            .databaseUrl(url)
            .build()
    }

    protected fun chkRunTests(
        config: RellApiRunTests.Config,
        sourceDir: C_SourceDir,
        appModules: List<String>?,
        testModules: List<String>,
        vararg expected: String,
    ) {
        val actualList = runTests(config, sourceDir, appModules, testModules)
        assertEquals(expected.toList(), actualList)
    }

    protected fun runTests(
        config: RellApiRunTests.Config,
        sourceDir: C_SourceDir,
        appModules: List<String>?,
        testModules: List<String>,
    ): List<String> {
        val appMods = appModules?.mapToImmList { R_ModuleName.of(it) }
        val testMods = testModules.map { R_ModuleName.of(it) }

        val options: C_CompilerOptions

        val apiRes = try {
            options = RellApiGtxInternal.makeRunTestsCompilerOptions0(config)
            compileApp0(config.compileConfig, options, sourceDir, appMods, testMods)
        } catch (e: C_CommonError) {
            return listOf("CME:${e.code}")
        }

        val cRes = apiRes.cRes
        val ctErr = handleCompilationError(cRes)
        if (ctErr != null) return listOf(ctErr)
        val rApp = cRes.app!!

        val actualList = mutableListOf<String>()
        val config2 = config.toBuilder()
            .onTestCaseFinished {
                config.onTestCaseFinished(it)
                actualList.add("${it.case.name}:${it.res}")
            }
            .build()

        val res = RellApiGtxInternal.runTests(config2, options, sourceDir, rApp, appMods)
        val resList = res.getResults().map { "${it.case.name}:${it.res}" }

        assertEquals(actualList, resList)
        return actualList.toImmList()
    }
}
