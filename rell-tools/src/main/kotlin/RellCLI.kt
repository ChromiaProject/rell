/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.varargValues
import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.*
import net.postchain.rell.api.gtx.*
import net.postchain.rell.api.shell.RellApiShellInternal
import net.postchain.rell.api.shell.ReplIo
import net.postchain.rell.api.shell.ReplShell
import net.postchain.rell.api.shell.ReplShellOptions
import net.postchain.rell.base.compiler.base.core.C_AtAttrShadowing
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlManager
import net.postchain.rell.base.sql.SqlUtils
import net.postchain.rell.base.utils.*
import net.postchain.rell.gtx.Rt_PostchainOpContext
import net.postchain.rell.gtx.Rt_PostchainTxContext
import net.postchain.rell.module.RellPostchainModuleEnvironment
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.inputStream

@Suppress("unused")
private val INIT = run {
    RellToolsLogUtils.initLogging()
}

fun main(args: Array<String>) {
    RellToolsUtils.runCli(args, RellInterpreterCommand())
}

private const val ARG_TEST = "--test"

private class RellInterpreterCommand: RellBaseCommand("rell") {
    val dbUrl by option(
        "--db-url", metavar = "DB_URL",
        help = "Database JDBC URL, e. g. jdbc:postgresql://localhost/relltestdb?user=relltestuser&password=1234"
    )
    val dbProperties by option(
        "-p", "--db-properties", metavar = "DB_PROPERTIES",
        help = "Database connection properties file (same format as node-config.properties)"
    )
    val test by option(ARG_TEST, metavar = "MODULE").varargValues(min = 0)
    val resetdb by option("--resetdb", help = "Reset database (drop everything)").flag()
    val sqlLog by option("--sqllog", help = "Enable SQL logging").flag()
    val sqlInitLog by option("--sqlinitlog", help = "Enable SQL tables structure update logging").flag()
    val typeCheck by option("--typecheck", help = "Run-time type checking (debug)").flag()
    val quiet by option("-q", "--quiet", help = "No useless messages").flag()
    val version by option("-v", "--version", help = "Print version and quit").flag()
    val jsonArgs by option("--json-args", help = "Accept Rell program arguments in JSON format").flag()
    val jsonResult by option("--json-result", help = "Print Rell program result in JSON format").flag()
    val json by option("--json", help = "Equivalent to --json-args --json-result").flag()
    val batch by option("--batch", help = "Run in non-interactive mode (do not start shell)").flag()
    val noHistory by option("--no-history", help = "Disable REPL command history").flag()
    val extraOptions by option("-X", metavar = "OPTION", help = "Extra compiler/interpreter option").multiple()
    val module by argument("MODULE", help = "Module name").optional()
    val entry by argument("ENTRY", help = "Entry point (operation/query/function name)").optional()
    val entryArgs by argument("ARGS", help = "Entry point arguments").multiple()

    override fun run() {
        if (version) {
            val ver = Rt_RellVersion.getInstance()?.buildDescriptor ?: "Version unknown"
            println(ver)
            throw RellCliExitException(0)
        }

        val extraOptions = extraOptions.mapToImmList { parseExtraOptionCli(it) }
        val compilerOptions = getCompilerOptions(extraOptions)
        val argsEx = RellCliArgsEx(this, compilerOptions)

        if (dbUrl != null && dbProperties != null) {
            throw RellCliBasicException("Both database URL and properties specified")
        }

        val dbSpecified = dbUrl != null || dbProperties != null

        if (resetdb && !dbSpecified) {
            throw RellCliBasicException("Database connection URL not specified")
        }

        val testModules = test
        if (testModules != null && module != null) {
            throw RellCliBasicException("Main module must not be specified when $ARG_TEST argument is used")
        }

        if (resetdb && module == null && batch && testModules == null) {
            resetDatabase(this)
            return
        }

        val globalCtx = createGlobalCtx(argsEx)

        if (testModules != null) {
            runMultiModuleTests(argsEx, testModules)
            return
        }

        val (entryModule, entryRoutine) = parseEntryPoint(this)

        if (batch || (entryModule != null && entryRoutine != null)) {
            val compiled = RellToolsUtils.compileApp(sourceDir, entryModule, quiet, compilerOptions)
            val module = if (entryModule == null) null else compiled.rrApp.moduleMap[entryModule]
            if (module != null && module.test) {
                runSingleModuleTests(argsEx, compiled, module, entryRoutine)
            } else {
                runApp(globalCtx, this, dbSpecified, entryModule, entryRoutine, compiled)
            }
        } else if (entryModule != null) {
            val compiled = RellToolsUtils.compileApp(sourceDir, entryModule, quiet, compilerOptions)
            val module = compiled.rrApp.moduleMap[entryModule]
            if (module != null && module.test) {
                runSingleModuleTests(argsEx, compiled, module, entryRoutine)
            } else {
                runRepl(argsEx, entryModule)
            }
        } else {
            runRepl(argsEx, entryModule)
        }
    }

    private fun getCompilerOptions(extraOpts: List<ExtraOption>): C_CompilerOptions {
        val b = C_CompilerOptions.builder()
            .compatibility(RellVersions.VERSION)
        extraOpts.forEach { it.toCompilerOption(b) }
        return b.build()
    }

    private fun runApp(
        globalCtx: Rt_GlobalContext,
        args: RellInterpreterCommand,
        dbSpecified: Boolean,
        entryModule: ModuleName?,
        entryRoutine: QualifiedName?,
        compiled: RellCompiledApp,
    ) {
        val interpreter = compiled.createInterpreter()
        val appCtx = createRegularAppContext(globalCtx, interpreter)
        val launcher = getAppLauncher(args, appCtx, entryModule, entryRoutine)
        if (launcher == null && !args.resetdb) {
            return
        }

        runWithSqlManager(args, true) { sqlMgr ->
            val sqlCtx = RellApiBaseUtils.createSqlContext(compiled.rrApp)
            if (dbSpecified) {
                initDatabase(appCtx, args, sqlMgr, sqlCtx)
            }
            launcher?.launch(appCtx, sqlMgr, sqlCtx)
        }
    }

    private fun runSingleModuleTests(
        args: RellCliArgsEx,
        compiled: RellCompiledApp,
        rrModule: RR_Module,
        entryRoutine: QualifiedName?,
    ) {
        val fns = compiled.getRRTestFunctions(rrModule.name, UnitTestMatcher.ANY)
            .filter { entryRoutine == null || it.base.defName.qualifiedName == entryRoutine.str() }
        runTests(args, compiled, fns)
    }

    private fun runMultiModuleTests(args: RellCliArgsEx, modules: List<String>) {
        val rModules = if (modules.isEmpty()) {
            immListOf(ModuleName.EMPTY)
        } else {
            modules.mapToImmList { ModuleName.ofOpt(it) ?: throw RellCliBasicException("Invalid module name: '$it'") }
        }

        val sourceDir = RellApiBaseUtils.createSourceDir(args.raw.sourceDir)
        val modSel = C_CompilerModuleSelection(immListOf(), rModules)
        val compiled = RellToolsUtils.compileApp(sourceDir, modSel, args.raw.quiet, C_CompilerOptions.DEFAULT)

        val testFns = compiled.getAllRRTestFunctions(UnitTestMatcher.ANY)
        runTests(args, compiled, testFns)
    }

    private fun runTests(args: RellCliArgsEx, compiled: RellCompiledApp, fns: List<RR_FunctionDefinition>) {
        val globalCtx = createGlobalCtx(args)
        val chainCtx = RellApiBaseUtils.createChainContext()
        val sqlCtx = RellApiBaseUtils.createSqlContext(compiled.rrApp)

        val sourceDir = RellApiBaseUtils.createSourceDir(args.raw.sourceDir)
        val blockRunner = createBlockRunner(args, sourceDir, compiled.rrApp)

        val allOk = runWithSqlManager(args.raw, true) { sqlMgr ->
            val testCtx = UnitTestRunnerContext(
                app = compiled.rrApp,
                printer = Rt_OutPrinter,
                sqlCtx = sqlCtx,
                sqlMgr = sqlMgr,
                sqlInitProjExt = PostchainSqlInitProjExt,
                globalCtx = globalCtx,
                chainCtx = chainCtx,
                blockRunner = blockRunner,
                moduleArgsSource = Rt_ModuleArgsSource.NULL,
                compilationSysFns = compiled.compilationSysFns,
            )

            val cases = fns.map { UnitTestCase(null, it) }
            UnitTestRunner.runTests(testCtx, cases)
        }

        if (!allOk) {
            throw RellCliExitException(1)
        }
    }

    private fun createBlockRunner(args: RellCliArgsEx, sourceDir: C_SourceDir, rrApp: RR_App): Rt_UnitTestBlockRunner {
        val keyPair = Lib_RellTest.BLOCK_RUNNER_KEYPAIR

        val blockRunnerConfig = Rt_BlockRunnerConfig(
            forceTypeCheck = args.raw.typeCheck,
            sqlLog = args.raw.sqlLog,
            dbInitLogLevel = RellPostchainModuleEnvironment.DEFAULT_DB_INIT_LOG_LEVEL,
        )

        val blockRunnerModules = RellApiBaseUtils.getMainModules(rrApp).toImmList()
        val compileConfig = RellApiCompile.Config.Builder()
            .cliEnv(RellCliEnv.NULL)
            .build()
        val blockRunnerStrategy = Rt_DynamicBlockRunnerStrategy(sourceDir, keyPair, blockRunnerModules, compileConfig)

        return Rt_PostchainUnitTestBlockRunner(keyPair, blockRunnerConfig, blockRunnerStrategy)
    }

    private fun runRepl(args: RellCliArgsEx, moduleName: ModuleName?) {
        runWithSqlManager(args.raw, false) { sqlMgr ->
            if (args.raw.resetdb) {
                sqlMgr.transaction { sqlExec ->
                    SqlUtils.dropAll(sqlExec, true)
                }
            }

            val globalCtx = createGlobalCtx(args)
            val sourceDir = RellApiBaseUtils.createSourceDir(args.raw.sourceDir)
            val blockRunnerCfg = Rt_BlockRunnerConfig()
            val projExt = PostchainReplInterpreterProjExt(PostchainSqlInitProjExt, blockRunnerCfg)

            val historyFile = if (args.raw.noHistory) null else RellApiShellInternal.getDefaultReplHistoryFile()

            val shellOptions = ReplShellOptions(
                compilerOptions = args.compilerOptions,
                inputChannelFactory = ReplIo.DEFAULT_INPUT_FACTORY,
                outputChannelFactory = ReplIo.DEFAULT_OUTPUT_FACTORY,
                historyFile = historyFile,
                printIntroMessage = true,
                moduleArgs = immMapOf(),
            )

            ReplShell.start(
                sourceDir,
                moduleName,
                globalCtx,
                sqlMgr,
                projExt,
                shellOptions,
            )
        }
    }

    private fun resetDatabase(args: RellInterpreterCommand) {
        runWithSqlManager(args, true) { sqlMgr ->
            sqlMgr.transaction { sqlExec ->
                SqlUtils.dropAll(sqlExec, true)
            }
        }
        println("Database cleared")
    }

    private fun initDatabase(
        appCtx: Rt_AppContext,
        args: RellInterpreterCommand,
        sqlMgr: SqlManager,
        sqlCtx: Rt_SqlContext
    ) {
        SqlUtils.initDatabase(appCtx, sqlCtx, sqlMgr, PostchainSqlInitProjExt, args.resetdb, args.sqlInitLog)
    }

    private fun parseEntryPoint(args: RellInterpreterCommand): Pair<ModuleName?, QualifiedName?> {
        val m = args.module
        val e = args.entry

        if (m == null) {
            return Pair(null, null)
        }

        val moduleName = ModuleName.ofOpt(m) ?: throw RellCliBasicException("Invalid module name: '$m'")

        var routineName: QualifiedName? = null
        if (e != null) {
            routineName = QualifiedName.ofOpt(e)
            if (routineName == null || routineName.isEmpty()) throw RellCliBasicException("Invalid entry point name: '$e'")
        }

        return Pair(moduleName, routineName)
    }

    private fun getAppLauncher(
        args: RellInterpreterCommand,
        appCtx: Rt_AppContext,
        entryModule: ModuleName?,
        entryRoutine: QualifiedName?
    ): RellAppLauncher? {
        if (entryModule == null || entryRoutine == null) return null
        val entryPoint = findEntryPoint(appCtx, entryModule, entryRoutine)
        return RellAppLauncher(args, entryPoint)
    }

    private fun <T> runWithSqlManager(args: RellInterpreterCommand, sqlErrorLog: Boolean, code: (SqlManager) -> T): T {
        val dbUrl = getDbUrl(args)
        return RellApiGtxUtils.runWithSqlManager(dbUrl, args.sqlLog, sqlErrorLog, null, code)
    }

    private fun getDbUrl(args: RellInterpreterCommand): String? {
        val dbUrl = args.dbUrl
        if (dbUrl != null) {
            return dbUrl
        }

        val dbProperties = args.dbProperties
        dbProperties ?: return null

        val props = Properties()
        Path(dbProperties).inputStream().use { ins ->
            props.load(ins)
        }

        val baseUrl = props.getValue("database.url")

        val query = listOf(
            "user" to props.getValue("database.username"),
            "password" to props.getValue("database.password"),
            "currentSchema" to props.getValue("database.schema"),
        ).joinToString("&") {
            "${it.first}=${it.second}"
        }

        return "$baseUrl?$query"
    }

    private fun findEntryPoint(appCtx: Rt_AppContext, moduleName: ModuleName, routineName: QualifiedName): RellEntryPoint {
        val rrApp = appCtx.rrApp
        val interpreter = appCtx.interpreter
        val rrModule = rrApp.moduleMap[moduleName]
            ?: throw RellCliBasicException("Module not found: '$moduleName'")

        val name = routineName.str()
        val mountName = MountName(routineName.parts)
        val eps = mutableListOf<RellEntryPoint>()

        val rrOp = rrModule.operations[name] ?: rrApp.operations[mountName]
        if (rrOp != null) {
            val time = System.currentTimeMillis() / 1000
            val opCtx = Rt_PostchainOpContext(
                txCtx = Rt_CliPostchainTxContext,
                lastBlockTime = time,
                transactionIid = -1,
                blockHeight = -1,
                opIndex = -1,
                signers = immListOf(),
                allOperations = immListOf()
            )
            eps.add(RellEntryPoint_Operation(interpreter, rrOp, opCtx))
        }

        val rrQuery = rrModule.queries[name] ?: rrApp.queries[mountName]
        if (rrQuery != null) eps.add(RellEntryPoint_Query(interpreter, rrQuery))

        val rrFn = rrModule.functions[name]
        if (rrFn != null) eps.add(RellEntryPoint_Function(interpreter, rrFn))

        if (eps.isEmpty()) {
            throw RellCliBasicException("Found no operation, query or function with name '$name'")
        } else if (eps.size > 1) {
            throw RellCliBasicException("Found more than one definition with name '$name': ${eps.joinToString { it.kind }}")
        }

        return eps[0]
    }

    private fun createRegularAppContext(globalCtx: Rt_GlobalContext, interpreter: Rt_Interpreter): Rt_AppContext {
        val chainCtx = RellApiBaseUtils.createChainContext()
        return Rt_AppContext(globalCtx, chainCtx, interpreter)
    }

    private fun createGlobalCtx(args: RellCliArgsEx): Rt_GlobalContext {
        return RellApiBaseUtils.createGlobalContext(
            args.compilerOptions,
            typeCheck = args.raw.typeCheck,
        )
    }

    private fun parseArgs(
        entryPoint: RellEntryPoint,
        interpreter: Rt_Interpreter,
        gtvCtx: GtvToRtContext,
        args: List<String>,
        json: Boolean,
    ): List<Rt_Value> {
        val params = entryPoint.params()
        if (args.size != params.size) {
            System.err.println("Wrong number of arguments: ${args.size} instead of ${params.size}")
            throw RellCliExitException(1)
        }
        return args.withIndex().map { (idx, arg) -> parseArg(interpreter, gtvCtx, params[idx], arg, json) }
    }

    private fun parseArg(interpreter: Rt_Interpreter, gtvCtx: GtvToRtContext, param: RR_FunctionParam, arg: String, json: Boolean): Rt_Value {
        val rtType = interpreter.resolveType(param.type)

        if (json) {
            val gtvConv = rtType.gtvConversion
                ?: throw RellCliBasicException("Parameter '${param.name}' of type ${rtType.name} cannot be converted from Gtv")
            val gtv = PostchainGtvUtils.jsonToGtv(arg)
            return gtvConv.gtvToRt(gtvCtx, gtv)
        }

        try {
            return fromCliRR(interpreter, rtType, arg)
        } catch (_: UnsupportedOperationException) {
            throw RellCliBasicException("Parameter '${param.name}' has unsupported type: ${rtType.name}")
        } catch (_: Exception) {
            throw RellCliBasicException("Invalid value for type ${rtType.name}: '$arg'")
        }
    }

    private fun resultToString(res: Rt_Value, json: Boolean): String {
        return if (json) {
            val rtType = res.type()
            val gtvConv = rtType.gtvConversion
                ?: throw RellCliBasicException("Result of type '${rtType.name}' cannot be converted to Gtv")
            val gtv = gtvConv.rtToGtv(res, true)
            PostchainGtvUtils.gtvToJson(gtv)
        } else {
            res.toString()
        }
    }

    private sealed class ExtraOption {
        abstract fun toCompilerOption(b: C_CompilerOptions.Builder)

        class AtAttrShadowing(val v: C_AtAttrShadowing): ExtraOption() {
            override fun toCompilerOption(b: C_CompilerOptions.Builder) {
                b.atAttrShadowing(v)
            }
        }

        object HiddenLib: ExtraOption() {
            override fun toCompilerOption(b: C_CompilerOptions.Builder) {
                b.hiddenLib(true)
            }
        }
    }

    private fun parseExtraOptionCli(s: String): ExtraOption {
        return try {
            parseExtraOption(s)
        } catch (_: Throwable) {
            throw RellCliBasicException("Invalid extra option value: '$s'")
        }
    }

    private fun parseExtraOption(s: String): ExtraOption {
        val parts = s.split(":")
        require(parts.isNotEmpty())
        val opt = parts[0]
        val params = parts.subList(1, parts.size)
        return when (opt) {
            "AtAttrShadowing" -> {
                require(params.size == 1) { "AtAttrShadowing option takes only one parameter" }
                val p = params[0]
                require(p == p.lowercase()) { "AtAttrShadowing option value must be lowercase" }
                val v = C_AtAttrShadowing.valueOf(p.uppercase())
                ExtraOption.AtAttrShadowing(v)
            }

            "HiddenLib" -> {
                require(params.isEmpty()) { "HiddenLib option does not take any parameters" }
                ExtraOption.HiddenLib
            }

            else -> throw IllegalArgumentException()
        }
    }

    private inner class RellAppLauncher(
        private val args: RellInterpreterCommand,
        private val entryPoint: RellEntryPoint
    ) {
        fun launch(appCtx: Rt_AppContext, sqlMgr: SqlManager, sqlCtx: Rt_SqlContext) {
            val opCtx = entryPoint.opContext()

            val rtRes = sqlMgr.execute(entryPoint.transaction) { sqlExec ->
                val exeCtx = Rt_ExecutionContext(appCtx, opCtx, sqlCtx, sqlExec)

                val gtvCtx = GtvToRtContext.make(pretty = true, compilerOptions = exeCtx.globalCtx.compilerOptions)
                val rtArgs = parseArgs(entryPoint, appCtx.interpreter, gtvCtx, args.entryArgs, args.json || args.jsonArgs)
                gtvCtx.finish(exeCtx)

                callEntryPoint(exeCtx, rtArgs)
            }

            if (rtRes != null && rtRes != Rt_UnitValue) {
                val strRes = resultToString(rtRes, args.jsonResult || args.json)
                println(strRes)
            }
        }

        private fun callEntryPoint(exeCtx: Rt_ExecutionContext, rtArgs: List<Rt_Value>): Rt_Value? {
            val res = try {
                entryPoint.call(exeCtx, rtArgs)
            } catch (e: Rt_Exception) {
                val msg = Rt_Utils.appendStackTrace("ERROR ${e.message}", e.info.stack)
                System.err.println(msg)
                throw RellCliExitException(1)
            }
            return res
        }
    }

    private object Rt_CliPostchainTxContext: Rt_PostchainTxContext() {
        override fun emitEvent(type: String, data: Gtv) {
            throw Rt_Utils.errNotSupported("Function emit_event() not supported")
        }
    }

    private sealed class RellEntryPoint {
        abstract val kind: String
        abstract val transaction: Boolean
        abstract fun params(): List<RR_FunctionParam>
        abstract fun opContext(): Rt_OpContext
        abstract fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value?
    }

    private class RellEntryPoint_Function(
        private val interpreter: Rt_Interpreter,
        private val f: RR_FunctionDefinition,
    ): RellEntryPoint() {
        override val kind = "function"
        override val transaction = false
        override fun params() = f.fnBase.params
        override fun opContext() = Rt_NullOpContext

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
            return interpreter.callFunction(f, exeCtx, args, dbUpdateAllowed = true)
        }
    }

    private class RellEntryPoint_Operation(
        private val interpreter: Rt_Interpreter,
        private val o: RR_OperationDefinition,
        private val opCtx: Rt_OpContext,
    ): RellEntryPoint() {
        override val kind = "operation"
        override val transaction = true
        override fun params() = o.params
        override fun opContext() = opCtx

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value? {
            interpreter.callOperation(o, exeCtx, args)
            return null
        }
    }

    private class RellEntryPoint_Query(
        private val interpreter: Rt_Interpreter,
        private val q: RR_QueryDefinition,
    ): RellEntryPoint() {
        override val kind = "query"
        override val transaction = false
        override fun params() = q.params()
        override fun opContext() = Rt_NullOpContext

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
            return interpreter.callQuery(q, exeCtx, args)
        }
    }

    private class RellCliArgsEx(val raw: RellInterpreterCommand, val compilerOptions: C_CompilerOptions)
}
