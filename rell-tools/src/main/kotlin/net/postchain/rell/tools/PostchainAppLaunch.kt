/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import mu.KotlinLogging
import mu.withLoggingContext
import net.postchain.PostchainNode
import net.postchain.api.internal.BlockchainApi
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadWriteConnection
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.api.base.RellConfigGen
import net.postchain.rell.api.gtx.RellApiGtxUtils
import net.postchain.rell.base.runtime.Rt_LogPrinter
import net.postchain.rell.base.sql.SqlInitLogging
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.module.RellPostchainModuleEnvironment
import picocli.CommandLine
import java.io.File

private val log = run {
    RellToolsLogUtils.initLogging()
    KotlinLogging.logger("PostchainApp")
}

fun main(args: Array<String>) {
    RellToolsUtils.runCli(args, RunPostchainAppArgs(), ::main0)
}

private fun main0(args: RunPostchainAppArgs) {
    val target = RellToolsUtils.getTarget(args.sourceDir, args.module)
    RellToolsUtils.checkFile(args.nodeConfigFile)

    log.info("STARTING POSTCHAIN APP")
    log.info("    source directory: ${target.sourcePath.absolutePath}")
    log.info("    module:           ${args.module}")
    log.info("    node config file: ${File(args.nodeConfigFile).absolutePath}")
    log.info("")

    RellToolsUtils.printVersionInfo()

    val configGen = RellConfigGen.create(RellCliEnv.DEFAULT, target)

    val nodeAppConf = AppConfig.fromPropertiesFile(args.nodeConfigFile)
    val template = genBlockchainConfigTemplate(nodeAppConf.pubKeyByteArray)
    val bcConf = configGen.makeConfig(template)

    val bcConfBytes = GtvEncoder.encodeGtv(bcConf)
    val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(BlockchainConfigurationData.fromRaw(bcConfBytes))

    val node = PostchainNode(nodeAppConf, true)
    val chainId = 0L

    val pcEnv = RellPostchainModuleEnvironment(
        combinedPrinter = Rt_LogPrinter(),
        copyOutputToPrinter = false,
        sqlLog = args.sqlLog,
        dbInitLogLevel = SqlInitLogging.LOG_STEP_COMPLEX,
    )

    withLoggingContext(
        NODE_PUBKEY_TAG to nodeAppConf.pubKey,
        CHAIN_IID_TAG to chainId.toString(),
        BLOCKCHAIN_RID_TAG to brid.toHex()
    ) {
        RellPostchainModuleEnvironment.set(pcEnv) {
            withReadWriteConnection(node.postchainContext.sharedStorage, chainId) { eContext: EContext ->
                BlockchainApi.initializeBlockchain(eContext, brid, override = true, bcConf)
            }

            node.startBlockchain(chainId)

            log.info("")
            log.info("POSTCHAIN APP STARTED")
            log.info("    REST API port:  ${RestApiConfig.fromAppConfig(nodeAppConf).port}")
            log.info("    blockchain RID: ${brid.toHex()}")
            log.info("")
        }
    }
}

private fun genBlockchainConfigTemplate(pubKey: ByteArray): Gtv {
    val template0 = RellApiGtxUtils.genBlockchainConfigTemplateNoRell(pubKey, RellApiCompile.Config.DEFAULT)
    val rell = gtv(
        "version" to gtv(RellVersions.VERSION_STR),
    )
    return RellConfigGen.makeConfig(template0, rell)
}

@CommandLine.Command(name = "PostchainAppLaunch", description = ["Runs a Rell Postchain app"])
class RunPostchainAppArgs: RellBaseCliArgs() {
    @CommandLine.Option(names = ["--node-config"], paramLabel = "NODE_CONFIG_FILE", required = true,
            description = ["Node configuration (.properties)"])
    var nodeConfigFile: String = ""

    @CommandLine.Parameters(index = "0", paramLabel = "MODULE", description = ["Module name"])
    var module: String = ""

    @CommandLine.Option(names = ["--sqllog"], description = ["Enable SQL logging"])
    var sqlLog = false
}
