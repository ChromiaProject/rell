/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.builder.GtvBuilder
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.runtime.PostchainGtvUtils
import net.postchain.rell.base.utils.*
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString
import kotlin.io.path.readText

class RellPostAppCliConfig(val sourceDir: C_SourceDir, val configDir: Path, val config: RellPostAppConfig)

class RellPostAppConfig(
        val node: RellPostAppNode,
        val testNode: RellPostAppNode?,
        val chains: List<RellPostAppChain>,
        val wipeDb: Boolean
)

class RellPostAppNode(
        val srcPropsPath: String?,
        val srcPropsText: String?,
        val dstFiles: Map<String, DirFile>,
        val signers: Set<Bytes33>
)

class RellPostAppChain(
        val name: String,
        val iid: Long,
        val brid: Bytes32,
        val configs: Map<Long, RellPostAppChainConfig>,
        val modules: Set<ModuleName>
)

class RellPostAppChainConfig(
        val appModule: ModuleName?,
        val testModules: List<ModuleName>,
        val gtvConfig: Gtv
)

class Rcfg_Run(
        val nodeConfig: Rcfg_NodeConfig?,
        val testNodeConfig: Rcfg_NodeConfig?,
        val chains: List<Rcfg_Chain>,
        val tests: List<Rcfg_TestModule>,
        val wipeDb: Boolean
)

class Rcfg_NodeConfig(val src: String?, val text: String?, val addSigners: Boolean)

class Rcfg_Chain(val name: String, val iid: Long, val configs: List<Rcfg_ChainConfig>, val tests: List<Rcfg_TestModule>)

class Rcfg_Dependency(val chain: Rcfg_Chain?, val brid: Bytes32?)

class Rcfg_ChainConfig(
        val height: Long,
        val app: Rcfg_App?,
        val gtvs: List<Rcfg_ChainConfigGtv>,
        val addDependencies: Boolean,
        val dependencies: Map<String, Rcfg_Dependency>
)

class Rcfg_TestModule(val module: ModuleName)

class Rcfg_App(
        val module: ModuleName,
        val args: Map<ModuleName, Gtv>,
        val addDefaults: Boolean
)

class Rcfg_ChainConfigGtv(val path: List<String>, val src: String?, val gtv: GtvBuilder.GtvNode?)

class RellRunConfigParams(
        val sourceDir: C_SourceDir,
        val configDir: GeneralDir,
        val sourceVersion: R_LangVersion,
        val unitTest: Boolean
)

object RellRunConfigGenerator {
    fun generateCli(
            sourceDir: Path,
            runConfFile: Path,
            sourceVersion: R_LangVersion,
            unitTest: Boolean
    ): RellPostAppCliConfig {
        val cSourceDir = C_SourceDir.diskDir(sourceDir.toFile())

        val configDir = runConfFile.absolute().parent
        val generalConfigDir = DiskGeneralDir(configDir.toFile())
        val params = RellRunConfigParams(cSourceDir, generalConfigDir, sourceVersion, unitTest)

        val runConfText = runConfFile.readText()
        val config = generate(RellCliEnv.DEFAULT, params, runConfFile.pathString, runConfText)

        return RellPostAppCliConfig(cSourceDir, configDir, config)
    }

    fun generate(
        cliEnv: RellCliEnv,
        params: RellRunConfigParams,
        confPath: String,
        confText: String
    ): RellPostAppConfig {
        val parserOpts = RunConfigParserOptions(unitTest = params.unitTest)
        val rcfg = readConfig(params.configDir, confPath, confText, parserOpts)

        val rawNodeConfig = if (params.unitTest) rcfg.testNodeConfig else rcfg.nodeConfig
        checkNotNull(rawNodeConfig) { "Node config not defined!" }

        val nodeConfig = RunConfigNodeConfigGen.generateNodeConfig(rawNodeConfig, params.configDir)

        val testNodeConfig = if (rcfg.testNodeConfig == null) null else {
            RunConfigNodeConfigGen.generateNodeConfig(rcfg.testNodeConfig, params.configDir)
        }

        val replaceSigners = if (rawNodeConfig.addSigners) nodeConfig.signers else null
        val chainConfigs = RunConfigChainConfigGen.generateChainsConfigs(cliEnv, params, rcfg, replaceSigners)

        return RellPostAppConfig(nodeConfig, testNodeConfig, chainConfigs, wipeDb = rcfg.wipeDb)
    }

    private fun readConfig(
            configDir: GeneralDir,
            confPath: String,
            confText: String,
            parserOpts: RunConfigParserOptions
    ): Rcfg_Run {
        val xml = RellXmlParser().parse(confPath, confText)
        val fullXml = RellXmlIncluder.includeFiles(xml, configDir)
        val runConfig = RunConfigParser.parseConfig(fullXml, parserOpts)
        return runConfig
    }

    fun buildFiles(appConfig: RellPostAppConfig): Map<String, DirFile> {
        val dirBuilder = DirBuilder()
        dirBuilder.put(appConfig.node.dstFiles)

        for (chain in appConfig.chains) {
            val chainPath = "blockchains/${chain.iid}"
            dirBuilder.put("$chainPath/brid.txt", chain.brid.toHex())

            for ((height, config) in chain.configs) {
                val xml = PostchainGtvUtils.gtvToXml(config.gtvConfig)
                dirBuilder.put("$chainPath/$height.xml", xml)

                val bytes = GtvEncoder.encodeGtv(config.gtvConfig).toBytes()
                dirBuilder.put("$chainPath/$height.gtv", bytes)
            }
        }

        return dirBuilder.toFileMap()
    }
}
