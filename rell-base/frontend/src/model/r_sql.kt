/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model


import net.postchain.rell.base.model.expr.*

/**
 * SQL mapping for an entity. Runtime methods ([table], [selectExistingObjects]) accept
 * `Rt_SqlContext` / `Rt_ChainSqlMapping` as `Any` to avoid a model-to-runtime import.
 * Callers from the runtime package pass the concrete types, and implementations cast internally.
 */
abstract class R_EntitySqlMapping(val mountName: MountName) {
    abstract val metaName: String

    abstract fun rowidColumn(): String
    abstract fun autoCreateTable(): Boolean
    abstract fun isSystemEntity(): Boolean

    /** @param sqlCtx an `Rt_SqlContext` */
    abstract fun table(sqlCtx: Any): String

    /** @param chainMapping an `Rt_ChainSqlMapping` */
    abstract fun tableByChainMapping(chainMapping: Any): String

    abstract fun extraWhereExpr(atEntity: R_DbAtEntity): Db_Expr?

    /** @param sqlCtx an `Rt_SqlContext` */
    abstract fun selectExistingObjects(sqlCtx: Any, where: String): String

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

// These accessors use reflection-free duck-typing casts to break the model→runtime import.
// At runtime, sqlCtx is always Rt_SqlContext and chainMapping is always Rt_ChainSqlMapping.
// The lateinit adapters are set from runtime initialization.
object R_SqlBridge {
    lateinit var mainChainMapping: (sqlCtx: Any) -> Any
    lateinit var linkedChain: (sqlCtx: Any, chain: R_ExternalChainRef) -> R_LinkedChainInfo
    lateinit var chainMapping: (sqlCtx: Any, chain: R_ExternalChainRef?) -> Any
    lateinit var fullName: (chainMapping: Any, mountName: MountName) -> String
    lateinit var chainMappingBlocksTable: (chainMapping: Any) -> String
    lateinit var chainMappingTransactionsTable: (chainMapping: Any) -> String
}

class R_LinkedChainInfo(val sqlMapping: Any, val height: Long)

class R_EntitySqlMapping_Regular(mountName: MountName): R_EntitySqlMapping(mountName) {
    override val metaName = mountName.str()

    override fun rowidColumn() = R_SqlConstants.ROWID_COLUMN
    override fun autoCreateTable() = true
    override fun isSystemEntity() = false

    override fun table(sqlCtx: Any) = tableByChainMapping(R_SqlBridge.mainChainMapping(sqlCtx))
    override fun tableByChainMapping(chainMapping: Any) = R_SqlBridge.fullName(chainMapping, mountName)

    override fun extraWhereExpr(atEntity: R_DbAtEntity) = null

    override fun selectExistingObjects(sqlCtx: Any, where: String): String {
        val tbl = table(sqlCtx)
        val rowid = rowidColumn()
        return """SELECT "$rowid" FROM "$tbl" WHERE $where"""
    }
}

class R_EntitySqlMapping_External(
    mountName: MountName,
    val chain: R_ExternalChainRef,
): R_EntitySqlMapping(mountName) {
    override val metaName = mountName.str()

    override fun rowidColumn() = R_SqlConstants.ROWID_COLUMN
    override fun autoCreateTable() = false
    override fun isSystemEntity() = false

    override fun table(sqlCtx: Any): String {
        val chainMapping = R_SqlBridge.linkedChain(sqlCtx, chain).sqlMapping
        return tableByChainMapping(chainMapping)
    }

    override fun tableByChainMapping(chainMapping: Any): String {
        return R_SqlBridge.fullName(chainMapping, mountName)
    }

    override fun extraWhereExpr(atEntity: R_DbAtEntity): Db_Expr {
        check(atEntity.rEntity.sqlMapping === this)
        val txAttr = atEntity.rEntity.attribute("transaction")
        val txEntity = (txAttr.type as R_EntityType).rEntity
        val entityExpr = Db_EntityExpr(atEntity)
        val txExpr = Db_RelExpr(entityExpr, txAttr, txEntity)
        return makeTransactionBlockHeightExpr(txEntity, txExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Any, where: String): String {
        val linked = R_SqlBridge.linkedChain(sqlCtx, chain)
        val chainMapping = linked.sqlMapping
        val tbl = tableByChainMapping(chainMapping)
        val rowid = rowidColumn()
        val blkTbl = R_SqlBridge.chainMappingBlocksTable(chainMapping)
        val txTbl = R_SqlBridge.chainMappingTransactionsTable(chainMapping)

        val height = linked.height

        return """SELECT A."$rowid"
            | FROM "$tbl" A JOIN "$txTbl" T ON T.tx_iid = A.transaction
            | JOIN "$blkTbl" B ON B.block_iid = T.block_iid
            | WHERE $where AND B.block_height <= $height"""
                .trimMargin()
    }
}

abstract class R_EntitySqlMapping_TxBlk(
    tableName: String,
    final override val metaName: String,
    private val rowid: String,
    val chain: R_ExternalChainRef?,
): R_EntitySqlMapping(MountName.of(tableName)) {
    final override fun rowidColumn() = rowid
    final override fun autoCreateTable() = false
    final override fun isSystemEntity() = true

    final override fun table(sqlCtx: Any): String {
        val mapping = R_SqlBridge.chainMapping(sqlCtx, chain)
        return tableByChainMapping(mapping)
    }

    abstract fun extraWhereExpr0(
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

class R_EntitySqlMapping_Transaction(
    chain: R_ExternalChainRef?,
): R_EntitySqlMapping_TxBlk(R_SqlConstants.TRANSACTIONS_TABLE, "transaction", "tx_iid", chain) {
    override fun tableByChainMapping(chainMapping: Any) = R_SqlBridge.chainMappingTransactionsTable(chainMapping)

    override fun extraWhereExpr0(
        entity: R_EntityDefinition,
        entityExpr: Db_EntityExpr,
        chain: R_ExternalChainRef?,
    ): Db_Expr? {
        // Extra WHERE with block height check is needed only for external block/transaction entities.
        return if (chain == null) null else makeTransactionBlockHeightExpr(entity, entityExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Any, where: String): String {
        val txTbl = table(sqlCtx)
        val rowid = rowidColumn()
        return if (chain == null) {
            """SELECT "$rowid" FROM "$txTbl" WHERE $where"""
        } else {
            val linked = R_SqlBridge.linkedChain(sqlCtx, chain)
            val blkTbl = R_SqlBridge.chainMappingBlocksTable(R_SqlBridge.chainMapping(sqlCtx, chain))
            """SELECT "$rowid" FROM "$txTbl" T JOIN "$blkTbl" B ON B.block_iid = T.block_iid
                | WHERE ($where) AND (B.block_height <= ${linked.height})
            """.trimMargin()
        }
    }
}

class R_EntitySqlMapping_Block(
    chain: R_ExternalChainRef?,
): R_EntitySqlMapping_TxBlk(R_SqlConstants.BLOCKS_TABLE, "block", "block_iid", chain) {
    override fun tableByChainMapping(chainMapping: Any) = R_SqlBridge.chainMappingBlocksTable(chainMapping)

    override fun extraWhereExpr0(
        entity: R_EntityDefinition,
        entityExpr: Db_EntityExpr,
        chain: R_ExternalChainRef?,
    ): Db_Expr? {
        // Extra WHERE with block height check is needed only for external block/transaction entities.
        return if (chain == null) null else makeBlockHeightExpr(entity, entityExpr, chain)
    }

    override fun selectExistingObjects(sqlCtx: Any, where: String): String {
        val tbl = table(sqlCtx)
        val rowid = rowidColumn()
        val commonSql = """SELECT "$rowid" FROM "$tbl""""
        return if (chain == null) {
            "$commonSql WHERE $where"
        } else {
            val height = R_SqlBridge.linkedChain(sqlCtx, chain).height
            "$commonSql WHERE ($where) AND (block_height <= $height)"
        }
    }
}
