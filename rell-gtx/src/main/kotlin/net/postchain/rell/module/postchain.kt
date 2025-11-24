/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.NON_STRICT_QUERY_ARGUMENT
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.gtx.PostchainBaseUtils
import net.postchain.rell.gtx.Rt_CheckCorrectnessPostchainTxContext
import net.postchain.rell.gtx.Rt_DefaultPostchainTxContextFactory
import net.postchain.rell.gtx.Rt_PostchainOpContext
import net.postchain.rell.gtx.Rt_PostchainTxContextFactory
import org.apache.commons.lang3.time.FastDateFormat

private class ErrorHandler(
    val printer: Rt_Printer,
    private val wrapCtErrors: Boolean,
    private val wrapRtErrors: Boolean,
) {
    private var ignore = false

    fun ignoreError() {
        ignore = true
    }

    fun <T> handleError(msgSupplier: () -> String, code: () -> T): T {
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
        } catch (e: Exception) {
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg, e)
        } catch (e: Throwable) {
            val msg = processError(msgSupplier, e)
            throw ProgrammerMistake(msg)
        }
    }

    private fun processError(msgSupplier: () -> String, e: Throwable, stack: List<R_StackPos> = listOf()): String {
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
        private val DATE_FMT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss,SSS")
    }

    override fun print(str: String) {
        val time = System.currentTimeMillis()
        val timeStr = DATE_FMT.format(time)
        val str2 = "$timeStr $str"
        printer.print(str2)
    }
}

private class RellGTXOperation(
    private val module: RellPostchainModule,
    private val rOperation: R_OperationDefinition,
    private val errorHandler: ErrorHandler,
    opData: ExtOpData,
): GTXOperation(opData) {
    private val gtvArgs = data.args.toImmList()

    override fun checkCorrectness(ctxt: EContext) {
        handleError {
            val exeCtx = makeCheckCorrectnessExeCtx(ctxt)
            val gtvCtx = makeGtvToRtContext(GtvToRtDefaultValueEvaluator.getError(), validateOnly = true)
            val rtArgs = makeArgs(exeCtx, gtvCtx)
            gtvCtx.finish(exeCtx)
            rOperation.executeGuard(exeCtx, rtArgs)
        }
    }

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) = checkCorrectness(ctxt)
    override fun isCompound(): Boolean = rOperation.modifiers.isCompound
    override fun isSinglePerTransaction(): Boolean = rOperation.modifiers.isSingular

    override fun apply(ctx: TxEContext): Boolean {
        handleError {
            val exeCtx = makeApplyExeCtx(ctx)
            val gtvCtx = makeGtvToRtContext(GtvToRtDefaultValueEvaluator.getNormal(exeCtx))
            val rtArgs = makeArgs(exeCtx, gtvCtx)
            gtvCtx.finish(exeCtx)
            rOperation.call(exeCtx, rtArgs)
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
        )
        val heightProvider = Rt_TxChainHeightProvider(ctx)
        return module.createExecutionContext(ctx, opCtx, heightProvider, dbReadOnly = false)
    }

    private fun makeArgs(exeCtx: Rt_ExecutionContext, gtvCtx: GtvToRtContext): List<Rt_Value> {
        val opArgs = getOpArgs(gtvCtx)
        val defCtx = Rt_DefinitionContext(exeCtx, true, rOperation.defId)
        val rtArgs = rOperation.params().mapIndexed { i, param ->
            val rtArg = opArgs.getOrNull(i)
            if (rtArg != null) rtArg else {
                val evaluator = param.getDefaultValueEvaluator()!!
                evaluator(defCtx)
            }
        }
        return rtArgs
    }

    private fun getOpArgs(gtvCtx: GtvToRtContext): List<Rt_Value> {
        val params = rOperation.params()

        val minParams = params.dropLastWhile { it.getDefaultValueEvaluator() != null }.size
        if (gtvArgs.size < minParams || gtvArgs.size > params.size) {
            throw Rt_Exception.common(
                "operation:[${rOperation.appLevelName}]:arg_count:${data.args.size}:${params.size}",
                "Wrong argument count: ${data.args.size} instead of ${params.size}",
            )
        }

        val rtArgs = gtvArgs.mapIndexed { i, arg ->
            val param = params[i]
            val rtArg = RellPcUtils.convertArg(gtvCtx, param, arg)
            param.validate(rtArg)?.raise()
            rtArg
        }

        return rtArgs
    }

    private fun makeGtvToRtContext(
        defaultValueEvaluator: GtvToRtDefaultValueEvaluator,
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
        return errorHandler.handleError({ "Operation '${rOperation.appLevelName}' failed" }) {
            code()
        }
    }

    private class Rt_TxChainHeightProvider(private val ctx: TxEContext): Rt_ChainHeightProvider {
        override fun getChainHeight(rid: WrappedByteArray, id: Long): Long? {
            return try {
                ctx.getChainDependencyHeight(id)
            } catch (e: Exception) {
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
    private val rApp: R_App,
    chainCtx: Rt_ChainContext,
    private val chainDeps: Map<String, ByteArray>,
    outPrinter: Rt_Printer,
    logPrinter: Rt_Printer,
    private val errorHandler: ErrorHandler,
    moduleArgsSource: Rt_ModuleArgsSource,
    gtvHashCalculator: PostchainGtvUtils.HashCalculator,
): GTXModule {
    private val operationNames = rApp.operations.keys.map { it.str() }.toImmSet()
    private val queryNames = rApp.queries.keys.map { it.str() }.toImmSet()

    private val globalCtx = Rt_GlobalContext(
        compilerOptions = config.compilerOptions,
        outPrinter = outPrinter,
        logPrinter = logPrinter,
        typeCheck = config.typeCheck,
    )

    private val appCtx = Rt_AppContext(
        globalCtx,
        chainCtx,
        rApp,
        moduleArgsSource = moduleArgsSource,
        gtvHashCalculator = gtvHashCalculator,
    )

    override fun getOperations(): Set<String> {
        return operationNames
    }

    override fun getQueries(): Set<String> {
        return queryNames
    }

    override fun initializeDB(ctx: EContext) {
        if (!env.dbInitEnabled) {
            return
        }

        errorHandler.handleError({ "Database initialization failed" }) {
            val heightProvider = Rt_ConstantChainHeightProvider(-1)
            val exeCtx = createExecutionContext(ctx, Rt_NullOpContext, heightProvider, dbReadOnly = false)
            val initLogging = SqlInitLogging.ofLevel(config.dbInitLogLevel)

            // Using the null ProjExt, because Postchain must do the initialization itself.
            SqlInit.init(exeCtx, NullSqlInitProjExt, initLogging)
        }
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        return errorHandler.handleError({ "Operation '${opData.opName}' failed" }) {
            val rOperation = getRoutine("Operation", rApp.operations, opData.opName)
            RellGTXOperation(this, rOperation, errorHandler, opData)
        }
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        return errorHandler.handleError({ "Query '$name' failed" }) {
            query0(ctxt, name, args)
        }
    }

    private fun query0(ctx: EContext, name: String, args: Gtv): Gtv {
        val rQuery = getRoutine("Query", rApp.queries, name)

        val heightProvider = Rt_ConstantChainHeightProvider(Long.MAX_VALUE)

        val exeCtx = createExecutionContext(ctx, Rt_NullOpContext, heightProvider, dbReadOnly = true)

        val defCtx = Rt_DefinitionContext(exeCtx, false, rQuery.defId)
        val rtArgs = translateQueryArgs(defCtx, rQuery, args)
        val rtResult = rQuery.call(defCtx, rtArgs)

        val type = rQuery.type()
        val gtvResult = type.rtToGtv(rtResult, GTV_QUERY_PRETTY)
        return gtvResult
    }

    private fun <T> getRoutine(kind: String, map: Map<R_MountName, T>, name: String): T {
        val mountName = R_MountName.ofOpt(name)
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
        val sqlCtx = Rt_RegularSqlContext.create(rApp, sqlMapping, chainDeps, sqlExec, heightProvider)

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
        rQuery: R_QueryDefinition,
        gtvArgs: Gtv,
    ): ImmList<Rt_Value> {
        gtvArgs is GtvDictionary
        val params = rQuery.params()

        val argMap = gtvArgs.asDict()

        val invalidArgs = argMap.keys
            .filterNot { it == NON_STRICT_QUERY_ARGUMENT }
            .filter { argName ->
                params.none { it.name.str == argName }
            }
        if (invalidArgs.isNotEmpty()) {
            val code = "query:invalid_args:${rQuery.appLevelName}:${invalidArgs.joinToString(",")}"
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
            val evaluator = param.getDefaultValueEvaluator()
            if (arg != null) {
                val rtArg = RellPcUtils.convertArg(gtvToRtCtx, param, arg)
                rtArgsList.add(rtArg)
            } else if (evaluator != null) {
                val rtArg = evaluator(defCtx)
                rtArgsList.add(rtArg)
            } else {
                missingArgs.add(param.name.str)
            }
        }

        if (missingArgs.isNotEmpty()) {
            val code = "query:missing_args:${rQuery.appLevelName}:${missingArgs.joinToString(",")}"
            throw Rt_Exception.common(code, "Missing argument(s): ${missingArgs.joinToString()}")
        }

        val rtArgs = rtArgsList.toImmList()
        gtvToRtCtx.finish(defCtx.exeCtx)

        return rtArgs
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = immListOf()
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = immListOf()
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
    val fallbackModules: List<R_ModuleName> = immListOf(R_ModuleName.EMPTY),
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

        @Suppress("UNUSED_VARIABLE") // Legacy...
        val moduleName = rellNode["moduleName"]?.asString() ?: ""

        val combinedPrinter = env.combinedPrinter ?: env.logPrinter
        val errorHandler = ErrorHandler(combinedPrinter, env.wrapCtErrors, env.wrapRtErrors)

        return errorHandler.handleError({ "Module initialization failed" }) {
            val modApp = getApp(rellNode, errorHandler)
            val bcRid = Bytes32(blockchainRID.data)
            val chainCtx = Rt_ChainContext(config, bcRid)
            val chainDeps = getGtxChainDependencies(config)
            val moduleArgsSource = PostchainBaseUtils.createModuleArgsSource(modApp.app, config, modApp.compilerOptions)
            val strictGtvConversion = (rellNode["strictGtvConversion"]?.asBoolean() ?: false)

            val modLogPrinter = getModulePrinter(env.logPrinter, Rt_TimestampPrinter(combinedPrinter))
            val modOutPrinter = getModulePrinter(env.outPrinter, combinedPrinter)

            val typeCheck = env.forceTypeCheck || (rellNode["typeCheck"]?.asBoolean() ?: false)
            val dbInitLogLevel = rellNode["dbInitLogLevel"]?.asInteger()?.toInt() ?: env.dbInitLogLevel

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
                modApp.app,
                chainCtx,
                chainDeps,
                logPrinter = modLogPrinter,
                outPrinter = modOutPrinter,
                errorHandler = errorHandler,
                moduleArgsSource = moduleArgsSource,
                gtvHashCalculator = getGtvHashCalculator(modApp.compilerOptions, config),
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
        val app = processCompilationResult(cResult, errorHandler)

        for (moduleName in rellCfg.modules) {
            val module = app.moduleMap[moduleName]
            if (module != null && module.test) {
                throw UserMistake("Test module specified as a main module: '$moduleName'")
            }
        }

        return RellGtxModuleApp(app, rellCfg.compilerOptions)
    }

    private fun getModulePrinter(basePrinter: Rt_Printer, combinedPrinter: Rt_Printer): Rt_Printer {
        return if (env.copyOutputToPrinter) Rt_MultiPrinter(basePrinter, combinedPrinter) else basePrinter
    }

    private fun processCompilationResult(
        cResult: C_CompilationResult,
        errorHandler: ErrorHandler,
    ): R_App {
        for (message in cResult.messages) {
            val str = message.toString()

            if (env.logCompilerMessages) {
                when (message.type) {
                    C_MessageType.WARNING -> logger.warn(str)
                    C_MessageType.ERROR -> logger.error(str)
                    else -> logger.info(str)
                }
            }

            if (env.copyOutputToPrinter) {
                errorHandler.printer.print(str)
            }
        }

        val errors = cResult.errors

        val rApp = cResult.app
        if (rApp != null && rApp.valid && errors.isEmpty()) {
            return rApp
        }

        if (env.copyOutputToPrinter) {
            errorHandler.printer.print("Compilation failed")
        }

        errorHandler.ignoreError()

        val err = if (env.wrapCtErrors) {
            val error = if (errors.isEmpty()) null else errors[0]
            UserMistake(error?.text ?: "Compilation error")
        } else if (errors.isNotEmpty()) {
            val error = errors[0]
            C_Error.other(error.pos, error.code, error.text)
        } else {
            IllegalStateException("Compilation error")
        }

        throw err
    }

    private fun getGtxChainDependencies(data: Gtv): Map<String, ByteArray> {
        val gtvDeps = data["dependencies"]
        if (gtvDeps == null) return mapOf()

        val deps = mutableMapOf<String, ByteArray>()

        for (entry in gtvDeps.asArray()) {
            val entryArray = entry.asArray()
            checkEquals(entryArray.size, 2)
            val name = entryArray[0].asString()
            val rid = entryArray[1].asByteArray(true)
            check(name !in deps)
            deps[name] = rid
        }

        return deps.toMap()
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

        private fun getModuleNames(rellNode: Map<String, Gtv>): List<R_ModuleName> {
            val modulesNode = rellNode["modules"]

            val names = (modulesNode?.asArray() ?: arrayOf()).map {
                val s = it.asString()
                R_ModuleName.ofOpt(s) ?: throw UserMistake("Invalid module name: '$s'")
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

    companion object: KLogging() {
        fun compileApp(config: Gtv, env: RellPostchainModuleEnvironment): Pair<C_CompilationResult, C_SourceDir> {
            val gtxNode = config.asDict().getValue("gtx").asDict()
            val rellNode = gtxNode.getValue("rell").asDict()
            val rellCfg = RellGtvConfig(env, rellNode)
            val cRes = rellCfg.compile()
            return cRes to rellCfg.sourceDir
        }
    }
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
        } catch (e: IllegalArgumentException) {
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
        val path = C_SourcePath.parseOpt(s)
        return path ?: throw UserMistake("Invalid file path: '$s'")
    }

    private class SourceCode(val key: String, val version: R_LangVersion, val files: Boolean, val legacy: Boolean)
}

private object RellPcUtils {
    fun convertArg(ctx: GtvToRtContext, param: R_FunctionParam, arg: Gtv): Rt_Value {
        val subCtx = ctx.updateSymbol(GtvToRtSymbol_Param(param), true)
        return param.type.gtvToRt(subCtx, arg)
    }
}