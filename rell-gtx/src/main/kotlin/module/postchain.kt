/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.*
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_SnapshotSqlUtils
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.isPostgresQueryCanceled
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.gtx.*
import org.jooq.impl.DSL
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max

private class ErrorHandler(
    val printer: Rt_Printer,
    private val wrapCtErrors: Boolean,
    private val wrapRtErrors: Boolean,
) {
    private var ignore = false

    fun ignoreError() {
        ignore = true
    }

    inline fun <T> handleError(msgSupplier: () -> String, code: () -> T): T {
        try {
            val res = code()
            return res
        } catch (e: UserMistake) {
            val msg = processError(msgSupplier, e)
            throw UserMistake(msg, e)
        } catch (e: ProgrammerMistake) {
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg, e)
        } catch (e: Rt_Exception) {
            val msg = processError(msgSupplier, e, e.info.stack)
            throw if (wrapRtErrors) UserMistake(msg) else e
        } catch (e: C_Error){
            val msg = processError(msgSupplier, e)
            throw if (wrapCtErrors) UserMistake(msg) else e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: SQLException) {
            if (e.isPostgresQueryCanceled) {
                throw e
            }
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg, e)
        } catch (e: Exception) {
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg, e)
        } catch (e: Throwable) {
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg)
        }
    }

    private inline fun processError(msgSupplier: () -> String, e: Throwable, stack: List<R_StackPos> = listOf()): String {
        val subMsg = msgSupplier()
        val errMsg = e.message ?: e.toString()
        val headMsg = "$subMsg: $errMsg"

        if (!ignore) {
            val fullMsg = Rt_Utils.appendStackTrace(headMsg, stack)
            printer.print(fullMsg)
        }
        ignore = false

        val resMsg = if (stack.isEmpty()) headMsg else "[${stack[0]}] $headMsg"
        return resMsg
    }
}

private class Rt_MultiPrinter(private val printers: Collection<Rt_Printer>): Rt_Printer {
    companion object: KLogging()

    constructor(vararg printers: Rt_Printer): this(printers.toList())

    override fun print(str: String) {
        for (printer in printers) {
            try {
                printer.print(str)
            } catch (e: Throwable) {
                logger.error("$e")
            }
        }
    }
}

class Rt_TimestampPrinter(private val printer: Rt_Printer): Rt_Printer {
    companion object: KLogging() {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS", Locale.US)
            .withZone(ZoneId.systemDefault())
    }

    override fun print(str: String) {
        val time = System.currentTimeMillis()
        val timeStr = DATE_FMT.format(Instant.ofEpochMilli(time))
        val str2 = "$timeStr $str"
        printer.print(str2)
    }
}

private class RellGTXOperation(
    private val module: RellPostchainModule,
    private val rrOperation: RR_OperationDefinition,
    private val errorHandler: ErrorHandler,
    private val snapshotContext: SnapshotContext?,
    opData: ExtOpData,
): GTXOperation(opData) {
    private val gtvArgs = data.args.toImmList()

    override fun checkCorrectness(ctxt: EContext) {
        handleError {
            val exeCtx = makeCheckCorrectnessExeCtx(ctxt)
            val gtvCtx = makeGtvToRtContext(null, validateOnly = true)
            val rtArgs = makeArgs(exeCtx, gtvCtx)
            gtvCtx.finish(exeCtx)
            module.appCtx.interpreter.executeOperationGuard(rrOperation, exeCtx, rtArgs)
        }
    }

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) = checkCorrectness(ctxt)
    override fun isCompound(): Boolean = rrOperation.modifiers.isCompound
    override fun isSinglePerTransaction(): Boolean = rrOperation.modifiers.isSingular

    override fun apply(ctx: TxEContext): Boolean {
        handleError {
            val exeCtx = makeApplyExeCtx(ctx)
            val gtvCtx = makeGtvToRtContext(GtvToRtDefaultValueEvaluator.getNormal(exeCtx))
            val rtArgs = makeArgs(exeCtx, gtvCtx)
            gtvCtx.finish(exeCtx)
            module.appCtx.interpreter.callOperation(rrOperation, exeCtx, rtArgs)
        }
        return true
    }

    private fun makeCheckCorrectnessExeCtx(ctx: EContext): Rt_ExecutionContext {
        val db = DatabaseAccess.of(ctx)
        val blockHeight = db.getLastBlockHeight(ctx)
        val lastBlocktime = db.getLastBlockTimestamp(ctx)
        val txIID = db.getLastTransactionNumber(ctx)
        val opCtx = Rt_PostchainOpContext(
            txCtx = Rt_CheckCorrectnessPostchainTxContext,
            lastBlockTime = lastBlocktime,
            transactionIid = txIID,
            blockHeight = blockHeight,
            opIndex = data.opIndex,
            signers = data.signers.mapToImmList { it.toBytes() },
            allOperations = data.operations.toImmList(),
        )
        return module.createExecutionContext(ctx, opCtx, Rt_NullChainHeightProvider, dbReadOnly = true)
    }

    private fun makeApplyExeCtx(ctx: TxEContext): Rt_ExecutionContext {
        val blockHeight = DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
        val txCtx = module.env.txContextFactory.createTxContext(ctx)

        val opCtx = Rt_PostchainOpContext(
            txCtx = txCtx,
            lastBlockTime = ctx.timestamp,
            transactionIid = ctx.txIID,
            blockHeight = blockHeight,
            opIndex = data.opIndex,
            signers = data.signers.mapToImmList { it.toBytes() },
            allOperations = data.operations.toImmList(),
            eCtx = ctx,
            snapshotContext = snapshotContext,
            objectSnapshotIds = module.objectSnapshotIds,
        )

        val heightProvider = Rt_TxChainHeightProvider(ctx)
        return module.createExecutionContext(ctx, opCtx, heightProvider, dbReadOnly = false)
    }

    private fun makeArgs(exeCtx: Rt_ExecutionContext, gtvCtx: GtvToRtContext): List<Rt_Value> {
        val opArgs = getOpArgs(gtvCtx)
        val defCtx = Rt_DefinitionContext(exeCtx, true, rrOperation.base.defId)
        val rtArgs = rrOperation.params.mapIndexed { i, param ->
            val rtArg = opArgs.getOrNull(i)
            rtArg ?: module.appCtx.interpreter.evaluateParamDefault(param, defCtx)
        }
        return rtArgs
    }

    private fun getOpArgs(gtvCtx: GtvToRtContext): List<Rt_Value> {
        val params = rrOperation.params

        val minParams = params.dropLastWhile { it.defaultExpr != null }.size
        if (gtvArgs.size < minParams || gtvArgs.size > params.size) {
            throw Rt_Exception.common(
                "operation:[${rrOperation.base.appLevelName}]:arg_count:${data.args.size}:${params.size}",
                "Wrong argument count: ${data.args.size} instead of ${params.size}",
            )
        }

        val interpreter = module.appCtx.interpreter
        val rtArgs = gtvArgs.mapIndexed { i, arg ->
            val param = params[i]
            RellPcUtils.convertArg(gtvCtx, interpreter, param, arg)
        }

        return rtArgs
    }

    private fun makeGtvToRtContext(
        defaultValueEvaluator: GtvToRtDefaultValueEvaluator?,
        validateOnly: Boolean = false,
    ): GtvToRtContext {
        return GtvToRtContext.make(
            pretty = GTV_OPERATION_PRETTY,
            strictGtvConversion = module.config.strictGtvConversion,
            compilerOptions = module.config.compilerOptions,
            defaultValueEvaluator = defaultValueEvaluator,
            validateOnly = validateOnly,
        )
    }

    private fun <T> handleError(code: () -> T): T {
        return errorHandler.handleError({ "Operation '${rrOperation.base.appLevelName}' failed" }) {
            code()
        }
    }

    private class Rt_TxChainHeightProvider(private val ctx: TxEContext): Rt_ChainHeightProvider {
        override fun getChainHeight(rid: WrappedByteArray, id: Long): Long? {
            return try {
                ctx.getChainDependencyHeight(id)
            } catch (_: Exception) {
                null
            }
        }
    }
}

private class RellModuleConfig(
    val sqlLogging: Boolean,
    val typeCheck: Boolean,
    val dbInitLogLevel: Int,
    val compilerOptions: C_CompilerOptions,
    val strictGtvConversion: Boolean,
)

private class RellPostchainModule(
    val env: RellPostchainModuleEnvironment,
    val config: RellModuleConfig,
    private val rrApp: RR_App,
    compilationSysFns: Map<String, Any>,
    chainCtx: Rt_ChainContext,
    private val chainDeps: Map<String, ByteArray>,
    outPrinter: Rt_Printer,
    logPrinter: Rt_Printer,
    private val errorHandler: ErrorHandler,
    moduleArgsSource: Rt_ModuleArgsSource,
    gtvHashCalculator: PostchainGtvUtils.HashCalculator,
    nativeProvider: Rt_NativeProvider,
): GTXModule, SnapshotAware {
    private val operationNames = rrApp.operations.keys.map { it.str() }.toImmSet()
    private val queryNames = rrApp.queries.keys.map { it.str() }.toImmSet()

    private val globalCtx = Rt_GlobalContext(
        compilerOptions = config.compilerOptions,
        outPrinter = outPrinter,
        logPrinter = logPrinter,
        typeCheck = config.typeCheck,
    )

    private val interpreter = Rt_InterpreterImpl.forCompilation(rrApp, compilationSysFns)

    val appCtx = Rt_AppContext(
        globalCtx,
        chainCtx,
        interpreter,
        moduleArgsSource = moduleArgsSource,
        gtvHashCalculator = gtvHashCalculator,
        nativeProvider = nativeProvider,
    )

    private var snapshotContext: SnapshotContext? = null
    private var snapshotMaxRowid = 1L
    var objectSnapshotIds: ImmMap<String, Long> = immMapOf()
        private set

    private val entityMap = rrApp.sqlDefs.entities.associateByToImmMap { it.sqlMapping.metaName }
    private val objectMap = rrApp.sqlDefs.objects.associateByToImmMap { it.rEntity.sqlMapping.metaName }

    override fun getOperations(): Set<String> = operationNames

    override fun getQueries(): Set<String> = queryNames

    override fun initializeDB(ctx: EContext) {
        if (!env.dbInitEnabled) {
            return
        }

        // Skip DB initialization if a snapshot sync is in progress.
        // During snapshot sync, initializeImport() has already dropped and recreated tables without constraints.
        // Re-running initializeDB would recreate them with constraints, breaking the sync.
        if (DatabaseAccess.of(ctx).getSnapshotSyncState(ctx) != null) {
            return
        }

        errorHandler.handleError({ "Database initialization failed" }) {
            initDb(ctx, false)
        }
    }

    private fun initDb(ctx: EContext, snapshot: Boolean) {
        val heightProvider = Rt_ConstantChainHeightProvider(-1)
        val exeCtx = createExecutionContext(ctx, Rt_NullOpContext, heightProvider, dbReadOnly = false)
        val initLogging = SqlInitLogging.ofLevel(config.dbInitLogLevel)
        SqlInit.init(exeCtx, NullSqlInitProjExt, initLogging, snapshot)
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        return errorHandler.handleError({ "Operation '${opData.opName}' failed" }) {
            val rrOperation = getRoutine("Operation", rrApp.operations, opData.opName)
            RellGTXOperation(this, rrOperation, errorHandler, snapshotContext, opData)
        }
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        return errorHandler.handleError({ "Query '$name' failed" }) {
            query0(ctxt, name, args)
        }
    }

    private fun query0(ctx: EContext, name: String, args: Gtv): Gtv {
        val rrQuery = getRoutine("Query", rrApp.queries, name)

        val heightProvider = Rt_ConstantChainHeightProvider(Long.MAX_VALUE)

        val exeCtx = createExecutionContext(ctx, Rt_NullOpContext, heightProvider, dbReadOnly = true)

        val defCtx = Rt_DefinitionContext(exeCtx, false, rrQuery.base.defId)
        val rtArgs = translateQueryArgs(defCtx, rrQuery, args)
        val rtResult = interpreter.callQuery(rrQuery, exeCtx, rtArgs)

        val resultType = interpreter.resolveType(rrQuery.type())
        val gtvResult = resultType.gtvConversion!!.rtToGtv(rtResult, GTV_QUERY_PRETTY)
        return gtvResult
    }

    private fun <T> getRoutine(kind: String, map: Map<MountName, T>, name: String): T {
        val mountName = MountName.ofOpt(name)
        mountName ?: throw UserMistake("$kind mount name is invalid: '$name")

        val r = map[mountName]
        return r ?: throw UserMistake("$kind not found: '$name'")
    }

    fun createExecutionContext(
        eCtx: EContext,
        opCtx: Rt_OpContext,
        heightProvider: Rt_ChainHeightProvider,
        dbReadOnly: Boolean,
    ): Rt_ExecutionContext {
        val sqlMapping = Rt_ChainSqlMapping(eCtx.chainID)

        val chainDeps = chainDeps.mapValues { (_, rid) -> Rt_ChainDependency(rid) }

        val sqlExec = createSqlExecutor(eCtx)
        val sqlCtx = Rt_RegularSqlContext.create(rrApp, sqlMapping, chainDeps, sqlExec, heightProvider, interpreter)

        return Rt_ExecutionContext(appCtx, opCtx, sqlCtx, sqlExec, dbReadOnly)
    }

    private fun createSqlExecutor(eCtx: EContext): SqlExecutor {
        var sqlInterceptor = env.sqlInterceptor

        val conLogger = SqlConnectionLogger.getOrNull(config.sqlLogging)
        sqlInterceptor = ConnectionSqlManager.wrapSqlInterceptor(sqlInterceptor, conLogger)

        sqlInterceptor = Rt_SqlManagerUtils.wrapSqlInterceptor(sqlInterceptor, globalCtx.logSqlErrors)

        var sqlCon = SqlManagerConnection.create(eCtx.conn)
        sqlCon = InterceptingSqlManagerConnection.wrap(sqlCon, sqlInterceptor)

        return sqlCon.createExecutor()
    }

    private fun translateQueryArgs(
        defCtx: Rt_DefinitionContext,
        rrQuery: RR_QueryDefinition,
        gtvArgs: Gtv,
    ): ImmList<Rt_Value> {
        gtvArgs is GtvDictionary
        val params = rrQuery.params()

        val argMap = gtvArgs.asDict()

        val invalidArgs = argMap.keys
            .filterNot { it == NON_STRICT_QUERY_ARGUMENT }
            .filter { argName ->
                params.none { it.name.str == argName }
            }
        if (invalidArgs.isNotEmpty()) {
            val code = "query:invalid_args:${rrQuery.base.appLevelName}:${invalidArgs.joinToString(",")}"
            throw Rt_Exception.common(code, "Invalid argument(s): ${invalidArgs.joinToString()}")
        }

        val gtvToRtCtx = GtvToRtContext.make(
            pretty = GTV_QUERY_PRETTY,
            compilerOptions = defCtx.globalCtx.compilerOptions,
            defaultValueEvaluator = GtvToRtDefaultValueEvaluator.getNormal(defCtx.exeCtx),
        )

        val missingArgs = mutableListOf<String>()
        val rtArgsList = mutableListOf<Rt_Value>()
        for (param in params) {
            val arg = argMap[param.name.str]
            if (arg != null) {
                val rtArg = RellPcUtils.convertArg(gtvToRtCtx, interpreter, param, arg)
                rtArgsList.add(rtArg)
            } else if (param.defaultExpr != null) {
                val rtArg = interpreter.evaluateParamDefault(param, defCtx)
                rtArgsList.add(rtArg)
            } else {
                missingArgs.add(param.name.str)
            }
        }

        if (missingArgs.isNotEmpty()) {
            val code = "query:missing_args:${rrQuery.base.appLevelName}:${missingArgs.joinToString(",")}"
            throw Rt_Exception.common(code, "Missing argument(s): ${missingArgs.joinToString()}")
        }

        val rtArgs = rtArgsList.toImmList()
        gtvToRtCtx.finish(defCtx.exeCtx)

        return rtArgs
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = immListOf()
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = immListOf()

    override fun initializeSnapshotContext(context: SnapshotContext) {
        snapshotContext = context
    }

    override fun getInitialDatums(ctx: EContext): List<SnapshotDatum> {
        val sqlMapping = Rt_ChainSqlMapping(ctx.chainID)
        val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(rrApp, sqlMapping)
        val sqlExec = createSqlExecutor(ctx)

        objectSnapshotIds = Rt_SnapshotSqlUtils.initObjectSnapshotIds(sqlCtx, sqlExec, rrApp.sqlDefs.objects)

        val datums = mutableListOf<SnapshotDatum>()
        datums.add(SnapshotDatum(0, GtvFactory.gtv(listOf()), false))

        for (obj in rrApp.sqlDefs.objects) {
            val objRowid = objectSnapshotIds.getValue(obj.rEntity.sqlMapping.metaName)
            val objData = Rt_SnapshotSqlUtils.readObjectState(sqlCtx, sqlExec, obj.rEntity, interpreter)
            datums.add(SnapshotDatum(objRowid, objData, false))
        }

        return datums
    }

    override fun getPermanentDatumIdMax(ctx: EContext) = null

    override fun getPermanentDatums(
        ctx: EContext,
        datumIdFrom: Long,
        datumHandler: (datum: SnapshotDatum?) -> Boolean,
    ) {
    }

    override fun initializeImport(ctx: EContext) {
        initDb(ctx, true)
    }

    override fun finalizeImport(ctx: EContext) {
        val sqlMapping = Rt_ChainSqlMapping(ctx.chainID)
        val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(rrApp, sqlMapping)
        val sqlExec = createSqlExecutor(ctx)

        addEntityConstraints(sqlCtx, sqlExec)

        val rowidTable = DSL.table(DSL.name(sqlCtx.mainChainMapping().rowidTable))
        val update = DSL.update(rowidTable)
            .set(DSL.field(DSL.name("last_value"), Long::class.java), DSL.field("?", Long::class.java))
        val sql = ParameterizedSql(
            renderJooq(update),
            listOf(Rt_IntValue.get(snapshotMaxRowid)).toImmList(),
        )

        sqlExec.execute(sql)
    }

    private fun addEntityConstraints(sqlCtx: Rt_SqlContext, sqlExec: SqlExecutor) {
        val allEntities = rrApp.sqlDefs.entities + rrApp.sqlDefs.objects.map { it.rEntity }

        for (entity in allEntities) {
            for (stmt in SqlGen.genEntityConstraintsAndIndexes(sqlCtx, entity, interpreter)) {
                sqlExec.execute(stmt)
            }
        }
    }

    override fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>) {
        val sqlMapping = Rt_ChainSqlMapping(ctx.chainID)
        val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(rrApp, sqlMapping)
        val sqlExec = createSqlExecutor(ctx)

        for (datum in datumList) {
            processDatum(sqlExec, sqlCtx, datum)
        }
    }

    private fun processDatum(sqlExec: SqlExecutor, sqlCtx: Rt_SqlContext, datum: SnapshotDatum) {
        val rowid = datum.id
        if (rowid == 0L) {
            return
        }

        snapshotMaxRowid = max(snapshotMaxRowid, rowid)

        val array = datum.data.asArray()
        if (array.isEmpty()) {
            return
        }

        val entityName = array[0].asString()
        val values = array[1].asDict()

        val rEntity = entityMap[entityName]
        if (rEntity != null) {
            processEntity(sqlExec, sqlCtx, rEntity, rowid, values, isObject = false)
            return
        }

        val rObject = objectMap.getValue(entityName)
        processEntity(sqlExec, sqlCtx, rObject.rEntity, rowid, values, isObject = true)
    }

    private fun processEntity(
        sqlExec: SqlExecutor,
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
        rowid: Long,
        values: Map<String, Gtv>,
        isObject: Boolean,
    ) {
        val gtvCtx = GtvToRtContext.make(false)
        val rtValues = entity.attributes.values.map { attr ->
            val rtType = interpreter.resolveType(attr.type)
            values[attr.name]?.let { rtType.gtvConversion!!.gtvToRt(gtvCtx, it) } ?: gtvCtx.getDefaultValue(entity, attr, interpreter)
        }

        if (isObject) {
            val sql = Rt_SnapshotSqlUtils.delete(sqlCtx, entity)
            sqlExec.execute(sql)
        }

        val sql = Rt_SnapshotSqlUtils.insert(sqlCtx, entity, rowid, rtValues)
        sqlExec.execute(sql)
    }
}

class RellPostchainModuleEnvironment(
    val outPrinter: Rt_Printer = Rt_OutPrinter,
    val logPrinter: Rt_Printer = Rt_LogPrinter(),
    val combinedPrinter: Rt_Printer? = null,
    val copyOutputToPrinter: Boolean = false,
    val logCompilerMessages: Boolean = true,
    val wrapCtErrors: Boolean = true,
    val wrapRtErrors: Boolean = true,
    val forceTypeCheck: Boolean = false,
    val dbInitEnabled: Boolean = true,
    val dbInitLogLevel: Int = DEFAULT_DB_INIT_LOG_LEVEL,
    val hiddenLib: Boolean = false,
    val ideDocSymbolsEnabled: Boolean = false,
    val useLatestRellVersion: Boolean = false,
    val sqlLog: Boolean = false,
    val fallbackModules: List<ModuleName> = immListOf(ModuleName.EMPTY),
    val precompiledApp: RellGtxModuleApp? = null,
    val txContextFactory: Rt_PostchainTxContextFactory = Rt_DefaultPostchainTxContextFactory,
    val sqlInterceptor: SqlInterceptor? = null,
) {
    companion object {
        val DEFAULT = RellPostchainModuleEnvironment()

        const val DEFAULT_DB_INIT_LOG_LEVEL = SqlInitLogging.LOG_STEP_COMPLEX

        private val THREAD_LOCAL = ThreadLocalContext(DEFAULT)

        fun get() = THREAD_LOCAL.get()

        fun set(env: RellPostchainModuleEnvironment, code: () -> Unit) {
            THREAD_LOCAL.set(env, code)
        }
    }
}

class RellPostchainModuleFactory(env: RellPostchainModuleEnvironment? = null): GTXModuleFactory {
    private val env = env ?: RellPostchainModuleEnvironment.get()

    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule {
        val gtxNode = config.asDict().getValue("gtx").asDict()
        val rellNode = gtxNode.getValue("rell").asDict()

        val combinedPrinter = env.combinedPrinter ?: env.logPrinter

        val errorHandler = ErrorHandler(combinedPrinter, env.wrapCtErrors, env.wrapRtErrors)

        return errorHandler.handleError({ "Module initialization failed" }) {
            val modApp = getApp(rellNode, errorHandler)
            val bcRid = Bytes32(blockchainRID.data)
            val chainCtx = Rt_ChainContext(config, bcRid)
            val chainDeps = getGtxChainDependencies(config)
            val moduleArgsSource = PostchainBaseUtils.createModuleArgsSource(modApp.rrApp, config, modApp.compilerOptions)
            val strictGtvConversion = (rellNode["strictGtvConversion"]?.asBoolean() ?: false)

            val modLogPrinter = getModulePrinter(env.logPrinter, Rt_TimestampPrinter(combinedPrinter))
            val modOutPrinter = getModulePrinter(env.outPrinter, combinedPrinter)

            val typeCheck = env.forceTypeCheck || (rellNode["typeCheck"]?.asBoolean() ?: false)
            val dbInitLogLevel = rellNode["dbInitLogLevel"]?.asInteger()?.toInt() ?: env.dbInitLogLevel

            val nativeEnv = PostchainRellNativeEnvironment(config, blockchainRID)
            val nativeProvider = PostchainNativeUtils.getNativeProvider(nativeEnv, rellNode["native"])

            val moduleConfig = RellModuleConfig(
                sqlLogging = env.sqlLog,
                typeCheck = typeCheck,
                dbInitLogLevel = dbInitLogLevel,
                compilerOptions = modApp.compilerOptions,
                strictGtvConversion = strictGtvConversion,
            )

            RellPostchainModule(
                env,
                moduleConfig,
                modApp.rrApp,
                modApp.compilationSysFns,
                chainCtx,
                chainDeps,
                logPrinter = modLogPrinter,
                outPrinter = modOutPrinter,
                errorHandler = errorHandler,
                moduleArgsSource = moduleArgsSource,
                gtvHashCalculator = getGtvHashCalculator(modApp.compilerOptions, config),
                nativeProvider = nativeProvider,
            )
        }
    }

    private fun getApp(
        rellNode: Map<String, Gtv>,
        errorHandler: ErrorHandler,
    ): RellGtxModuleApp {
        if (env.precompiledApp != null) {
            return env.precompiledApp
        }

        val rellCfg = RellGtvConfig(env, rellNode)

        val cResult = rellCfg.compile()
        val rrApp = processCompilationResult(cResult, errorHandler)

        for (moduleName in rellCfg.modules) {
            val module = rrApp.module(moduleName)
            if (module != null && module.test) {
                throw UserMistake("Test module specified as a main module: '$moduleName'")
            }
        }

        return RellGtxModuleApp(rrApp, rellCfg.compilerOptions, cResult.compilationSysFns)
    }

    private fun getModulePrinter(basePrinter: Rt_Printer, combinedPrinter: Rt_Printer): Rt_Printer {
        return if (env.copyOutputToPrinter) Rt_MultiPrinter(basePrinter, combinedPrinter) else basePrinter
    }

    private fun processCompilationResult(
        cResult: C_CompilationResult,
        errorHandler: ErrorHandler,
    ): RR_App {
        for (message in cResult.messages) {
            val str = message.toString()

            if (env.logCompilerMessages) {
                when (message.type) {
                    C_MessageType.WARNING -> logger.warn(str)
                    C_MessageType.ERROR -> logger.error(str)
                }
            }

            if (env.copyOutputToPrinter) {
                errorHandler.printer.print(str)
            }
        }

        val errors = cResult.errors

        val rrApp = cResult.rrApp
        if (rrApp != null) {
            return rrApp
        }

        if (env.copyOutputToPrinter) {
            errorHandler.printer.print("Compilation failed")
        }

        errorHandler.ignoreError()

        throw if (env.wrapCtErrors) {
            val error = if (errors.isEmpty()) null else errors[0]
            UserMistake(error?.text ?: "Compilation error")
        } else if (errors.isNotEmpty()) {
            val error = errors[0]
            C_Error.other(error.pos, error.code, error.text)
        } else {
            IllegalStateException("Compilation error")
        }
    }

    private fun getGtxChainDependencies(data: Gtv): Map<String, ByteArray> {
        val gtvDeps = data["dependencies"] ?: return mapOf()

        return buildMap {
            for (entry in gtvDeps.asArray()) {
                val entryArray = entry.asArray()
                checkEquals(entryArray.size, 2)
                val name = entryArray[0].asString()
                val rid = entryArray[1].asByteArray(true)
                check(name !in this)
                this[name] = rid
            }
        }
    }

    private fun getGtvHashCalculator(
        compilerOptions: C_CompilerOptions,
        config: Gtv,
    ): PostchainGtvUtils.HashCalculator {
        var version = PostchainBaseUtils.getBlockchainConfigHashVersion(config)
        if (version == 2 && !PostchainGtvUtils.HASH_V2_SWITCH.isActive(compilerOptions)) {
            version = 1
        }
        return PostchainGtvUtils.HashCalculator(version)
    }

    private class RellGtvConfig(
        private val env: RellPostchainModuleEnvironment,
        rellNode: Map<String, Gtv>,
    ) {
        val modules = getModuleNames(rellNode)

        private val sourceCfg = SourceCodeConfig(rellNode)

        val sourceDir = sourceCfg.dir
        val compilerOptions = getCompilerOptions(sourceCfg.version)

        fun compile(): C_CompilationResult {
            return C_Compiler.compile(sourceDir, modules.toImmList(), compilerOptions)
        }

        private fun getModuleNames(rellNode: Map<String, Gtv>): List<ModuleName> {
            val modulesNode = rellNode["modules"]

            val names = (modulesNode?.asArray() ?: arrayOf()).map {
                val s = it.asString()
                ModuleName.ofOpt(s) ?: throw UserMistake("Invalid module name: '$s'")
            }

            return names.ifEmpty { env.fallbackModules }
        }

        private fun getCompilerOptions(langVersion: R_LangVersion?): C_CompilerOptions {
            val actualVersion = if (env.useLatestRellVersion) RellVersions.VERSION else langVersion
            val baseOpts = actualVersion?.let { C_CompilerOptions.forLangVersion(it) } ?: C_CompilerOptions.DEFAULT
            return baseOpts.toBuilder()
                .hiddenLib(env.hiddenLib)
                .ideDocSymbolsEnabled(env.ideDocSymbolsEnabled)
                .build()
        }
    }

    companion object: KLogging()
}

private class SourceCodeConfig(rellNode: Map<String, Gtv>) {
    val dir: C_SourceDir
    val version: R_LangVersion?

    init {
        val ver = getSourceVersion(rellNode)
        val textSources = getSourceCodes(rellNode, ver, false)
        val fileSources = getSourceCodes(rellNode, ver, true)
        val allSources = textSources + fileSources

        if (allSources.isEmpty()) {
            throw UserMistake("Source code not specified in the configuration")
        } else if (allSources.size > 1) {
            val s = allSources.map { it.key }.sorted().joinToString()
            throw UserMistake("Multiple source code nodes specified in the configuration: $s")
        }

        val source = allSources.first()
        if (source.legacy && ver != null) {
            val verKey = RellGtxConfigConstants.LANG_VERSION_KEY
            throw UserMistake("Keys '${source.key}' and '$verKey' cannot be specified together")
        }

        val sourcesNode = rellNode.getValue(source.key)

        val fileMap = sourcesNode.asDict()
                .mapValues { (_, v) ->
                    val s = v.asString()
                    if (source.files) CommonUtils.readFileText(s) else s
                }
                .mapKeys { (k, _) -> parseSourcePath(k) }
                .mapValuesToImmMap { (k, v) -> C_TextSourceFile(k, v) }

        version = getCompatibilityVersion(rellNode, source.version)
        dir = C_SourceDir.mapDir(fileMap)
    }

    private fun getCompatibilityVersion(rellNode: Map<String, Gtv>, sourceVersion: R_LangVersion): R_LangVersion? {
        RellVersions.checkCompatibilityVersion(sourceVersion) { UserMistake(it) }

        val compilerVersion = getCompilerVersion(rellNode)

        if (compilerVersion == null) {
            if (sourceVersion >= RellVersions.MIN_COMPILER_VERSION) {
                throw UserMistake("${RellGtxConfigConstants.COMPILER_VERSION_KEY} not specified in configuration")
            }
            return null
        }

        if (compilerVersion < RellVersions.MIN_COMPILER_VERSION) {
            throw UserMistake("Bad ${RellGtxConfigConstants.COMPILER_VERSION_KEY}: $compilerVersion")
        }
        if (sourceVersion > compilerVersion) {
            val langVerKey = RellGtxConfigConstants.LANG_VERSION_KEY
            val compVerKey = RellGtxConfigConstants.COMPILER_VERSION_KEY
            throw UserMistake("$langVerKey ($sourceVersion) is newer than $compVerKey ($compilerVersion)")
        }

        return sourceVersion
    }

    private fun getSourceVersion(rellNode: Map<String, Gtv>): R_LangVersion? {
        val key = RellGtxConfigConstants.LANG_VERSION_KEY
        val ver = getVersionValue(rellNode, key)
        if (ver != null && ver !in RellVersions.SUPPORTED_VERSIONS) {
            throw UserMistake("Unknown $key: $ver")
        }
        return ver
    }

    private fun getCompilerVersion(rellNode: Map<String, Gtv>): R_LangVersion? {
        val key = RellGtxConfigConstants.COMPILER_VERSION_KEY
        val ver = getVersionValue(rellNode, key)
        if (ver != null && ver < RellVersions.MAX_SUPPORTED_VERSION && ver !in RellVersions.SUPPORTED_VERSIONS) {
            throw UserMistake("Unknown $key: $ver")
        }
        return ver
    }

    private fun getVersionValue(rellNode: Map<String, Gtv>, key: String): R_LangVersion? {
        val verStr = rellNode[key]?.asString()
        verStr ?: return null

        val ver = try {
            R_LangVersion.of(verStr)
        } catch (_: IllegalArgumentException) {
            throw UserMistake("Invalid $key: $verStr")
        }

        return ver
    }

    private fun getSourceCodes(rellNode: Map<String, Gtv>, ver: R_LangVersion?, files: Boolean): ImmList<SourceCode> {
        val res = mutableListOf<SourceCode>()

        val key = if (files) RellGtxConfigConstants.FILES_KEY else RellGtxConfigConstants.SOURCES_KEY

        if (key in rellNode) {
            val verKey = RellGtxConfigConstants.LANG_VERSION_KEY
            ver ?: throw UserMistake("Configuration key '$key' is specified, but '$verKey' is missing")
            res.add(SourceCode(key, ver, files, false))
        }

        val regex = Regex("${key}_v(\\d.*)")
        val legacy = rellNode.keys
                .mapNotNull { regex.matchEntire(it) }
                .map {
                    val k = it.groupValues[0]
                    val legacyVer = processLegacySourceKeyVersion(k, it.groupValues[1], key)
                    SourceCode(k, legacyVer, files, true)
                }
        res.addAll(legacy)

        return res.toImmList()
    }

    private fun processLegacySourceKeyVersion(key: String, s: String, keyPrefix: String): R_LangVersion {
        return when (s) {
            "0.10" -> R_LangVersion.of("0.10.4")
            else -> {
                val verKey = RellGtxConfigConstants.LANG_VERSION_KEY
                throw UserMistake("Invalid source code key: $key; use '$keyPrefix' and '$verKey' instead")
            }
        }
    }

    private fun parseSourcePath(s: String): C_SourcePath {
        val path = C_SourcePath.parseOrNull(s)
        return path ?: throw UserMistake("Invalid file path: '$s'")
    }

    private class SourceCode(val key: String, val version: R_LangVersion, val files: Boolean, val legacy: Boolean)
}

private object RellPcUtils {
    fun convertArg(ctx: GtvToRtContext, interpreter: Rt_Interpreter, param: RR_FunctionParam, arg: Gtv): Rt_Value {
        val subCtx = ctx.updateSymbol(GtvToRtSymbol_ParamName(param.name), true)
        val rtType = interpreter.resolveType(param.type)
        return rtType.gtvConversion!!.gtvToRt(subCtx, arg)
    }
}
