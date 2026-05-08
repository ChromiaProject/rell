/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.base.BaseEContext
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.sql.SqlInitProjExt
import net.postchain.rell.gtx.PostchainBaseUtils

public object PostchainSqlInitProjExt: SqlInitProjExt {
    override fun initExtra(exeCtx: Rt_ExecutionContext) {
        val chainId = exeCtx.sqlCtx.mainChainMapping().chainId
        val bcRid = BlockchainRid(exeCtx.appCtx.chainCtx.blockchainRid.toByteArray())

        val sqlAccess = RellApiGtxUtils.createDatabaseAccess()
        exeCtx.sysSqlExec.connection { con ->
            PostchainBaseUtils.initializeApp(sqlAccess, con)
            val eCtx: EContext = BaseEContext(con, chainId, sqlAccess)
            sqlAccess.initializeBlockchain(eCtx, bcRid)
        }
    }
}
