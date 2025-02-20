/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.api.base.RellApiBaseInternal
import net.postchain.rell.api.base.RellApiBaseUtils
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.runtime.Rt_LogPrinter
import net.postchain.rell.base.runtime.Rt_OutPrinter
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.sql.SqlInitLogging
import net.postchain.rell.base.sql.SqlInterceptor
import net.postchain.rell.base.utils.*
import java.io.File

class SqlExecutionEvent(
    val startTimeMs: Long,
    val durationMs: Long,
    val sql: String,
    val isSystem: Boolean,
    val parameters: List<Any?>,
    val rowCount: Int?,
    val error: Exception?,
) {
    init {
        require(startTimeMs > 0) { startTimeMs }
        require(durationMs >= 0) { durationMs }
        require((rowCount ?: 0) >= 0) { "$rowCount" }
    }
}

object RellApiRunTests {
    /**
     * Run tests.
     *
     * Use-case 1: run tests same way as **`multirun`** does. Set [appModules] to the app's main module, add the
     * main module to [testModules], set [compileConfig.includeTestSubModules][RellApiCompile.Config.includeTestSubModules]
     * to `true`.
     *
     * Use-case 2: run all tests. Add the *root* module (`""`) to [testModules],
     * set [compileConfig.includeTestSubModules][RellApiCompile.Config.includeTestSubModules] to `true`.
     *
     * @param config Configuration.
     * @param sourceDir Source directory.
     * @param appModules List of app modules. Empty means none, `null` means all. Defines active modules for blocks
     * execution (tests can execute only operations defined in active modules).
     * @param testModules List of test modules to run. Empty means none. Can contain also app modules, if
     * [compileConfig.includeTestSubModules][RellApiCompile.Config.includeTestSubModules] is `true`.
     */
    fun runTests(
        config: Config,
        sourceDir: File,
        appModules: List<String>?,
        testModules: List<String>,
    ): UnitTestRunnerResults {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rAppModules = appModules?.map { R_ModuleName.of(it) }?.toImmList()
        val rTestModules = testModules.map { R_ModuleName.of(it) }.toImmList()

        val compileConfig = config.compileConfig
        val options = RellApiGtxInternal.makeRunTestsCompilerOptions(config)
        val (_, app) = RellApiBaseInternal.compileApp(compileConfig, options, cSourceDir, rAppModules, rTestModules)

        return RellApiGtxInternal.runTests(config, options, cSourceDir, app, rAppModules)
    }

    class Config(
        /** Compilation config. */
        val compileConfig: RellApiCompile.Config,
        /** CLI environment used to print tests execution progress (test cases) and results. */
        val cliEnv: RellCliEnv,
        /** Stop tests after the first error. */
        val stopOnError: Boolean,
        /** Database URL. */
        val databaseUrl: String?,
        /** Enable SQL logging. */
        val sqlLog: Boolean,
        /** Enable SQL error logging. */
        val sqlErrorLog: Boolean,
        /** List of glob patterns to filter test cases: when not `null`, only tests matching one of the patterns will be executed. */
        val testPatterns: List<String>?,
        /** Printer used for Rell `print()` calls. */
        val outPrinter: Rt_Printer,
        /** Printer used for Rell `log()` calls. */
        val logPrinter: Rt_Printer,
        /** Print test case names and results during the execution. */
        val printTestCases: Boolean,
        /** Print large values pretty-formatted (e.g. when a test fails because of assert_equals). */
        val printPrettyLargeValues: Boolean,
        /** Add dependencies of test modules to the set of active modules of the app (default: `true`).
         * Affects available operations and function extensions. */
        val activateTestDependencies: Boolean,
        /** Test case start callback. */
        val onTestCaseStart: (UnitTestCase) -> Unit,
        /** Test case finished callback. */
        val onTestCaseFinished: (UnitTestCaseResult) -> Unit,
        /** SQL execution finished callback. */
        val onSqlExecutionFinished: ((SqlExecutionEvent) -> Unit)?,
    ) {
        fun toBuilder() = Builder(this)

        companion object {
            val DEFAULT = Config(
                compileConfig = RellApiCompile.Config.DEFAULT,
                cliEnv = RellCliEnv.DEFAULT,
                stopOnError = false,
                databaseUrl = null,
                sqlLog = false,
                sqlErrorLog = false,
                testPatterns = null,
                outPrinter = Rt_OutPrinter,
                logPrinter = Rt_LogPrinter(),
                printTestCases = true,
                printPrettyLargeValues = true,
                activateTestDependencies = true,
                onTestCaseStart = {},
                onTestCaseFinished = {},
                onSqlExecutionFinished = null,
            )
        }

        class Builder(proto: Config = DEFAULT) {
            private var compileConfig = proto.compileConfig
            private var cliEnv = proto.cliEnv
            private var stopOnError = proto.stopOnError
            private var databaseUrl = proto.databaseUrl
            private var sqlLog = proto.sqlLog
            private var sqlErrorLog = proto.sqlErrorLog
            private var testPatterns = proto.testPatterns
            private var outPrinter = proto.outPrinter
            private var logPrinter = proto.logPrinter
            private var printTestCases = proto.printTestCases
            private var printPrettyLargeValues = proto.printPrettyLargeValues
            private var activateTestDependencies = proto.activateTestDependencies
            private var onTestCaseStart = proto.onTestCaseStart
            private var onTestCaseFinished = proto.onTestCaseFinished
            private var onSqlExecutionFinished = proto.onSqlExecutionFinished

            /** @see [Config.compileConfig] */
            fun compileConfig(v: RellApiCompile.Config) = apply { compileConfig = v }

            /** @see [Config.cliEnv] */
            fun cliEnv(v: RellCliEnv) = apply { cliEnv = v }

            /** @see [Config.stopOnError] */
            fun stopOnError(v: Boolean) = apply { stopOnError = v }

            /** @see [Config.databaseUrl] */
            fun databaseUrl(v: String?) = apply { databaseUrl = v }

            /** @see [Config.sqlLog] */
            fun sqlLog(v: Boolean) = apply { sqlLog = v }

            /** @see [Config.sqlErrorLog] */
            fun sqlErrorLog(v: Boolean) = apply { sqlErrorLog = v }

            /** @see [Config.testPatterns] */
            fun testPatterns(v: List<String>?) = apply { testPatterns = v?.toImmList() }

            /** @see [Config.outPrinter] */
            fun outPrinter(v: Rt_Printer) = apply { outPrinter = v }

            /** @see [Config.logPrinter] */
            fun logPrinter(v: Rt_Printer) = apply { logPrinter = v }

            /** @see [Config.printTestCases] */
            fun printTestCases(v: Boolean) = apply { printTestCases = v }

            /**  @see [Config.printPrettyLargeValues]] */
            fun printPrettyLargeValues(v: Boolean) = apply { printPrettyLargeValues = v }

            /** @see [Config.activateTestDependencies]  */
            fun activateTestDependencies(v: Boolean) = apply { activateTestDependencies = v }

            /** @see [Config.onTestCaseStart] */
            fun onTestCaseStart(v: (UnitTestCase) -> Unit) = apply { onTestCaseStart = v }

            /** @see [Config.onTestCaseFinished] */
            fun onTestCaseFinished(v: (UnitTestCaseResult) -> Unit) = apply { onTestCaseFinished = v }

            /** @see [Config.onSqlExecutionFinished] */
            fun onSqlExecutionFinished(v: (SqlExecutionEvent) -> Unit) = apply { onSqlExecutionFinished = v }

            fun build(): Config {
                return Config(
                    compileConfig = compileConfig,
                    cliEnv = cliEnv,
                    stopOnError = stopOnError,
                    databaseUrl = databaseUrl,
                    sqlLog = sqlLog,
                    sqlErrorLog = sqlErrorLog,
                    testPatterns = testPatterns?.toImmList(),
                    outPrinter = outPrinter,
                    logPrinter = logPrinter,
                    printTestCases = printTestCases,
                    printPrettyLargeValues = printPrettyLargeValues,
                    activateTestDependencies = activateTestDependencies,
                    onTestCaseStart = onTestCaseStart,
                    onTestCaseFinished = onTestCaseFinished,
                    onSqlExecutionFinished = onSqlExecutionFinished,
                )
            }
        }
    }
}

internal object RellApiGtxInternal {
    fun runTests(
        config: RellApiRunTests.Config,
        options: C_CompilerOptions,
        sourceDir: C_SourceDir,
        app: R_App,
        appModules: List<R_ModuleName>?,
    ): UnitTestRunnerResults {
        val globalCtx = RellApiBaseUtils.createGlobalContext(
            options,
            typeCheck = false,
            outPrinter = config.outPrinter,
            logPrinter = config.logPrinter,
        )

        val sqlInterceptor = config.onSqlExecutionFinished?.let { ListeningSqlInterceptor(it) }
        val blockRunner = createBlockRunner(config, sourceDir, app, appModules, sqlInterceptor)

        val sqlCtx = RellApiBaseUtils.createSqlContext(app)
        val chainCtx = RellApiBaseUtils.createChainContext()

        val testMatcher = if (config.testPatterns == null) UnitTestMatcher.ANY else UnitTestMatcher.make(config.testPatterns)
        val testFns = UnitTestRunner.getTestFunctions(app, testMatcher)
        val testCases = testFns.map { UnitTestCase(null, it) }

        return RellApiGtxUtils.runWithSqlManager(
            dbUrl = config.databaseUrl,
            sqlLog = config.sqlLog,
            sqlErrorLog = config.sqlErrorLog,
            sqlInterceptor = sqlInterceptor,
        ) { sqlMgr ->
            val testCtx = UnitTestRunnerContext(
                app = app,
                printer = config.cliEnv::print,
                sqlCtx = sqlCtx,
                sqlMgr = sqlMgr,
                sqlInitProjExt = PostchainSqlInitProjExt,
                globalCtx = globalCtx,
                chainCtx = chainCtx,
                blockRunner = blockRunner,
                moduleArgsSource = Rt_GtvModuleArgsSource(config.compileConfig.moduleArgs, options),
                printTestCases = config.printTestCases,
                printPrettyLargeValues = config.printPrettyLargeValues,
                stopOnError = config.stopOnError,
                onTestCaseStart = config.onTestCaseStart,
                onTestCaseFinished = config.onTestCaseFinished,
            )

            val testRes = UnitTestRunnerResults(testCtx.printPrettyLargeValues)
            UnitTestRunner.runTests(testCtx, testCases, testRes)
            testRes
        }
    }

    private fun createBlockRunner(
        config: RellApiRunTests.Config,
        sourceDir: C_SourceDir,
        app: R_App,
        appModules: List<R_ModuleName>?,
        sqlInterceptor: SqlInterceptor?,
    ): Rt_UnitTestBlockRunner {
        val keyPair = Lib_RellTest.BLOCK_RUNNER_KEYPAIR

        val blockRunnerCfg = Rt_BlockRunnerConfig(
            forceTypeCheck = false,
            sqlLog = config.sqlLog,
            dbInitLogLevel = SqlInitLogging.LOG_NONE,
            sqlInterceptor = sqlInterceptor,
        )

        val mainModules = when {
            appModules == null -> null
            config.activateTestDependencies -> (appModules + RellApiBaseUtils.getMainModules(app)).toSet().toImmList()
            else -> appModules
        }

        val gtvCompileConfig = RellApiCompile.Config.Builder(config.compileConfig)
            .quiet(true)
            .build()

        val blockRunnerStrategy = Rt_DynamicBlockRunnerStrategy(sourceDir, keyPair, mainModules, gtvCompileConfig)
        return Rt_PostchainUnitTestBlockRunner(keyPair, blockRunnerCfg, blockRunnerStrategy)
    }

    fun makeRunTestsCompilerOptions(config: RellApiRunTests.Config): C_CompilerOptions {
        return RellApiBaseInternal.makeCompilerOptions(config.compileConfig)
            .toBuilder()
            .useTestDependencyExtensions(config.activateTestDependencies)
            .build()
    }
}
