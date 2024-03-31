/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import kotlin.test.assertEquals

class RellCodeTester(
    tstCtx: RellTestContext,
    entityDefs: List<String> = listOf(),
    inserts: List<String> = listOf(),
    gtv: Boolean = false,
): RellBaseTester(tstCtx, entityDefs, inserts, gtv) {
    var gtvResult: Boolean = gtv
        set(value) {
            checkNotInited()
            field = value
        }

    var gtvResultRaw = false

    var dropTables = true
    var createTables = true
    var wrapInit = false
    var strictToString = true
    var opContext: Rt_OpContext = Rt_NullOpContext
    var sqlUpdatePortionSize = 1000
    var replModule: String? = null
    var typeCheck: Boolean = true
    var wrapFunctionCallErrors = true

    private val chainDependencies = mutableMapOf<String, TestChainDependency>()
    private val postInitOps = mutableListOf<String>()

    private var rtErrStack = listOf<R_StackPos>()

    override fun initSqlReset(sqlExec: SqlExecutor, moduleCode: String, app: R_App) {
        val globalCtx = createInitGlobalCtx()
        val exeCtx = createExeCtx(globalCtx, sqlExec, app)

        if (dropTables) {
            SqlUtils.dropAll(sqlExec, false)
        }

        if (createTables) {
            val chainId = exeCtx.sqlCtx.mainChainMapping().chainId
            SqlTestUtils.createSysBlockchainTables(exeCtx.sqlExec, chainId)

            SqlInit.init(exeCtx, NullSqlInitProjExt, SqlInitLogging())
        }
    }

    fun createInitGlobalCtx(): Rt_GlobalContext {
        val compilerOptions = compilerOptions()
        return Rt_GlobalContext(
                compilerOptions,
                Rt_FailingPrinter,
                Rt_FailingPrinter,
                logSqlErrors = false,
                typeCheck = true,
        )
    }

    fun chainDependency(name: String, rid: String, height: Long) {
        check(name !in chainDependencies)
        val bcRid = RellTestUtils.strToBlockchainRid(rid)
        chainDependencies[name] = TestChainDependency(bcRid, height)
    }

    fun chainDependencies() = chainDependencies.mapValues { (_, v) -> Pair(v.rid, v.height) }.toImmMap()

    fun postInitOp(code: String) {
        checkNotInited()
        postInitOps.add(code)
    }

    override fun postInit() {
        for (code in postInitOps) {
            chkOp(code, "OK")
        }
    }

    override fun chkEx(code: String, expected: String) {
        val queryCode = "query q() $code"
        chkFull(queryCode, "q", listOf(), expected)
    }

    fun chkEx(code: String, args: List<Any>, expected: String) {
        val args2 = args.map { v ->
            when (v) {
                is Boolean -> "boolean" to Rt_BooleanValue.get(v)
                is Long -> "integer" to Rt_IntValue.get(v)
                else -> throw IllegalArgumentException(v.javaClass.name)
            }
        }

        val params = args2.mapIndexed { i, v ->
            val argName = ('a' + i).toString()
            "$argName: ${v.first}"
        }.joinToString(", ")

        chkFull("query q($params) $code", args2.map { it.second }, expected)
    }

    override fun chkFull(code: String, expected: String) = chkFull(code, listOf(), expected)

    fun chkFull(code: String, args: List<Rt_Value>, expected: String) = chkFull(code, "q", args, expected)

    fun chkFull(code: String, name: String, args: List<Rt_Value>, expected: String) {
        val actual = callQuery(code, name, args)
        checkResult(expected, actual)
    }

    fun chkVirtual(type: String, expr: String, arg: Gtv, expected: String) {
        chkVirtualEx(type, "= $expr;", arg, expected)
    }

    fun chkVirtualEx(type: String, body: String, arg: Gtv, expected: String) {
        chkFullGtv("query q(x: $type) $body", listOf(arg), expected)
    }

    fun chkFullGtv(code: String, args: List<Gtv>, expected: String) {
        val encoder = resultEncoder()
        val actual = callQuery0(code, "q", args, GtvTestUtils::decodeGtvQueryArgs, encoder)
        checkResult(expected, actual)
    }

    fun chkQueryGtv(code: String, expected: String) {
        val queryCode = "query q() $code"
        val encoder = resultEncoder()
        val actual = callQuery0(queryCode, "q", listOf(), GtvTestUtils::decodeGtvStrQueryArgs, encoder)
        checkResult(expected, actual)
    }

    fun chkQueryGtvEx(code: String, args: List<String>, expected: String) {
        val encoder = resultEncoder()
        val actual = callQuery0(code, "q", args, GtvTestUtils::decodeGtvStrQueryArgs, encoder)
        checkResult(expected, actual)
    }

    fun chkQueryType(bodyCode: String, expected: String) {
        val queryCode = "query q() $bodyCode"
        val moduleCode = moduleCode(queryCode)
        val actual = processApp(moduleCode) { module ->
            module.queries.getValue(R_MountName.of("q")).type().strCode()
        }
        checkResult(expected, actual)
    }

    fun chkOp(bodyCode: String, expected: String = "OK") {
        val opCode = "operation o() { $bodyCode }"
        chkOpEx(opCode, "o", expected)
    }

    fun chkOpEx(opCode: String, name: String, expected: String) {
        val actual = callOp(opCode, name, listOf())
        checkResult(expected, actual)
    }

    fun chkOpExGtv(code: String, args: List<Gtv>, expected: String) {
        val actual = callOp0(code, "o", args, GtvTestUtils::decodeGtvOpArgs)
        checkResult(expected, actual)
    }

    fun chkOpExGtvStr(code: String, args: List<String>, expected: String) {
        val actual = callOp0(code, "o", args, GtvTestUtils::decodeGtvStrOpArgs)
        checkResult(expected, actual)
    }

    fun chkFn(code: String, expected: String) = chkFnFull("function f() $code", expected)

    fun chkFnFull(code: String, expected: String) {
        val actual = callFn(code)
        checkResult(expected, actual)
    }

    private fun callFn(code: String): String {
        init()
        val moduleCode = moduleCode(code)
        return processWithExeCtx(moduleCode) { modCtx ->
            RellTestUtils.callFn(modCtx, "f", listOf(), strictToString)
        }
    }

    fun callQuery(code: String, name: String, args: List<Rt_Value>): String {
        val encoder = resultEncoder()
        return callQuery0(code, name, args, { _, v -> v }, encoder)
    }

    fun callQueryGtv(code: String, name: String, args: List<Rt_Value>): Gtv {
        val encoder = { t: R_Type, v: Rt_Value -> PostchainGtvUtils.gtvToBytes(t.rtToGtv(v, true)).toHex() }
        val str = callQuery0(code, name, args, { _, v -> v }, encoder)
        val bytes = str.hexStringToByteArray()
        return PostchainGtvUtils.bytesToGtv(bytes)
    }

    private fun resultEncoder(): (R_Type, Rt_Value) -> String {
        return when {
            gtvResult && gtvResultRaw -> RellTestUtils.ENCODER_GTV_STRICT
            gtvResult -> RellTestUtils.ENCODER_GTV
            strictToString -> RellTestUtils.ENCODER_STRICT
            else -> RellTestUtils.ENCODER_PLAIN
        }
    }

    private fun <T> callQuery0(
        code: String,
        name: String,
        args: List<T>,
        decoder: (List<R_FunctionParam>, List<T>) -> List<Rt_Value>,
        encoder: (R_Type, Rt_Value) -> String,
    ): String {
        val evalRes = eval.eval {
            if (wrapInit) {
                eval.wrapAll { init() }
            } else {
                init()
            }

            val moduleCode = moduleCode(code)

            processWithExeCtx(moduleCode) { appCtx ->
                RellTestUtils.callQueryGeneric(eval, appCtx, name, args, decoder, encoder)
            }
        }

        rtErrStack = eval.errorStack()
        return evalRes
    }

    private fun callOp(code: String, name: String, args: List<Rt_Value>): String {
        return callOp0(code, name, args) { _, v -> v }
    }

    fun callOpGtv(code: String, args: List<Gtv>): String {
        return callOp0(code, "o", args, GtvTestUtils::decodeGtvOpArgs)
    }

    fun callOpGtvStr(code: String, args: List<String>): String {
        return callOp0(code, "o", args, GtvTestUtils::decodeGtvStrOpArgs)
    }

    private fun <T> callOp0(code: String, name: String, args: List<T>, decoder: (List<R_FunctionParam>, List<T>) -> List<Rt_Value>): String {
        init()
        val moduleCode = moduleCode(code)
        return processWithAppSqlCtx(moduleCode) { appCtx, sqlCtx ->
            RellTestUtils.callOpGeneric(appCtx, opContext, sqlCtx, tstCtx.sqlMgr(), name, args, decoder)
        }
    }

    fun chkWarn(vararg msgs: String) {
        val actual = messages.filter { it.type == C_MessageType.WARNING }.map { it.code }.sorted()
        val expected = msgs.toList()
        assertEquals(expected, actual)
    }

    fun chkStack(vararg expected: String) {
        val actual = rtErrStack.map { it.toString() }
        assertEquals(expected.toList(), actual)
    }

    fun chkInit(expected: String) {
        val actual = RellTestUtils.catchRtErr {
            init()
            "OK"
        }
        assertEquals(expected, actual)
    }

    fun resetSqlBuffer() {
        tstCtx.resetSqlBuffer()
    }

    fun chkSql(vararg expected: String) {
        tstCtx.chkSql(expected.toList())
    }

    fun chkSqlCtr(expected: Int) {
        tstCtx.chkSqlCtr(expected)
    }

    fun chkVerCt(code: String, version: String, expOld: String, expNew: String) {
        chkVer0(code, version, expOld, expNew, ::chkCompile)
    }

    fun createRepl(): RellReplTester {
        val files = files()
        val moduleNameStr = replModule
        val module = if (moduleNameStr == null) null else R_ModuleName.of(moduleNameStr)

        val sourceDir = C_SourceDir.mapDirOf(files)
        val globalCtx = createGlobalCtx()

        val sqlMgr = tstCtx.sqlMgr()
        val modArgsSource = createModuleArgsSource(globalCtx.compilerOptions)
        return RellReplTester(tstCtx.projExt, globalCtx, sourceDir, sqlMgr, modArgsSource, module)
    }

    private fun processWithExeCtx(code: String, processor: (Rt_ExecutionContext) -> String): String {
        val globalCtx = createGlobalCtx()
        return processApp(code) { app ->
            RellTestUtils.catchRtErr {
                tstCtx.sqlMgr().access { sqlExec ->
                    val exeCtx = createExeCtx(globalCtx, sqlExec, app)
                    processor(exeCtx)
                }
            }
        }
    }

    private fun processWithAppSqlCtx(code: String, processor: (Rt_AppContext, Rt_SqlContext) -> String): String {
        val globalCtx = createGlobalCtx()
        val res = processApp(code) { app ->
            RellTestUtils.catchRtErr {
                val (appCtx, sqlCtx) = tstCtx.sqlMgr().access { sqlExec ->
                    val appCtx0 = createAppCtx(globalCtx, app, C_SourceDir.EMPTY, false)
                    val sqlCtx0 = createSqlCtx(app, sqlExec)
                    Pair(appCtx0, sqlCtx0)
                }
                processor(appCtx, sqlCtx)
            }
        }
        return res
    }

    fun chkTests(testModule: String, expected: String) {
        testModules(testModule) // Side effect, not good.
        val actual = runTests()
        assertEquals(expected, actual)
    }

    private fun runTests(): String {
        val res = processWithTestRunnerCtx { testRunnerCtx ->
            val testFns = UnitTestRunner.getTestFunctions(testRunnerCtx.app, UnitTestMatcher.ANY)
                    .map { UnitTestCase(UnitTestRunnerChain("foo", 123), it) }
                    .toImmList()

            val testRes = UnitTestRunnerResults()
            UnitTestRunner.runTests(testRunnerCtx, testFns, testRes)

            val resList = testRes.getResults()
            val resMap = resList
                    .map {
                        val err = it.res.error
                        it.case.fn.moduleLevelName to (if(err == null) "OK" else RellTestUtils.rtErrToResult(err).res)
                    }.toMap().toImmMap()

            resMap.entries.joinToString(",")
        }

        return res
    }

    private fun processWithTestRunnerCtx(processor: (UnitTestRunnerContext) -> String): String {
        val globalCtx = createGlobalCtx()
        val moduleCode = moduleCode("")

        return processApp(moduleCode) { app ->
            val sqlCtx = tstCtx.sqlMgr().access { sqlExec ->
                createSqlCtx(app, sqlExec)
            }

            val sourceDir = createSourceDir(moduleCode)
            val modArgs = getModuleArgs()
            val modArgsGtv = GtvTestUtils.moduleArgsToMap(modArgs).mapKeys { R_ModuleName.of(it.key) }
            val blockRunner = tstCtx.projExt.createUnitTestBlockRunner(sourceDir, app, modArgsGtv)

            val testRunnerCtx = UnitTestRunnerContext(
                sqlCtx = sqlCtx,
                printer = Rt_OutPrinter,
                sqlMgr = tstCtx.sqlMgr(),
                sqlInitProjExt = tstCtx.projExt.getSqlInitProjExt(),
                globalCtx = globalCtx,
                chainCtx = createChainContext(),
                blockRunner = blockRunner,
                moduleArgsSource = createModuleArgsSource(globalCtx.compilerOptions),
                app = app,
            )

            try {
                processor(testRunnerCtx)
            } catch (e: Rt_Exception) {
                "RTE:${e.err.code()}"
            }
        }
    }

    private fun createGlobalCtx(): Rt_GlobalContext {
        val compilerOptions = compilerOptions()

        return Rt_GlobalContext(
                compilerOptions,
                outPrinter,
                logPrinter,
                logSqlErrors = true,
                sqlUpdatePortionSize = sqlUpdatePortionSize,
                typeCheck = typeCheck,
                wrapFunctionCallErrors = wrapFunctionCallErrors,
        )
    }

    private fun createChainContext(gtvConfig: Gtv = GtvNull): Rt_ChainContext {
        val bcRid = Bytes32(blockchainRid.hexStringToByteArray())
        return Rt_ChainContext(gtvConfig, bcRid)
    }

    private fun createModuleArgsSource(options: C_CompilerOptions): Rt_GtvModuleArgsSource {
        val modArgs = getModuleArgs()
        val gtvMap = modArgs
            .map {
                val modName = R_ModuleName.of(it.key)
                val gtv = GtvTestUtils.strToGtv(it.value)
                modName to gtv
            }.toImmMap()
        return Rt_GtvModuleArgsSource(gtvMap, options)
    }

    fun createAppCtx(
        globalCtx: Rt_GlobalContext,
        app: R_App,
        sourceDir: C_SourceDir,
        test: Boolean,
    ): Rt_AppContext {
        val chainCtx = createChainContext(GtvNull)
        val blockRunner = createBlockRunner(sourceDir, app)
        val moduleArgsSource = createModuleArgsSource(globalCtx.compilerOptions)
        return Rt_AppContext(
            globalCtx,
            chainCtx,
            app,
            repl = false,
            test = test,
            blockRunner = blockRunner,
            moduleArgsSource = moduleArgsSource,
        )
    }

    private fun createBlockRunner(sourceDir: C_SourceDir, app: R_App): Rt_UnitTestBlockRunner {
        val modArgs = getModuleArgs()
        val modArgsGtv = GtvTestUtils.moduleArgsToMap(modArgs).mapKeys { R_ModuleName.of(it.key) }
        return tstCtx.projExt.createUnitTestBlockRunner(sourceDir, app, modArgsGtv)
    }

    fun createExeCtx(
            globalCtx: Rt_GlobalContext,
            sqlExec: SqlExecutor,
            app: R_App,
            sourceDir: C_SourceDir = C_SourceDir.EMPTY,
            test: Boolean = false
    ): Rt_ExecutionContext {
        val appCtx = createAppCtx(globalCtx, app, sourceDir, test)
        val sqlCtx = createSqlCtx(app, sqlExec)
        return Rt_ExecutionContext(appCtx, opContext, sqlCtx, sqlExec)
    }

    private fun createSqlCtx(app: R_App, sqlExec: SqlExecutor): Rt_SqlContext {
        val sqlMapping = createChainSqlMapping()
        val rtDeps = chainDependencies.mapValues { (_, v) -> Rt_ChainDependency(v.rid.data) }

        val heightMap = chainDependencies.mapKeys { (_, v) -> WrappedByteArray(v.rid.data) }.mapValues { (_, v) -> v.height }
        val heightProvider = TestChainHeightProvider(heightMap)

        return eval.wrapRt {
            Rt_RegularSqlContext.create(app, sqlMapping, rtDeps, sqlExec, heightProvider)
        }
    }

    private class TestChainDependency(val rid: BlockchainRid, val height: Long)

    private class TestChainHeightProvider(private val map: Map<WrappedByteArray, Long>): Rt_ChainHeightProvider {
        override fun getChainHeight(rid: WrappedByteArray, id: Long): Long? {
            val height = map[rid]
            return height
        }
    }
}
