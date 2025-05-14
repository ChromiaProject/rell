/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.google.common.collect.Sets
import mu.KLogging
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.lib.test.Rt_TestBlockClock
import net.postchain.rell.base.model.*
import net.postchain.rell.base.repl.ReplOutputChannel
import net.postchain.rell.base.runtime.utils.Rt_Messages
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*

class Rt_GlobalContext(
    val compilerOptions: C_CompilerOptions,
    val outPrinter: Rt_Printer,
    val logPrinter: Rt_Printer,
    val logSqlErrors: Boolean = false,
    val typeCheck: Boolean = false,
    val wrapFunctionCallErrors: Boolean = true,
    val sqlUpdatePortionSize: Int = 1000, // Experimental maximum is 2^15
    val sqlInsertFastRowidCountThreshold: Int = 10,
) {
    private val rellVersion = Rt_RellVersion.getInstance()

    fun rellVersion(): Rt_RellVersion {
        return rellVersion ?: throw Rt_Exception.common("fn:rell.git_info:no_data", "Version information not found")
    }
}

abstract class Rt_SqlContext(app: R_App) {
    val appDefs = app.sqlDefs

    abstract fun mainChainMapping(): Rt_ChainSqlMapping
    abstract fun linkedChain(chain: R_ExternalChainRef): Rt_ExternalChain
    abstract fun chainMapping(externalChain: R_ExternalChainRef?): Rt_ChainSqlMapping
}

class Rt_NullSqlContext private constructor(app: R_App): Rt_SqlContext(app) {
    override fun mainChainMapping() = throw UnsupportedOperationException()
    override fun linkedChain(chain: R_ExternalChainRef) = throw UnsupportedOperationException()
    override fun chainMapping(externalChain: R_ExternalChainRef?) = throw UnsupportedOperationException()

    companion object {
        fun create(app: R_App): Rt_SqlContext = Rt_NullSqlContext(app)
    }
}

class Rt_RegularSqlContext private constructor(
        app: R_App,
        private val mainChainMapping: Rt_ChainSqlMapping,
        private val linkedExternalChains: List<Rt_ExternalChain>
): Rt_SqlContext(app) {
    private val externalChainsRoot = app.externalChainsRoot

    override fun mainChainMapping() = mainChainMapping

    override fun linkedChain(chain: R_ExternalChainRef): Rt_ExternalChain {
        check(chain.root === externalChainsRoot)
        return linkedExternalChains[chain.index]
    }

    override fun chainMapping(externalChain: R_ExternalChainRef?): Rt_ChainSqlMapping {
        return if (externalChain == null) mainChainMapping else linkedChain(externalChain).sqlMapping
    }

    companion object : KLogging() {
        fun createNoExternalChains(app: R_App, mainChainMapping: Rt_ChainSqlMapping): Rt_SqlContext {
            require(app.valid)
            require(app.externalChains.isEmpty()) { "App uses external chains" }
            return Rt_RegularSqlContext(app, mainChainMapping, listOf())
        }

        fun create(
                app: R_App,
                mainChainMapping: Rt_ChainSqlMapping,
                chainDependencies: Map<String, Rt_ChainDependency>,
                sqlExec: SqlExecutor,
                heightProvider: Rt_ChainHeightProvider
        ): Rt_SqlContext {
            require(app.valid)
            val externalChains = getExternalChains(sqlExec, chainDependencies, heightProvider)
            val linkedExternalChains = calcLinkedExternalChains(app, externalChains)
            val sqlCtx = Rt_RegularSqlContext(app, mainChainMapping, linkedExternalChains)
            checkExternalMetaInfo(sqlCtx, externalChains, sqlExec)
            return sqlCtx
        }

        private fun getExternalChains(
                sqlExec: SqlExecutor,
                dependencies: Map<String, Rt_ChainDependency>,
                heightProvider: Rt_ChainHeightProvider
        ): Map<String, Rt_ExternalChain> {
            if (dependencies.isEmpty()) return mapOf()

            val rids = mutableSetOf<String>()
            for ((name, dep) in dependencies) {
                val ridStr = CommonUtils.bytesToHex(dep.rid)
                if (!rids.add(ridStr)) {
                    throw errInit("external_chain_dup_rid:$name:$ridStr",
                            "Duplicate external chain RID: '$name', 0x$ridStr")
                }
            }

            val dbChains = loadDatabaseBlockchains(sqlExec)
            val dbRidMap = dbChains.map { (chainId, rid) -> Pair(CommonUtils.bytesToHex(rid), chainId) }.toMap()

            val res = mutableMapOf<String, Rt_ExternalChain>()
            for ((name, dep) in dependencies) {
                val ridStr = CommonUtils.bytesToHex(dep.rid)
                val chainId = dbRidMap[ridStr]
                if (chainId == null) {
                    throw errInit("external_chain_no_rid:$name:$ridStr",
                            "External chain '$name' not found in the database by RID 0x$ridStr")
                }

                val ridKey = WrappedByteArray(dep.rid)
                val height = heightProvider.getChainHeight(ridKey, chainId)
                if (height == null) {
                    throw errInit("external_chain_no_height:$name:$ridStr:$chainId",
                            "Unknown height of the external chain '$name' (RID: 0x$ridStr, ID: $chainId)")
                }

                res[name] = Rt_ExternalChain(chainId, dep.rid, height)
            }

            return res
        }

        private fun loadDatabaseBlockchains(sqlExec: SqlExecutor): Map<Long, ByteArray> {
            val res = mutableMapOf<Long, ByteArray>()
            sqlExec.executeQuery("SELECT chain_iid, blockchain_rid FROM blockchains ORDER BY chain_iid;", {}) { rs ->
                val chainId = rs.getLong(1)
                val rid = rs.getBytes(2)!!
                check(chainId !in res)
                res[chainId] = rid
            }
            return res
        }

        private fun calcLinkedExternalChains(
                app: R_App,
                externalChains: Map<String, Rt_ExternalChain>
        ): List<Rt_ExternalChain> {
            val chainIds = mutableSetOf<Long>()
            val chainRids = mutableSetOf<String>()
            for ((name, c) in externalChains) {
                val id = c.chainId
                val rid = CommonUtils.bytesToHex(c.rid)
                if (!chainIds.add(id)) {
                    throw errInit("external_chain_dup_id:$name:$id", "Duplicate external chain ID: '$name', $id")
                }
                if (!chainRids.add(rid)) {
                    throw errInit("external_chain_dup_rid:$name:$rid", "Duplicate external chain RID: '$name', 0x$rid")
                }
            }

            return app.externalChains.map { rChain ->
                val name = rChain.name
                val rtChain = externalChains[name]
                if (rtChain == null) {
                    throw errInit("external_chain_unknown:$name", "External chain not found: '$name'")
                }
                rtChain
            }
        }

        private fun checkExternalMetaInfo(sqlCtx: Rt_SqlContext, chains: Map<String, Rt_ExternalChain>, sqlExec: SqlExecutor) {
            val chainMetaEntities = chains.mapValues { (name, chain) -> loadExternalMetaData(name, chain, sqlExec) }
            val chainExternalEntities = getChainExternalEntities(sqlCtx.appDefs.entities)

            for (chain in chainExternalEntities.keys) {
                val extEntities = chainExternalEntities.getValue(chain)
                val metaEntities = chainMetaEntities.getOrDefault(chain, mapOf())
                checkMissingEntities(chain, extEntities, metaEntities)

                for (entityName in extEntities.keys.sorted()) {
                    val extEntity = extEntities.getValue(entityName)
                    val metaEntity = metaEntities.getValue(entityName)
                    if (!metaEntity.log) {
                        throw errInit("external_meta_nolog:$chain:$entityName",
                                "Entity '$entityName' in external chain '$chain' is not a log entity")
                    }

                    checkMissingAttrs(chain, extEntity, metaEntity)
                    checkAttrTypes(sqlCtx, chain, extEntity, metaEntity)
                }
            }
        }

        private fun checkMissingEntities(
                chain: String,
                extEntities: Map<String, R_EntityDefinition>,
                metaEntities: Map<String, MetaEntity>
        ) {
            val metaEntityNames = metaEntities.filter { (_, c) -> c.type == MetaEntityType.ENTITY }.keys
            val missingEntities = Sets.difference(extEntities.keys, metaEntityNames)
            if (!missingEntities.isEmpty()) {
                val list = missingEntities.sorted()
                throw errInit("external_meta_no_entity:$chain:${list.joinToString(",")}",
                        "Entities not found in external chain '$chain': ${list.joinToString()}")
            }
        }

        private fun checkMissingAttrs(chain: String, extEntity: R_EntityDefinition, metaEntity: MetaEntity) {
            val metaAttrNames = metaEntity.attrs.keys
            val extAttrNames = extEntity.strAttributes.keys
            val missingAttrs = Sets.difference(extAttrNames, metaAttrNames)
            if (!missingAttrs.isEmpty()) {
                val entityName = extEntity.appLevelName
                val list = missingAttrs.sorted()
                throw errInit("external_meta_noattrs:$chain:[$entityName]:${list.joinToString(",")}",
                        "Missing attributes of entity '$entityName' in external chain '$chain': ${list.joinToString()}")
            }
        }

        private fun checkAttrTypes(sqlCtx: Rt_SqlContext, chain: String, extEntity: R_EntityDefinition, metaEntity: MetaEntity) {
            for (extAttr in extEntity.attributes.values.sortedBy { it.name }) {
                val attrName = extAttr.name
                val metaAttr = metaEntity.attrs.getValue(attrName)
                val metaType = metaAttr.type
                val extType = extAttr.type.sqlAdapter.metaName(sqlCtx)
                if (metaType != extType) {
                    val entityName = extEntity.appLevelName
                    throw errInit("external_meta_attrtype:$chain:[$entityName]:$attrName:[$extType]:[$metaType]",
                            "Attribute type mismatch for '$entityName.$attrName' in external chain '$chain': " +
                                    "expected '$extType', actual '$metaType'")
                }
            }
        }

        private fun getChainExternalEntities(entities: List<R_EntityDefinition>): Map<String, Map<String, R_EntityDefinition>> {
            val res = mutableMapOf<String, MutableMap<String, R_EntityDefinition>>()
            for (entity in entities) {
                if (entity.external != null && entity.external.metaCheck) {
                    val map = res.computeIfAbsent(entity.external.chain.name) { mutableMapOf() }
                    check(entity.metaName !in map)
                    map[entity.metaName] = entity
                }
            }
            return res
        }

        private fun loadExternalMetaData(name: String, chain: Rt_ExternalChain, sqlExec: SqlExecutor): Map<String, MetaEntity> {
            val res: Map<String, MetaEntity>

            val msgs = Rt_Messages(logger)
            try {
                res = SqlMeta.loadMetaData(sqlExec, chain.sqlMapping, msgs)
                msgs.checkErrors()
            } catch (e: Rt_Exception) {
                val code = when (e.err) {
                    is Rt_CommonError -> e.err.code
                    else -> e.err.javaClass.simpleName
                }
                throw errInit("external_meta_error:${chain.chainId}:$name:$code",
                        "Failed to load metadata for external chain '$name' (chain_iid = ${chain.chainId}): ${e.message}")
            }

            return res
        }

        private fun errInit(code: String, msg: String): RuntimeException = Rt_Exception.common(code, msg)
    }
}

class Rt_AppContext(
    val globalCtx: Rt_GlobalContext,
    val chainCtx: Rt_ChainContext,
    val app: R_App,
    val repl: Boolean,
    val test: Boolean,
    val replOut: ReplOutputChannel? = null,
    val blockRunner: Rt_UnitTestBlockRunner = Rt_NullUnitTestBlockRunner,
    val gtvHashCalculator: PostchainGtvUtils.HashCalculator = getDefaultHashCalculator(globalCtx),
    moduleArgsSource: Rt_ModuleArgsSource = Rt_ModuleArgsSource.NULL,
    globalConstantsState: Rt_GlobalConstants.State = Rt_GlobalConstants.State(),
) {
    private var objsInit: SqlObjectsInit? = null
    private var objsInited = false

    private val globalConstants = Rt_GlobalConstants(this, moduleArgsSource, globalConstantsState)

    init {
        globalConstants.initialize()
    }

    fun objectsInitialization(objsInit: SqlObjectsInit, code: () -> Unit) {
        check(this.objsInit == null)
        check(!objsInited)
        objsInited = true
        this.objsInit = objsInit
        try {
            code()
        } finally {
            this.objsInit = null
        }
    }

    fun forceObjectInit(obj: R_ObjectDefinition): Boolean {
        val ref = objsInit
        return if (ref == null) false else {
            ref.forceObject(obj)
            true
        }
    }

    fun getGlobalConstant(constId: R_GlobalConstantId): Rt_Value {
        return globalConstants.getConstantValue(constId)
    }

    fun getModuleArgs(moduleName: R_ModuleName): Rt_Value? {
        return globalConstants.getModuleArgsValue(moduleName)
    }

    fun dumpGlobalConstants(): Rt_GlobalConstants.State {
        return globalConstants.dump()
    }

    companion object {
        private fun getDefaultHashCalculator(globalCtx: Rt_GlobalContext): PostchainGtvUtils.HashCalculator {
            val v2 = PostchainGtvUtils.HASH_V2_SWITCH.isActive(globalCtx.compilerOptions)
            val version = if (v2) 2 else 1
            return PostchainGtvUtils.HashCalculator(version)
        }
    }
}

class Rt_ExecutionContext(
    val appCtx: Rt_AppContext,
    val opCtx: Rt_OpContext,
    val sqlCtx: Rt_SqlContext,
    sqlExec: SqlExecutor,
    var dbReadOnly: Boolean = true,
    state: State? = null,
) {
    val globalCtx = appCtx.globalCtx

    val sysSqlExec = sqlExec
    val userSqlExec = sqlExec.withAttributes(SqlExecutor.Attributes(category = SqlExecutor.Category.USER))

    private var nextNopNonce: Long = state?.nextNopNonce ?: 0L

    fun nextNopNonce(): Long {
        val r = nextNopNonce
        ++nextNopNonce
        return r
    }

    var emittedEvents: ImmList<Rt_Value> = state?.emittedEvents ?: immListOf()

    val testBlockClock: Rt_TestBlockClock = Rt_TestBlockClock(state?.testBlockClock ?: Rt_TestBlockClock.DEFAULT_STATE)

    fun toState() = State(this)

    class State(ctx: Rt_ExecutionContext) {
        val nextNopNonce = ctx.nextNopNonce
        val emittedEvents = ctx.emittedEvents
        val testBlockClock = ctx.testBlockClock.toState()
    }
}

class Rt_CallContext(
    val defCtx: Rt_DefinitionContext,
    val stack: Rt_CallStack?,
    private val dbUpdateAllowed: Boolean,
) {
    val exeCtx = defCtx.exeCtx
    val appCtx = exeCtx.appCtx
    val sqlCtx = exeCtx.sqlCtx
    val globalCtx = appCtx.globalCtx
    val chainCtx = appCtx.chainCtx

    fun dbUpdateAllowed() = dbUpdateAllowed && defCtx.dbUpdateAllowed

    fun subContext(filePos: R_FilePos): Rt_CallContext {
        val subStack = subStack(filePos)
        return Rt_CallContext(defCtx, subStack, dbUpdateAllowed)
    }

    private fun subStack(filePos: R_FilePos): Rt_CallStack {
        val stackPos = R_StackPos(defCtx.defId, filePos)
        return Rt_CallStack(stack, stackPos)
    }
}

class Rt_DefinitionContext(val exeCtx: Rt_ExecutionContext, val dbUpdateAllowed: Boolean, val defId: R_DefinitionId) {
    val appCtx = exeCtx.appCtx
    val globalCtx = appCtx.globalCtx
    val sqlCtx = exeCtx.sqlCtx
}

abstract class Rt_OpContext {
    abstract fun exists(): Boolean
    abstract fun lastBlockTime(): Long
    abstract fun transactionIid(): Long
    abstract fun blockHeight(): Long
    abstract fun opIndex(): Int
    abstract fun isSigner(pubKey: Bytes): Boolean
    abstract fun signers(): List<Bytes>
    abstract fun allOperations(): List<Rt_Value>
    abstract fun currentOperation(): Rt_Value
    abstract fun emitEvent(type: String, data: Gtv)
}

object Rt_NullOpContext: Rt_OpContext() {
    override fun exists() = false
    override fun lastBlockTime() = throw errNoOp()
    override fun transactionIid() = throw errNoOp()
    override fun blockHeight() = throw errNoOp()
    override fun opIndex() = throw errNoOp()
    override fun isSigner(pubKey: Bytes) = false
    override fun signers() = throw errNoOp()
    override fun allOperations() = throw errNoOp()
    override fun currentOperation() = throw errNoOp()
    override fun emitEvent(type: String, data: Gtv) = throw errNoOp()

    private fun errNoOp(): Rt_Exception {
        return Rt_Exception.common("op_context:noop", "Operation context not available")
    }
}

class Rt_ChainContext(
    val rawConfig: Gtv,
    val blockchainRid: Bytes32,
) {
    companion object {
        val ZERO_BLOCKCHAIN_RID = Bytes32(ByteArray(32))
        val NULL = Rt_ChainContext(GtvNull, ZERO_BLOCKCHAIN_RID)
    }
}
