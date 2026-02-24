/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.sql.SqlConstants

internal abstract class R_EntitySqlMapping(val mountName: R_MountName) {
    abstract val metaName: String

    abstract fun rowidColumn(): String
    abstract fun autoCreateTable(): Boolean
    abstract fun isSystemEntity(): Boolean
    abstract fun table(sqlCtx: Rt_SqlContext): String
    abstract fun table(chainMapping: Rt_ChainSqlMapping): String
    abstract fun extraWhereExpr(atEntity: R_DbAtEntity): Db_Expr?
    abstract fun selectExistingObjects(sqlCtx: Rt_SqlContext, where: String): String

    companion object {
        fun makeTransactionBlockHeightExpr(
            txEntity: R_EntityDefinition,
            txExpr: Db_TableExpr,
            chain: R_ExternalChainRef,
        ): Db_Expr {
            val blockAttr = txEntity.attribute("block")
            val blockEntity = (blockAttr.type as R_EntityType).rEntity
            val blockExpr = Db_RelExpr(txExpr, blockAttr, blockEntity)
            return makeBlockHeightExpr(blockEntity, blockExpr, chain)
        }

        fun makeBlockHeightExpr(
            blockEntity: R_EntityDefinition,
            blockExpr: Db_TableExpr,
            chain: R_ExternalChainRef,
        ): Db_Expr {
            val heightAttr = blockEntity.attribute("block_height")
            val blockHeightExpr = Db_AttrExpr(blockExpr, heightAttr)
            val chainHeightExpr = Db_InterpretedExpr(R_ChainHeightExpr(chain))
            return Db_BinaryExpr(R_BooleanType, Db_BinaryOp_Le, blockHeightExpr, chainHeightExpr)
        }
    }
}

internal class R_EntitySqlMapping_Regular(mountName: R_MountName): R_EntitySqlMapping(mountName) {
    override val metaName = mountName.str()

    override fun rowidColumn() = SqlConstants.ROWID_COLUMN
    override fun autoCreateTable() = true
    override fun isSystemEntity() = false

    override fun table(sqlCtx: Rt_SqlContext) = table(sqlCtx.mainChainMapping())
    override fun table(chainMapping: Rt_ChainSqlMapping) = chainMapping.fullName(mountName)

    override fun extraWhereExpr(atEntity: R_DbAtEntity) = null

    override fun selectExistingObjects(sqlCtx: Rt_SqlContext, where: String): String {
        val tbl = table(sqlCtx)
        val rowid = rowidColumn()
        return """SELECT "$rowid" FROM "$tbl" WHERE $where"""
    }
}

internal class R_EntitySqlMapping_External(
    mountName: R_MountName,
    private val chain: R_ExternalChainRef,
): R_EntitySqlMapping(mountName) {
    override val metaName = mountName.str()

    override fun rowidColumn() = SqlConstants.ROWID_COLUMN
    override fun autoCreateTable() = false
    override fun isSystemEntity() = false

    override fun table(sqlCtx: Rt_SqlContext): String {
        val chainMapping = sqlCtx.linkedChain(chain).sqlMapping
        return table(chainMapping)
    }

    override fun table(chainMapping: Rt_ChainSqlMapping): String {
        val res = chainMapping.fullName(mountName)
        return res
    }

    override fun extraWhereExpr(atEntity: R_DbAtEntity): Db_Expr {
        check(atEntity.rEntity.sqlMapping === this)
        val txAttr = atEntity.rEntity.attribute("transaction")
        val txEntity = (txAttr.type as R_EntityType).rEntity
        val entityExpr = Db_EntityExpr(atEntity)
        val txExpr = Db_RelExpr(entityExpr, txAttr, txEntity)
        return makeTransactionBlockHeightExpr(txEntity, txExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Rt_SqlContext, where: String): String {
        val chainMapping = sqlCtx.linkedChain(chain).sqlMapping
        val tbl = table(chainMapping)
        val rowid = rowidColumn()
        val blkTbl = chainMapping.blocksTable
        val txTbl = chainMapping.transactionsTable

        val height = sqlCtx.linkedChain(chain).height

        return """SELECT A."$rowid"
            | FROM "$tbl" A JOIN "$txTbl" T ON T.tx_iid = A.transaction
            | JOIN "$blkTbl" B ON B.block_iid = T.block_iid
            | WHERE $where AND B.block_height <= $height"""
                .trimMargin()
    }
}

internal abstract class R_EntitySqlMapping_TxBlk(
    tableName: String,
    final override val metaName: String,
    private val rowid: String,
    protected val chain: R_ExternalChainRef?,
): R_EntitySqlMapping(R_MountName.of(tableName)) {
    final override fun rowidColumn() = rowid
    final override fun autoCreateTable() = false
    final override fun isSystemEntity() = true

    final override fun table(sqlCtx: Rt_SqlContext): String {
        val mapping = sqlCtx.chainMapping(chain)
        val res = table(mapping)
        return res
    }

    protected abstract fun extraWhereExpr0(
        entity: R_EntityDefinition,
        entityExpr: Db_EntityExpr,
        chain: R_ExternalChainRef?,
    ): Db_Expr?

    final override fun extraWhereExpr(atEntity: R_DbAtEntity): Db_Expr? {
        check(atEntity.rEntity.sqlMapping === this)
        val entity = atEntity.rEntity
        val entityExpr = Db_EntityExpr(atEntity)
        return extraWhereExpr0(entity, entityExpr, chain)
    }
}

internal class R_EntitySqlMapping_Transaction(
    chain: R_ExternalChainRef?,
): R_EntitySqlMapping_TxBlk(SqlConstants.TRANSACTIONS_TABLE, "transaction", "tx_iid", chain) {
    override fun table(chainMapping: Rt_ChainSqlMapping) = chainMapping.transactionsTable

    override fun extraWhereExpr0(
        entity: R_EntityDefinition,
        entityExpr: Db_EntityExpr,
        chain: R_ExternalChainRef?,
    ): Db_Expr? {
        // Extra WHERE with block height check is needed only for external block/transaction entities.
        return if (chain == null) null else makeTransactionBlockHeightExpr(entity, entityExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Rt_SqlContext, where: String): String {
        val txTbl = table(sqlCtx)
        val rowid = rowidColumn()
        return if (chain == null) {
            """SELECT "$rowid" FROM "$txTbl" WHERE $where"""
        } else {
            val height = sqlCtx.linkedChain(chain).height
            val blkTbl = sqlCtx.chainMapping(chain).blocksTable
            """SELECT "$rowid" FROM "$txTbl" T JOIN "$blkTbl" B ON B.block_iid = T.block_iid
                | WHERE ($where) AND (B.block_height <= $height)
            """.trimMargin()
        }
    }
}

internal class R_EntitySqlMapping_Block(
    chain: R_ExternalChainRef?,
): R_EntitySqlMapping_TxBlk(SqlConstants.BLOCKS_TABLE, "block", "block_iid", chain) {
    override fun table(chainMapping: Rt_ChainSqlMapping) = chainMapping.blocksTable

    override fun extraWhereExpr0(
        entity: R_EntityDefinition,
        entityExpr: Db_EntityExpr,
        chain: R_ExternalChainRef?,
    ): Db_Expr? {
        // Extra WHERE with block height check is needed only for external block/transaction entities.
        return if (chain == null) null else makeBlockHeightExpr(entity, entityExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Rt_SqlContext, where: String): String {
        val tbl = table(sqlCtx)
        val rowid = rowidColumn()
        val commonSql = """SELECT "$rowid" FROM "$tbl""""
        return if (chain == null) {
            "$commonSql WHERE $where"
        } else {
            val height = sqlCtx.linkedChain(chain).height
            "$commonSql WHERE ($where) AND (block_height <= $height)"
        }
    }
}
