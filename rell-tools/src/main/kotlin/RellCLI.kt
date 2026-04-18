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
            val app = RellToolsUtils.compileApp(sourceDir, entryModule, quiet, compilerOptions)
            val module = if (entryModule == null) null else app.moduleMap[entryModule]
            if (module != null && module.test) {
                runSingleModuleTests(argsEx, app, module, entryRoutine)
            } else {
                runApp(globalCtx, this, dbSpecified, entryModule, entryRoutine, app)
            }
        } else if (entryModule != null) {
            val app = RellToolsUtils.compileApp(sourceDir, entryModule, quiet, compilerOptions)
            val module = app.moduleMap[entryModule]
            if (module != null && module.test) {
                runSingleModuleTests(argsEx, app, module, entryRoutine)
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
        entryModule: R_ModuleName?,
        entryRoutine: R_QualifiedName?,
        app: R_App
    ) {
        val launcher = getAppLauncher(args, app, entryModule, entryRoutine)
        if (launcher == null && !args.resetdb) {
            return
        }

        val appCtx = createRegularAppContext(globalCtx, app)

        runWithSqlManager(args, true) { sqlMgr ->
            val sqlCtx = RellApiBaseUtils.createSqlContext(app)
            if (dbSpecified) {
                initDatabase(appCtx, args, sqlMgr, sqlCtx)
            }
            launcher?.launch(appCtx, sqlMgr, sqlCtx)
        }
    }

    private fun runSingleModuleTests(
        args: RellCliArgsEx,
        app: R_App,
        module: R_Module,
        entryRoutine: R_QualifiedName?,
    ) {
        val fns = UnitTestRunner.getTestFunctions(module, UnitTestMatcher.ANY)
            .filter { entryRoutine == null || it.defName.qualifiedName == entryRoutine.str() }
        runTests(args, app, fns)
    }

    private fun runMultiModuleTests(args: RellCliArgsEx, modules: List<String>) {
        val rModules = if (modules.isEmpty()) {
            immListOf(R_ModuleName.EMPTY)
        } else {
            modules.mapToImmList { R_ModuleName.ofOpt(it) ?: throw RellCliBasicException("Invalid module name: '$it'") }
        }

        val sourceDir = RellApiBaseUtils.createSourceDir(args.raw.sourceDir)
        val modSel = C_CompilerModuleSelection(immListOf(), rModules)
        val app = RellToolsUtils.compileApp(sourceDir, modSel, args.raw.quiet, C_CompilerOptions.DEFAULT)

        val testFns = UnitTestRunner.getTestFunctions(app, UnitTestMatcher.ANY)
        runTests(args, app, testFns)
    }

    private fun runTests(args: RellCliArgsEx, app: R_App, fns: List<R_FunctionDefinition>) {
        val globalCtx = createGlobalCtx(args)
        val chainCtx = RellApiBaseUtils.createChainContext()
        val sqlCtx = RellApiBaseUtils.createSqlContext(app)

        val sourceDir = RellApiBaseUtils.createSourceDir(args.raw.sourceDir)
        val blockRunner = createBlockRunner(args, sourceDir, app)

        val allOk = runWithSqlManager(args.raw, true) { sqlMgr ->
            val testCtx = UnitTestRunnerContext(
                app,
                Rt_OutPrinter,
                sqlCtx,
                sqlMgr,
                PostchainSqlInitProjExt,
                globalCtx,
                chainCtx,
                blockRunner,
                moduleArgsSource = Rt_ModuleArgsSource.NULL,
            )

            val cases = fns.map { UnitTestCase(null, it) }
            UnitTestRunner.runTests(testCtx, cases)
        }

        if (!allOk) {
            throw RellCliExitException(1)
        }
    }

    private fun createBlockRunner(args: RellCliArgsEx, sourceDir: C_SourceDir, app: R_App): Rt_UnitTestBlockRunner {
        val keyPair = Lib_RellTest.BLOCK_RUNNER_KEYPAIR

        val blockRunnerConfig = Rt_BlockRunnerConfig(
            forceTypeCheck = args.raw.typeCheck,
            sqlLog = args.raw.sqlLog,
            dbInitLogLevel = RellPostchainModuleEnvironment.DEFAULT_DB_INIT_LOG_LEVEL,
        )

        val blockRunnerModules = RellApiBaseUtils.getMainModules(app).toImmList()
        val compileConfig = RellApiCompile.Config.Builder()
            .cliEnv(RellCliEnv.NULL)
            .build()
        val blockRunnerStrategy = Rt_DynamicBlockRunnerStrategy(sourceDir, keyPair, blockRunnerModules, compileConfig)

        return Rt_PostchainUnitTestBlockRunner(keyPair, blockRunnerConfig, blockRunnerStrategy)
    }

    private fun runRepl(args: RellCliArgsEx, moduleName: R_ModuleName?) {
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

    private fun parseEntryPoint(args: RellInterpreterCommand): Pair<R_ModuleName?, R_QualifiedName?> {
        val m = args.module
        val e = args.entry

        if (m == null) {
            return Pair(null, null)
        }

        val moduleName = R_ModuleName.ofOpt(m) ?: throw RellCliBasicException("Invalid module name: '$m'")

        var routineName: R_QualifiedName? = null
        if (e != null) {
            routineName = R_QualifiedName.ofOpt(e)
            if (routineName == null || routineName.isEmpty()) throw RellCliBasicException("Invalid entry point name: '$e'")
        }

        return Pair(moduleName, routineName)
    }

    private fun getAppLauncher(
        args: RellInterpreterCommand,
        app: R_App,
        entryModule: R_ModuleName?,
        entryRoutine: R_QualifiedName?
    ): RellAppLauncher? {
        if (entryModule == null || entryRoutine == null) return null
        val entryPoint = findEntryPoint(app, entryModule, entryRoutine)
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

    private fun findEntryPoint(app: R_App, moduleName: R_ModuleName, routineName: R_QualifiedName): RellEntryPoint {
        val module = app.modules.find { it.name == moduleName }
        if (module == null) {
            throw RellCliBasicException("Module not found: '$moduleName'")
        }

        val name = routineName.str()
        val mountName = R_MountName(routineName.parts)
        val eps = mutableListOf<RellEntryPoint>()

        val op = module.operations[name] ?: app.operations[mountName]
        if (op != null) {
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
            eps.add(RellEntryPoint_Operation(op, opCtx))
        }

        val q = module.queries[name] ?: app.queries[mountName]
        if (q != null) eps.add(RellEntryPoint_Query(q))

        val f = module.functions[name]
        if (f != null) eps.add(RellEntryPoint_Function(f))

        if (eps.isEmpty()) {
            throw RellCliBasicException("Found no operation, query or function with name '$name'")
        } else if (eps.size > 1) {
            throw RellCliBasicException("Found more than one definition with name '$name': ${eps.joinToString { it.kind }}")
        }

        val ep = eps[0]
        return ep
    }

    private fun createRegularAppContext(globalCtx: Rt_GlobalContext, app: R_App): Rt_AppContext {
        val chainCtx = RellApiBaseUtils.createChainContext()
        return Rt_AppContext(globalCtx, chainCtx, app)
    }

    private fun createGlobalCtx(args: RellCliArgsEx): Rt_GlobalContext {
        return RellApiBaseUtils.createGlobalContext(
            args.compilerOptions,
            typeCheck = args.raw.typeCheck,
        )
    }

    private fun parseArgs(
        entryPoint: RellEntryPoint,
        gtvCtx: GtvToRtContext,
        args: List<String>,
        json: Boolean,
    ): List<Rt_Value> {
        val params = entryPoint.routine().params()
        if (args.size != params.size) {
            System.err.println("Wrong number of arguments: ${args.size} instead of ${params.size}")
            throw RellCliExitException(1)
        }
        return args.withIndex().map { (idx, arg) -> parseArg(gtvCtx, params[idx], arg, json) }
    }

    private fun parseArg(gtvCtx: GtvToRtContext, param: R_FunctionParam, arg: String, json: Boolean): Rt_Value {
        val type = param.type

        if (json) {
            if (!type.completeFlags().gtv.fromGtv) {
                throw RellCliBasicException("Parameter '${param.name}' of type ${type.strCode()} cannot be converted from Gtv")
            }
            val gtv = PostchainGtvUtils.jsonToGtv(arg)
            return type.gtvToRt(gtvCtx, gtv)
        }

        try {
            return type.fromCli(arg)
        } catch (_: UnsupportedOperationException) {
            throw RellCliBasicException("Parameter '${param.name}' has unsupported type: ${type.strCode()}")
        } catch (_: Exception) {
            throw RellCliBasicException("Invalid value for type ${type.strCode()}: '$arg'")
        }
    }

    private fun resultToString(res: Rt_Value, json: Boolean): String {
        return if (json) {
            val type = res.type()
            if (!type.completeFlags().gtv.toGtv) {
                throw RellCliBasicException("Result of type '${type.strCode()}' cannot be converted to Gtv")
            }
            val gtv = type.rtToGtv(res, true)
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
                val rtArgs = parseArgs(entryPoint, gtvCtx, args.entryArgs, args.json || args.jsonArgs)
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
        abstract fun routine(): R_RoutineDefinition
        abstract fun opContext(): Rt_OpContext
        abstract fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value?
    }

    private class RellEntryPoint_Function(private val f: R_FunctionDefinition): RellEntryPoint() {
        override val kind = "function"
        override val transaction = false
        override fun routine() = f
        override fun opContext() = Rt_NullOpContext

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
            return f.callTop(exeCtx, args, true)
        }
    }

    private class RellEntryPoint_Operation(
        private val o: R_OperationDefinition,
        private val opCtx: Rt_OpContext,
    ): RellEntryPoint() {
        override val kind = "operation"
        override val transaction = true
        override fun routine() = o
        override fun opContext() = opCtx

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value? {
            o.call(exeCtx, args)
            return null
        }
    }

    private class RellEntryPoint_Query(private val q: R_QueryDefinition): RellEntryPoint() {
        override val kind = "query"
        override val transaction = false
        override fun routine() = q
        override fun opContext() = Rt_NullOpContext

        override fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
            return q.call(exeCtx, args)
        }
    }

    private class RellCliArgsEx(val raw: RellInterpreterCommand, val compilerOptions: C_CompilerOptions)
}
