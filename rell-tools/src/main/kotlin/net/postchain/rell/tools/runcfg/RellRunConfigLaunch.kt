/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import mu.KotlinLogging
import mu.withLoggingContext
import net.postchain.PostchainNode
import net.postchain.api.internal.BlockchainApi
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadWriteConnection
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.rell.api.base.RellApiBaseUtils
import net.postchain.rell.api.base.RellCliBasicException
import net.postchain.rell.api.base.RellCliExitException
import net.postchain.rell.api.gtx.*
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlInitLogging
import net.postchain.rell.base.utils.*
import net.postchain.rell.gtx.PostchainBaseUtils
import net.postchain.rell.module.RellPostchainModuleEnvironment
import net.postchain.rell.tools.RellToolsLogUtils
import net.postchain.rell.tools.RellToolsUtils
import org.apache.commons.configuration2.PropertiesConfiguration
import picocli.CommandLine
import java.io.File
import java.io.StringReader
import java.util.*

private val log = run {
    RellToolsLogUtils.initLogging()
    KotlinLogging.logger("PostchainApp")
}

fun main(args: Array<String>) {
    RellToolsUtils.runCli(args, RellRunConfigLaunchCliArgs(), ::main0)
}

private fun main0(args: RellRunConfigLaunchCliArgs) {
    val runConfigFile = RellToolsUtils.checkFile(args.runConfigFile)
    val sourceDir = RellToolsUtils.checkDir(args.sourceDir ?: ".").absoluteFile
    val sourceVer = RellToolsUtils.checkVersion(args.sourceVersion)
    val commonArgs = CommonArgs(runConfigFile, sourceDir, sourceVer, args.sqlLog)

    if (args.test) {
        val filter = args.testFilter
        val targetChains = args.getTargetChains()
        val matcher = if (filter == null) UnitTestMatcher.ANY else {
            val patterns = filter.split(",")
            UnitTestMatcher.make(patterns)
        }
        runTests(commonArgs, matcher, targetChains)
    } else {
        val env = RellPostchainModuleEnvironment(sqlLog = args.sqlLog)
        RellPostchainModuleEnvironment.set(env) {
            runApp(commonArgs)
        }
    }
}

private fun runApp(args: CommonArgs) {
    log.info("STARTING POSTCHAIN APP")
    log.info("    source directory: ${args.sourceDir.absolutePath}")
    log.info("    run config file: ${args.runConfigFile.absolutePath}")
    log.info("")

    RellToolsUtils.printVersionInfo()

    val rellAppConf = generateRunConfig(args, false)

    // Make sure that all sources compile before trying to start a node.
    for (chain in rellAppConf.config.chains) {
        val modules = chain.modules.toImmList()
        val modSel = C_CompilerModuleSelection(modules)
        RellToolsUtils.compileApp(rellAppConf.sourceDir, modSel, true, C_CompilerOptions.DEFAULT)
    }

    val appConfig = startPostchainNode(rellAppConf)

    log.info("")
    log.info("POSTCHAIN APP STARTED")
    log.info("    REST API port: ${RestApiConfig.fromAppConfig(appConfig).port}")
    log.info("")
}

private fun startPostchainNode(rellAppConf: RellPostAppCliConfig): AppConfig {
    val nodeAppConf = getNodeConfig(rellAppConf, rellAppConf.config.node)

    val node = PostchainNode(nodeAppConf, rellAppConf.config.wipeDb)

    val chainsSorted = rellAppConf.config.chains.sortedBy { it.iid }

    for (chain in chainsSorted) {
        val genesisConfig = chain.configs.getValue(0).gtvConfig
        val genesisConfigBytes = GtvEncoder.encodeGtv(genesisConfig)
        val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(BlockchainConfigurationData.fromRaw(genesisConfigBytes))
        withLoggingContext(
            NODE_PUBKEY_TAG to nodeAppConf.pubKey,
            CHAIN_IID_TAG to chain.iid.toString(),
            BLOCKCHAIN_RID_TAG to brid.toHex()
        ) {
            withReadWriteConnection(node.postchainContext.sharedStorage, chain.iid) { eContext: EContext ->
                BlockchainApi.initializeBlockchain(eContext, brid, override = true, genesisConfig)
            }
            log.info { "Chain '${chain.name}' ID = ${chain.iid} RID = ${brid.toHex()}" }

            check(Arrays.equals(brid.data, chain.brid.toByteArray())) {
                "Chain '${chain.name}' (${chain.iid}): calculated BRID = ${chain.brid.toHex()}, postchain BRID = ${brid.toHex()}"
            }

            for ((height, config) in chain.configs) {
                if (height != 0L) {
                    log.info("Adding configuration for chain: ${chain.iid}, height: $height")
                    withReadWriteConnection(node.postchainContext.sharedStorage, chain.iid) { eContext: EContext ->
                        BlockchainApi.addConfiguration(eContext, height, override = true, config.gtvConfig)
                    }
                }
            }
        }
    }

    for (chain in chainsSorted) {
        try {
            node.startBlockchain(chain.iid)
        } catch (e: UserMistake) {
            throw UserMistake("Failed to start chain '${chain.name}' (IID = ${chain.iid})", e)
        } catch (e: Throwable) {
            throw RuntimeException("Failed to start chain '${chain.name}' (IID = ${chain.iid})", e)
        }
    }

    return nodeAppConf
}

private fun runTests(args: CommonArgs, matcher: UnitTestMatcher, targetChains: Collection<String>?) {
    val compilerOptions = C_CompilerOptions.forLangVersion(args.sourceVer)

    val rellAppConf = generateRunConfig(args, true)
    val testNodeConfig = rellAppConf.config.testNode
    testNodeConfig ?: throw RellCliBasicException("Test database configuration not specified in run.xml")

    val nodeAppConf = getNodeConfig(rellAppConf, testNodeConfig)
    val keyPair = BytesKeyPair(nodeAppConf.privKeyByteArray, nodeAppConf.pubKeyByteArray)

    class TestChain(val chain: RellPostAppChain, val rApp: R_App, val gtvConfig: Gtv)

    val sortedChains = rellAppConf.config.chains
        .filter { targetChains == null || it.name in targetChains }
        .sortedBy { it.iid }

    val tChains = sortedChains.mapNotNull { chain ->
        val (_, config) = chain.configs.maxByOrNull { it.key }!!
        if (config.appModule == null) null else {
            val modules = immListOf(config.appModule)
            val testModules = (modules.toSet() + config.testModules.toSet()).toImmList()
            val modSel = C_CompilerModuleSelection(modules, testModules)
            val rApp = RellToolsUtils.compileApp(rellAppConf.sourceDir, modSel, true, compilerOptions)
            TestChain(chain, rApp, config.gtvConfig)
        }
    }

    val printer: Rt_Printer = Rt_OutPrinter
    val testRes = UnitTestRunnerResults()

    val dbUrl = getDbUrl(nodeAppConf)

    RellApiGtxUtils.runWithSqlManager(dbUrl, args.sqlLog, false) { sqlMgr ->
        for (tChain in tChains) {
            val globalCtx = RellApiBaseUtils.createGlobalContext(compilerOptions, typeCheck = true)
            val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(tChain.rApp, Rt_ChainSqlMapping(tChain.chain.iid))
            val chainCtx = Rt_ChainContext(tChain.gtvConfig, tChain.chain.brid)

            val moduleArgsSource = PostchainBaseUtils.createModuleArgsSource(
                tChain.rApp,
                tChain.gtvConfig,
                compilerOptions,
            )

            val blockRunner = createBlockRunner(args, keyPair, tChain.gtvConfig)

            val fns = UnitTestRunner.getTestFunctions(tChain.rApp, matcher)
            val tc = UnitTestRunnerChain(tChain.chain.name, tChain.chain.iid)
            val cases = fns.map { UnitTestCase(tc, it) }

            val testCtx = UnitTestRunnerContext(
                tChain.rApp,
                printer,
                sqlCtx,
                sqlMgr,
                PostchainSqlInitProjExt,
                globalCtx,
                chainCtx,
                blockRunner,
                moduleArgsSource = moduleArgsSource,
            )

            UnitTestRunner.runTests(testCtx, cases, testRes)
        }
    }

    val ok = testRes.print(printer)
    if (!ok) {
        throw RellCliExitException(1)
    }
}

private fun getDbUrl(appConf: AppConfig): String {
    val query = listOf(
        "user" to appConf.databaseUsername,
        "password" to appConf.databasePassword,
        "currentSchema" to appConf.databaseSchema,
    ).joinToString("&") {
        "${it.first}=${it.second}"
    }
    return "${appConf.databaseUrl}?$query"
}

private fun createBlockRunner(
    args: CommonArgs,
    keyPair: BytesKeyPair,
    gtvConfig: Gtv,
): Rt_UnitTestBlockRunner {
    val blockRunnerConfig = Rt_BlockRunnerConfig(
        forceTypeCheck = true,
        sqlLog = args.sqlLog,
        dbInitLogLevel = SqlInitLogging.LOG_NONE,
    )
    val blockRunnerStrategy = Rt_StaticBlockRunnerStrategy(gtvConfig)
    return Rt_PostchainUnitTestBlockRunner(keyPair, blockRunnerConfig, blockRunnerStrategy)
}

private fun generateRunConfig(args: CommonArgs, test: Boolean): RellPostAppCliConfig {
    return RellRunConfigGenerator.generateCli(args.sourceDir, args.runConfigFile, args.sourceVer, unitTest = test)
}

private fun getNodeConfig(rellAppConf: RellPostAppCliConfig, rellAppNode: RellPostAppNode): AppConfig {
    if (rellAppNode.srcPropsPath != null) {
        val file = File(rellAppNode.srcPropsPath)
        val fullFile = if (file.isAbsolute) file else File(rellAppConf.configDir, rellAppNode.srcPropsPath)
        return AppConfig.fromPropertiesFile(fullFile)
    }

    val text = rellAppNode.srcPropsText!!
    val conf = PropertiesConfiguration()
    conf.layout.load(conf, StringReader(text))
    return AppConfig(conf)
}

private class CommonArgs(
        val runConfigFile: File,
        val sourceDir: File,
        val sourceVer: R_LangVersion,
        val sqlLog: Boolean,
)

@CommandLine.Command(name = "RellRunConfigLaunch", description = ["Launch a run.xml config"])
class RellRunConfigLaunchCliArgs: RellRunConfigCliArgs() {
    @CommandLine.Option(names = ["--test"], description = ["Run unit tests"])
    var test = false

    @CommandLine.Option(
        names = ["--test-filter"],
        description = ["Filter test modules and functions (supports glob patterns, comma-separated)"]
    )
    var testFilter: String? = null

    @CommandLine.Option(
        names = ["--test-chain"],
        description = ["Execute tests only for specified chains (comma-separated list of chain names)"]
    )
    var testChain: String? = null

    @CommandLine.Option(names = ["--sqllog"], description = ["Enable SQL logging"])
    var sqlLog = false

    fun getTargetChains(): Set<String>? {
        val s = testChain
        return if (s == null) null else s.split(",").map { it.trim() }.toImmSet()
    }
}
