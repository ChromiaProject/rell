/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.runtime.PostchainGtvUtils
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.runtime.Rt_ModuleArgsSource
import net.postchain.rell.base.utils.Bytes32
import net.postchain.rell.base.utils.mapKeysToImmMap
import net.postchain.rell.base.utils.toIntExact
import java.sql.Connection

object PostchainBaseUtils {
    fun getBlockchainConfigHashVersion(config: Gtv): Int {
        return BlockchainConfigurationData.merkleHashVersion(config).toIntExact()
    }

    fun calcBlockchainRid(config: Gtv): Bytes32 {
        val version = getBlockchainConfigHashVersion(config)
        val hash = PostchainGtvUtils.hashCalculator.hash(config, version)
        return Bytes32(hash)
    }

    fun initializeApp(dbAccess: DatabaseAccess, con: Connection) {
        dbAccess.initializeAppWithCurrentDbVersion(con)
    }

    fun createModuleArgsSource(rrApp: RR_App, configGtv: Gtv, compilerOptions: C_CompilerOptions): Rt_ModuleArgsSource {
        val gtxNode = configGtv.asDict().getValue("gtx").asDict()
        val rellNode = gtxNode.getValue("rell").asDict()

        val gtvs = (rellNode["moduleArgs"]?.asDict() ?: mapOf())
            .mapKeysToImmMap { ModuleName.of(it.key) }

        val defaultValuesSupported = Rt_GtvModuleArgsSource.DEFAULT_VALUES_SWITCH.isActive(compilerOptions)

        for ((moduleName, argsStruct) in rrApp.moduleArgs) {
            if (moduleName !in gtvs) {
                if (!(defaultValuesSupported && argsStruct.hasDefaultConstructor)) {
                    throw UserMistake("No moduleArgs for module '$moduleName' in blockchain configuration, " +
                            "but type ${argsStruct.base.defName.qualifiedName} defined in the code")
                }
            }
        }

        return Rt_GtvModuleArgsSource(gtvs, compilerOptions)
    }
}
