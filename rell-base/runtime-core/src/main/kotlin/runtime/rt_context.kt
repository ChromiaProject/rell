/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import mu.KLogging
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.lib.test.Rt_TestBlockClock
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.repl.ReplOutputChannel
import net.postchain.rell.base.runtime.utils.Rt_Messages
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import kotlin.reflect.KType
import kotlin.reflect.full.createType

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

interface Rt_SqlContext {
    val appDefs: RR_AppSqlDefs

    fun mainChainMapping(): Rt_ChainSqlMapping
    fun linkedChain(chain: R_ExternalChainRef): Rt_ExternalChain
    fun chainMapping(externalChain: R_ExternalChainRef?): Rt_ChainSqlMapping

    /** Index-based external chain lookup. Indices match [RR_App.externalChains]. */
    fun linkedChainByIndex(index: Int): Rt_ExternalChain
    fun chainMappingByIndex(index: Int): Rt_ChainSqlMapping
}

class Rt_NullSqlContext private constructor(override val appDefs: RR_AppSqlDefs): Rt_SqlContext {
    override fun mainChainMapping() = throw UnsupportedOperationException()
    override fun linkedChain(chain: R_ExternalChainRef) = throw UnsupportedOperationException()
    override fun chainMapping(externalChain: R_ExternalChainRef?) = throw UnsupportedOperationException()
    override fun linkedChainByIndex(index: Int) = throw UnsupportedOperationException()
    override fun chainMappingByIndex(index: Int) = throw UnsupportedOperationException()

    companion object {
        fun create(sqlDefs: RR_AppSqlDefs): Rt_SqlContext = Rt_NullSqlContext(sqlDefs)
    }
}

class Rt_RegularSqlContext private constructor(
    override val appDefs: RR_AppSqlDefs,
    private val mainChainMapping: Rt_ChainSqlMapping,
    private val linkedExternalChains: ImmList<Rt_ExternalChain>,
): Rt_SqlContext {

    override fun mainChainMapping() = mainChainMapping

    override fun linkedChain(chain: R_ExternalChainRef): Rt_ExternalChain = linkedExternalChains[chain.index]

    override fun chainMapping(externalChain: R_ExternalChainRef?): Rt_ChainSqlMapping {
        return if (externalChain == null) mainChainMapping else linkedChain(externalChain).sqlMapping
    }

    override fun linkedChainByIndex(index: Int): Rt_ExternalChain = linkedExternalChains[index]

    override fun chainMappingByIndex(index: Int): Rt_ChainSqlMapping = linkedExternalChains[index].sqlMapping

    companion object: KLogging() {
        fun createNoExternalChains(rrApp: RR_App, mainChainMapping: Rt_ChainSqlMapping): Rt_SqlContext =
            Rt_RegularSqlContext(rrApp.sqlDefs, mainChainMapping, immListOf())

        fun create(
            app: RR_App,
            mainChainMapping: Rt_ChainSqlMapping,
            chainDependencies: Map<String, Rt_ChainDependency>,
            sqlExec: SqlExecutor,
            heightProvider: Rt_ChainHeightProvider,
            interpreter: Rt_Interpreter? = null,
        ): Rt_SqlContext {
            val externalChains = getExternalChains(sqlExec, chainDependencies, heightProvider)
            val linkedExternalChains = calcLinkedExternalChains(app, externalChains)
            val sqlCtx = Rt_RegularSqlContext(app.sqlDefs, mainChainMapping, linkedExternalChains)
            checkExternalMetaInfo(sqlCtx, externalChains, sqlExec, interpreter)
            return sqlCtx
        }

        private fun getExternalChains(
            sqlExec: SqlExecutor,
            dependencies: Map<String, Rt_ChainDependency>,
            heightProvider: Rt_ChainHeightProvider,
        ): Map<String, Rt_ExternalChain> {
            if (dependencies.isEmpty()) return mapOf()

            val rids = mutableSetOf<String>()
            for ((name, dep) in dependencies) {
                val ridStr = CommonUtils.bytesToHex(dep.rid)
                if (!rids.add(ridStr)) {
                    errInit(
                        "external_chain_dup_rid:$name:$ridStr",
                        "Duplicate external chain RID: '$name', 0x$ridStr",
                    )
                }
            }

            val dbChains = loadDatabaseBlockchains(sqlExec)
            val dbRidMap = dbChains.map { (chainId, rid) -> Pair(CommonUtils.bytesToHex(rid), chainId) }.toMap()

            val res = mutableMapOf<String, Rt_ExternalChain>()
            for ((name, dep) in dependencies) {
                val ridStr = CommonUtils.bytesToHex(dep.rid)
                val chainId = dbRidMap[ridStr] ?: errInit(
                    "external_chain_no_rid:$name:$ridStr",
                    "External chain '$name' not found in the database by RID 0x$ridStr",
                )

                val ridKey = WrappedByteArray(dep.rid)
                val height = heightProvider.getChainHeight(ridKey, chainId) ?: errInit(
                    "external_chain_no_height:$name:$ridStr:$chainId",
                    "Unknown height of the external chain '$name' (RID: 0x$ridStr, ID: $chainId)",
                )

                res[name] = Rt_ExternalChain(chainId, dep.rid, height)
            }

            return res
        }

        private fun loadDatabaseBlockchains(sqlExec: SqlExecutor): Map<Long, ByteArray> {
            val res = mutableMapOf<Long, ByteArray>()
            sqlExec.executeQuery(
                ParameterizedSql(
                    "SELECT chain_iid, blockchain_rid FROM blockchains ORDER BY chain_iid;",
                    immListOf(),
                ),
            ) { rs ->
                val chainId = rs.getLong(1)
                val rid = rs.getBytes(2)!!
                check(chainId !in res)
                res[chainId] = rid
            }
            return res
        }

        private fun calcLinkedExternalChains(
            app: RR_App,
            externalChains: Map<String, Rt_ExternalChain>,
        ): ImmList<Rt_ExternalChain> {
            val chainIds = mutableSetOf<Long>()
            val chainRids = mutableSetOf<String>()
            for ((name, c) in externalChains) {
                val id = c.chainId
                val rid = CommonUtils.bytesToHex(c.rid)
                if (!chainIds.add(id)) {
                    errInit("external_chain_dup_id:$name:$id", "Duplicate external chain ID: '$name', $id")
                }
                if (!chainRids.add(rid)) {
                    errInit("external_chain_dup_rid:$name:$rid", "Duplicate external chain RID: '$name', 0x$rid")
                }
            }

            return app.externalChains.mapToImmList { rrChain ->
                val name = rrChain.name
                val rtChain = externalChains[name] ?: errInit(
                    "external_chain_unknown:$name",
                    "External chain not found: '$name'",
                )
                rtChain
            }
        }

        private fun checkExternalMetaInfo(
            sqlCtx: Rt_SqlContext,
            chains: Map<String, Rt_ExternalChain>,
            sqlExec: SqlExecutor,
            interpreter: Rt_Interpreter?,
        ) {
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
                        errInit(
                            "external_meta_nolog:$chain:$entityName",
                            "Entity '$entityName' in external chain '$chain' is not a log entity",
                        )
                    }

                    checkMissingAttrs(chain, extEntity, metaEntity)
                    if (interpreter != null) {
                        checkAttrTypes(sqlCtx, chain, extEntity, metaEntity, interpreter)
                    }
                }
            }
        }

        private fun checkMissingEntities(
            chain: String,
            extEntities: Map<String, RR_EntityDefinition>,
            metaEntities: Map<String, MetaEntity>,
        ) {
            val metaEntityNames = metaEntities.filter { (_, c) -> c.type == MetaEntityType.ENTITY }.keys
            val missingEntities = extEntities.keys - metaEntityNames
            if (!missingEntities.isEmpty()) {
                val list = missingEntities.sorted()
                errInit(
                    "external_meta_no_entity:$chain:${list.joinToString(",")}",
                    "Entities not found in external chain '$chain': ${list.joinToString()}",
                )
            }
        }

        private fun checkMissingAttrs(chain: String, extEntity: RR_EntityDefinition, metaEntity: MetaEntity) {
            val metaAttrNames = metaEntity.attrs.keys
            val extAttrNames = extEntity.attributes.values.mapTo(HashSet()) { it.sqlMapping }
            val missingAttrs = extAttrNames - metaAttrNames
            if (!missingAttrs.isEmpty()) {
                val entityName = extEntity.base.appLevelName
                val list = missingAttrs.sorted()
                errInit(
                    "external_meta_noattrs:$chain:[$entityName]:${list.joinToString(",")}",
                    "Missing attributes of entity '$entityName' in external chain '$chain': ${list.joinToString()}",
                )
            }
        }

        private fun checkAttrTypes(
            sqlCtx: Rt_SqlContext,
            chain: String,
            extEntity: RR_EntityDefinition,
            metaEntity: MetaEntity,
            interpreter: Rt_Interpreter,
        ) {
            for (extAttr in extEntity.attributes.values.sortedBy { it.name }) {
                val attrName = extAttr.sqlMapping
                val metaAttr = metaEntity.attrs.getValue(attrName)
                val metaType = metaAttr.type
                val extRtType = interpreter.resolveType(extAttr.type)

                val extType = checkNotNull(extRtType.sqlAdapter) {
                    "No SQL adapter for type: ${extRtType.name}"
                }.metaName(sqlCtx)

                if (metaType != extType) {
                    val entityName = extEntity.base.appLevelName
                    errInit(
                        "external_meta_attrtype:$chain:[$entityName]:$attrName:[$extType]:[$metaType]",
                        "Attribute type mismatch for '$entityName.$attrName' in external chain '$chain': " +
                                "expected '$extType', actual '$metaType'",
                    )
                }
            }
        }

        private fun getChainExternalEntities(
            entities: List<RR_EntityDefinition>,
        ): Map<String, Map<String, RR_EntityDefinition>> {
            val res = mutableMapOf<String, MutableMap<String, RR_EntityDefinition>>()
            for (entity in entities) {
                val external = entity.external
                if (external != null && external.metaCheck) {
                    val metaName = entity.sqlMapping.metaName
                    val map = res.getOrPut(external.chainName) { mutableMapOf() }
                    check(metaName !in map)
                    map[metaName] = entity
                }
            }
            return res
        }

        private fun loadExternalMetaData(
            name: String,
            chain: Rt_ExternalChain,
            sqlExec: SqlExecutor,
        ): Map<String, MetaEntity> {
            val res: ImmMap<String, MetaEntity>

            val msgs = Rt_Messages(logger)
            try {
                res = SqlMeta.loadMetaData(sqlExec, chain.sqlMapping, msgs)
                msgs.checkErrors()
            } catch (e: Rt_Exception) {
                val code = when (e.err) {
                    is Rt_CommonError -> e.err.code
                    else -> e.err.javaClass.simpleName
                }
                val chainIdMsg = "chain_iid = ${chain.chainId}"
                val msg = "Failed to load metadata for external chain '$name' ($chainIdMsg): ${e.message}"
                errInit("external_meta_error:${chain.chainId}:$name:$code", msg)
            }

            return res
        }

        private fun errInit(code: String, msg: String): Nothing = throw Rt_Exception.common(code, msg)
    }
}

class Rt_AppContext(
    val globalCtx: Rt_GlobalContext,
    val chainCtx: Rt_ChainContext,
    val interpreter: Rt_Interpreter,
    val repl: Boolean,
    val test: Boolean,
    val replOut: ReplOutputChannel? = null,
    val blockRunner: Rt_UnitTestBlockRunner = Rt_NullUnitTestBlockRunner,
    val gtvHashCalculator: PostchainGtvUtils.HashCalculator = getDefaultHashCalculator(globalCtx),
    moduleArgsSource: Rt_ModuleArgsSource = Rt_ModuleArgsSource.NULL,
    nativeProvider: Rt_NativeProvider = Rt_NullNativeProvider,
    globalConstantsState: Rt_GlobalConstants.State = Rt_GlobalConstants.State(),
) {
    val rrApp: RR_App
        get() = interpreter.rrApp

    constructor(
        globalCtx: Rt_GlobalContext,
        chainCtx: Rt_ChainContext,
        interpreter: Rt_Interpreter,
        gtvHashCalculator: PostchainGtvUtils.HashCalculator = getDefaultHashCalculator(globalCtx),
        moduleArgsSource: Rt_ModuleArgsSource = Rt_ModuleArgsSource.NULL,
        nativeProvider: Rt_NativeProvider = Rt_NullNativeProvider,
    ): this(
        globalCtx,
        chainCtx,
        interpreter,
        repl = false,
        test = false,
        moduleArgsSource = moduleArgsSource,
        gtvHashCalculator = gtvHashCalculator,
        nativeProvider = nativeProvider,
    )

    private var objsInit: SqlObjectsInit? = null

    private val globalConstants = Rt_GlobalConstants(this, moduleArgsSource, globalConstantsState)

    val nativeFunctions: ImmMap<FullName, Rt_NativeFunction> = let {
        rrApp.nativeFunctions.entries.associateToImmMap { (name, rrHeader) ->
            val fn0 = nativeProvider.getFunction(name)
            val fn = Rt_Utils.checkNotNull(fn0) {
                "native_fn:not_found:[$name]" to "Native function not found: '${name.str()}'"
            }
            val header = fn.getHeader()
            header?.check(name, rrHeader) { interpreter.resolveType(it) }
            name to fn
        }
    }

    init {
        globalConstants.initialize()
    }

    fun objectsInitialization(objsInit: SqlObjectsInit, code: () -> Unit) {
        checkNull(this.objsInit)
        this.objsInit = objsInit
        try {
            code()
        } finally {
            this.objsInit = null
        }
    }

    fun forceObjectInit(obj: RR_ObjectDefinition): Boolean {
        val ref = objsInit
        return if (ref == null) {
            false
        } else {
            ref.forceObject(obj)
            true
        }
    }

    fun getGlobalConstant(constId: GlobalConstantId): Rt_Value = globalConstants.getConstantValue(constId)
    fun getModuleArgs(moduleName: ModuleName): Rt_Value? = globalConstants.getModuleArgsValue(moduleName)
    fun dumpGlobalConstants(): Rt_GlobalConstants.State = globalConstants.dump()

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
    val dbReadOnly: Boolean = true,
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

    var emittedEvents: ImmList<Rt_Value> = state?.emittedEvents.orEmpty()

    val testBlockClock: Rt_TestBlockClock = Rt_TestBlockClock(state?.testBlockClock ?: Rt_TestBlockClock.DEFAULT_STATE)

    fun toState() = State(this)

    class State(ctx: Rt_ExecutionContext) {
        val nextNopNonce = ctx.nextNopNonce
        val emittedEvents = ctx.emittedEvents
        val testBlockClock = ctx.testBlockClock.toState()
    }
}

class Rt_CallContext(val defCtx: Rt_DefinitionContext) {
    val exeCtx = defCtx.exeCtx
    val appCtx = exeCtx.appCtx
    val sqlCtx = exeCtx.sqlCtx
    val globalCtx = appCtx.globalCtx
    val chainCtx = appCtx.chainCtx
}

class Rt_DefinitionContext(val exeCtx: Rt_ExecutionContext, val dbUpdateAllowed: Boolean, val defId: DefinitionId) {
    val appCtx = exeCtx.appCtx
    val globalCtx = appCtx.globalCtx
    val sqlCtx = exeCtx.sqlCtx

    @Volatile
    private var cachedCallCtx: Rt_CallContext? = null

    /**
     * Lazy-cached [Rt_CallContext] wrapping this defCtx. Sys-fn dispatch on the Truffle hot
     * path issues one `Rt_CallContext` per invocation; the wrapper is purely a read-only view
     * over `this` (every field is `val`-derived from defCtx / exeCtx / appCtx, all immutable
     * for the lifetime of the execution), so the same instance is reusable across calls.
     */
    fun toCallContext(): Rt_CallContext {
        val cached = cachedCallCtx
        if (cached != null) return cached
        val fresh = Rt_CallContext(this)
        cachedCallCtx = fresh
        return fresh
    }
}

interface Rt_OpContext {
    fun exists(): Boolean
    fun lastBlockTime(): Long
    fun transactionIid(): Long
    fun blockHeight(): Long
    fun opIndex(): Int
    fun isSigner(pubKey: Bytes): Boolean
    fun signers(): List<Bytes>

    /**
     * Returns all operations of the current transaction as Rell `gtx_operation` struct values.
     * Takes an [Rt_Interpreter] so the implementation can resolve the `gtx_operation` struct's
     * runtime type via the RR app's name index, with no `R_Type` involvement.
     */
    fun allOperations(interpreter: Rt_Interpreter): List<Rt_Value>
    fun currentOperation(interpreter: Rt_Interpreter): Rt_Value
    fun emitEvent(type: String, data: Gtv)

    fun hasSnapshotContext(): Boolean = false
    fun objectSnapshotId(metaName: String): Long = 0L
    fun emitDatum(datumId: Long, datum: Gtv, isPermanent: Boolean) {}
}

object Rt_NullOpContext: Rt_OpContext {
    override fun exists() = false
    override fun lastBlockTime() = errNoOp()
    override fun transactionIid() = errNoOp()
    override fun blockHeight() = errNoOp()
    override fun opIndex() = errNoOp()
    override fun isSigner(pubKey: Bytes) = false
    override fun signers() = errNoOp()
    override fun allOperations(interpreter: Rt_Interpreter) = errNoOp()
    override fun currentOperation(interpreter: Rt_Interpreter) = errNoOp()
    override fun emitEvent(type: String, data: Gtv) = errNoOp()

    private fun errNoOp(): Nothing =
        throw Rt_Exception.common("op_context:noop", "Operation context not available")
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

interface Rt_NativeProvider {
    fun getFunction(name: FullName): Rt_NativeFunction?
}

object Rt_NullNativeProvider: Rt_NativeProvider {
    override fun getFunction(name: FullName) = null
}

class Rt_NativeFunctionHeader(
    val resultType: KType,
    val paramTypes: ImmList<KType>,
) {
    fun check(name: FullName, rrHeader: RR_FunctionHeader, typeResolver: (RR_Type) -> Rt_ValueClass<*>) {
        checkParamCount(name, rrHeader.params.size)
        matchNativeType(typeResolver(rrHeader.type), resultType, name) { "result" to "wrong return type" }
        for ((i, rrParam) in rrHeader.params.withIndex()) {
            matchNativeType(typeResolver(rrParam.type), paramTypes[i], name) {
                "param:${rrParam.name}" to "wrong type of parameter '${rrParam.name}'"
            }
        }
    }

    private fun checkParamCount(name: FullName, rCount: Int) {
        val nCount = paramTypes.size
        checkNative(nCount == rCount, name) {
            "param_count:$rCount:$nCount" to "wrong parameter count: $nCount instead of $rCount"
        }
    }

    private fun matchNativeType(
        rtType: Rt_ValueClass<*>,
        nativeType: KType,
        name: FullName,
        msgGetter: () -> Pair<String, String>,
    ) {
        val nativeConversion = rtType.nativeConversion ?: return
        val nativeType2 = nativeType.classifier?.createType(
            nativeType.arguments, nullable = nativeType.isMarkedNullable,
        ) ?: nativeType
        checkNative(nativeType2 in nativeConversion.nativeTypes, name) {
            val (code, msg) = msgGetter()
            "$code:[${rtType.name}]:[$nativeType]" to "$msg: $nativeType, Rell type: ${rtType.name}"
        }
    }

    private fun checkNative(b: Boolean, name: FullName, msgGetter: () -> Pair<String, String>) {
        Rt_Utils.check(b) {
            val (code, msg) = msgGetter()
            "native_fn:[$name]:$code" to "Native function '$name': $msg"
        }
    }
}

interface Rt_NativeFunction {
    fun getHeader(): Rt_NativeFunctionHeader?
    fun call(args: ImmList<Any?>): Any?
}
