/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_EntitySqlMapping
import net.postchain.rell.base.model.rr.RR_EntitySqlMappingKind

/**
 * Resolves the SQL table name for an [RR_EntitySqlMapping] at runtime.
 * Replaces the virtual dispatch of `R_EntitySqlMapping.table`.
 */
fun RR_EntitySqlMapping.table(sqlCtx: Rt_SqlContext): String {
    val chainMapping = when (kind) {
        RR_EntitySqlMappingKind.REGULAR -> sqlCtx.mainChainMapping()
        RR_EntitySqlMappingKind.EXTERNAL -> sqlCtx.chainMappingByIndex(externalChainIndex)
        RR_EntitySqlMappingKind.TRANSACTION, RR_EntitySqlMappingKind.BLOCK -> {
            if (externalChainIndex >= 0) {
                sqlCtx.chainMappingByIndex(externalChainIndex)
            } else {
                sqlCtx.mainChainMapping()
            }
        }
    }
    return when (kind) {
        RR_EntitySqlMappingKind.TRANSACTION -> chainMapping.transactionsTable
        RR_EntitySqlMappingKind.BLOCK -> chainMapping.blocksTable
        else -> chainMapping.fullName(mountName)
    }
}
