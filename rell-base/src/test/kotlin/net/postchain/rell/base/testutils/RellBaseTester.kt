/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.*
import kotlin.test.assertEquals

abstract class RellBaseTester(
    val tstCtx: RellTestContext,
    entityDefs: List<String> = listOf(),
    inserts: List<String> = listOf(),
    gtv: Boolean = false,
) {
    private var inited = false
    private var lastInserts = listOf<String>()
    private var appProto: R_App? = null

    protected val messages = mutableListOf<C_Message>()

    var errMsgPos = false
    var gtv = gtv
    var deprecatedError = false
    var atAttrShadowing = C_CompilerOptions.DEFAULT.atAttrShadowing
    var defaultLib = true
    var testLib = false
    var hiddenLib = true
    var extraMod: C_LibModule? = null
    var allowDbModificationsInObjectExprs = C_CompilerOptions.DEFAULT.allowDbModificationsInObjectExprs
    var allowLibNamedArgsAnyVersion = C_CompilerOptions.DEFAULT.allowLibNamedArgsAnyVersion
    var allowOlderCompatibilityVersion = true
    var complexWhatEnabled = true
    var ideDefIdConflictError = true
    var compatibilityVer: R_LangVersion? = RellVersions.VERSION
    var blockchainRid = RellTestUtils.strToRidHex("DEADBEEF")

    var defs: List<String> = entityDefs
        set(value) {
            checkNotInited()
            field = value
        }

    private val defs0 = mutableListOf<String>()

    var chainId: Long = 0
        set(value) {
            checkNotInited()
            field = value
        }

    var inserts: List<String> = inserts

    private val files = mutableMapOf<String, String>()

    private val outPrinter0 = Rt_TestPrinter()
    private val logPrinter0 = Rt_TestPrinter()

    val outPrinter: Rt_Printer = outPrinter0
    val logPrinter: Rt_Printer = logPrinter0

    private var mainModules: List<String>? = null
    private var testModules: List<String>? = null

    var mainFile: String = RellTestUtils.MAIN_FILE

    fun init() {
        tstCtx.init()

        if (!inited) {
            val code = defsCode()
            val app = initCompile(code)

            if (tstCtx.useSql) {
                initSql(code, app)
            }

            appProto = app
            inited = true

            postInit()
        } else if (inserts != lastInserts) {
            tstCtx.sqlMgr().transaction { sqlExec ->
                if (!lastInserts.isEmpty()) {
                    SqlTestUtils.clearTables(sqlExec)
                }
                initSqlInserts(sqlExec)
            }
        }

        tstCtx.resetSqlBuffer()
    }

    private fun initCompile(code: String): R_App {
        val sourceDir = createSourceDir(code)
        val options = compilerOptions()
        val modSel = C_CompilerModuleSelection(mainModules(), testModules())

        val cRes = RellTestUtils.compileApp(sourceDir, modSel, options)

        if (cRes.errors.isNotEmpty()) {
            val err = cRes.errors[0]
            throw C_Error.other(err.pos, err.code, err.text)
        }

        return cRes.app!!
    }

    protected val eval = RellTestEval()

    private val expectedData = mutableListOf<String>()

    fun compilerOptions(): C_CompilerOptions {
        val opts = C_CompilerOptions(
            compatibility = compatibilityVer,
            gtv = gtv,
            deprecatedError = deprecatedError,
            blockCheck = true,
            atAttrShadowing = atAttrShadowing,
            defaultLib = defaultLib,
            testLib = testLib,
            hiddenLib = hiddenLib,
            allowDbModificationsInObjectExprs = allowDbModificationsInObjectExprs,
            symbolInfoFile = C_SourcePath.parse(mainFile),
            complexWhatEnabled = complexWhatEnabled,
            mountConflictError = true,
            appModuleInTestsError = false,
            useTestDependencyExtensions = false,
            allowLibNamedArgsAnyVersion = allowLibNamedArgsAnyVersion,
            allowOlderCompatibilityVersion = allowOlderCompatibilityVersion,
            ide = false,
            ideDocSymbolsEnabled = false,
            ideDefIdConflictError = ideDefIdConflictError,
            ideCompletions = null,
        )

        // Check serialization and deserialization of options (to not forget to serialize a new field).
        val pojo = opts.toPojoMap()
        val copy = C_CompilerOptions.fromPojoMap(pojo)
        check(copy == opts)

        return opts
    }

    fun def(defs: List<String>) {
        checkNotInited()
        defs0.addAll(defs)
        this.defs = defs0
    }

    fun def(def: String) {
        def(listOf(def))
    }

    fun insert(table: String, columns: String, vararg rows: String) {
        inserts = inserts + rows.map { SqlTestUtils.mkins(table, columns, it) }
    }

    fun insert(inserts: List<String>) {
        this.inserts += inserts
    }

    fun file(path: String, text: String) {
        checkNotInited()
        check(path !in files)
        check(path != mainFile)
        files[path] = text
    }

    fun files() = files.toImmMap()

    protected fun files(code: String): Map<String, String> {
        return files().unionNoConflicts(mapOf(mainFile to code))
    }

    fun mainModule(vararg modules: String) {
        checkNotInited()
        mainModules = modules.toList().toImmList()
    }

    fun testModules(vararg modules: String) {
        checkNotInited()
        testModules = modules.toList().toImmList()
    }

    protected fun mainModules() = (mainModules ?: listOf("")).map { R_ModuleName.of(it) }
    protected fun testModules() = (testModules ?: listOf()).map { R_ModuleName.of(it) }

    private var moduleArgs = immMapOf<String, String>()

    fun moduleArgs(vararg args: Pair<String, String>) {
        moduleArgs = args.toMap().toImmMap()
    }

    fun compatibilityVer(v: String?) {
        compatibilityVer = if (v == null) null else R_LangVersion.of(v)
    }

    fun getModuleArgs() = moduleArgs

    protected fun checkNotInited() {
        check(!inited)
    }

    private fun initSql(moduleCode: String, app: R_App) {
        tstCtx.sqlMgr().transaction { sqlExec ->
            initSqlReset(sqlExec, moduleCode, app)
            initSqlInserts(sqlExec)
        }
    }

    protected abstract fun initSqlReset(sqlExec: SqlExecutor, moduleCode: String, app: R_App)
    protected open fun postInit() { }

    fun createSourceDir(code: String): C_SourceDir {
        val files = files(code)
        return C_SourceDir.mapDirOf(files)
    }

    private fun initSqlInserts(sqlExec: SqlExecutor) {
        if (!inserts.isEmpty()) {
            val insertSql = inserts.joinToString("\n") { it }
            sqlExec.execute(insertSql)
        }
        lastInserts = inserts
    }

    abstract fun chkEx(code: String, expected: String)
    abstract fun chkFull(code: String, expected: String)

    fun chk(expr: String, expected: String) = chkEx("= $expr;", expected)

    fun chkData(expected: List<String>) {
        expectedData.clear()
        chkDataNew(expected)
    }

    fun chkData(vararg expected: String) {
        chkData(expected.toList())
    }

    fun chkDataNew(expected: List<String>) {
        expectedData.addAll(expected)
        val actual = dumpDatabaseEntities()
        assertEquals(expectedData, actual)
    }

    fun chkDataNew(vararg expected: String) {
        chkDataNew(expected.toList())
    }

    fun chkDataRaw(vararg expected: String) {
        chkDataRaw(expected.toList())
    }

    fun chkDataRaw(expected: List<String>) {
        val actual = dumpDatabaseTables()
        assertEquals(expected, actual)
    }

    fun chkDataSql(sql: String, vararg expected: String) {
        init()
        val actual = tstCtx.sqlMgr().access { sqlExec ->
            SqlTestUtils.dumpSql(sqlExec, sql)
        }
        assertEquals(expected.toList(), actual)
    }

    private fun dumpDatabaseEntities(): List<String> {
        init()
        val sqlMapping = createChainSqlMapping()
        val res = tstCtx.innerSqlMgr().access { sqlExec ->
            SqlTestUtils.dumpDatabaseEntity(sqlExec, sqlMapping, appProto!!)
        }
        return res
    }

    fun dumpDatabaseTables(): List<String> {
        init()
        val map = SqlTestUtils.dumpDatabaseTables(tstCtx.sqlMgr())
        return map.keys.sorted()
                .filter { !it.matches(Regex("c\\d+\\.(rowid_gen|sys\\.attributes|sys\\.classes|blocks|transactions)")) }
                .flatMap {
                    map.getValue(it).map { row -> "$it($row)" }
                }
    }

    fun resetRowid() {
        init()
        val sqlMapping = createChainSqlMapping()
        tstCtx.sqlMgr().transaction { sqlExec ->
            SqlTestUtils.resetRowid(sqlExec, sqlMapping)
        }
    }

    protected fun defsCode(): String = defs.joinToString("\n")

    protected fun moduleCode(extraCode: String): String {
        val defsCode = defsCode()
        val modCode = (if (defsCode.isEmpty()) "" else defsCode + "\n") + extraCode
        return modCode
    }

    fun chkCompile(code: String, expected: String) {
        val actual = compileModule(code)
        checkResult(expected, actual)
    }

    fun chkOut(vararg expected: String) = outPrinter0.chk(*expected)
    fun chkLog(vararg expected: String) = logPrinter0.chk(*expected)

    fun chkExOut(code: String, expected: String, vararg expectedOut: String) {
        chkEx(code, expected)
        chkOut(*expectedOut)
    }

    fun chkVerRt(code: String, version: String, expOld: String, expNew: String) {
        chkVer0(code, version, expOld, expNew, ::chkFull)
    }

    protected fun chkVer0(
        code: String,
        version: String,
        expOld: String,
        expNew: String,
        tester: (String, String) -> Unit,
    ) {
        val sinceVer = R_LangVersion.of(version)
        val prevVer = RellTestUtils.getPrevVersion(sinceVer)

        compatibilityVer = null
        tester(code, expNew)
        compatibilityVer = sinceVer
        tester(code, expNew)

        val fullExpOld = if (!expOld.startsWith("VER:")) expOld else {
            "ct_err:version:${expOld.substring(4)}:$sinceVer:$prevVer"
        }

        compatibilityVer = prevVer
        tester(code, fullExpOld)
    }

    fun compileModule(code: String): String {
        val moduleCode = moduleCode(code)
        return processApp(moduleCode) { "OK" }
    }

    fun compileAppEx(code: String): R_App {
        val moduleCode = moduleCode(code)
        var res: R_App? = null
        val s = processApp(moduleCode) {
            res = it
            "OK"
        }
        assertEquals("OK", s)
        return res!!
    }

    fun processApp(code: String, processor: (R_App) -> String): String {
        messages.clear()
        val sourceDir = createSourceDir(code)
        val mainMods = mainModules()
        val testMods = testModules()
        return RellTestUtils.processApp(
            sourceDir,
            errMsgPos,
            compilerOptions(),
            messages,
            mainMods,
            testModules = testMods,
            extraLibMod = extraMod,
        ) {
            processor(it.rApp)
        }
    }

    protected fun createChainSqlMapping(): Rt_ChainSqlMapping {
        return Rt_ChainSqlMapping(chainId)
    }

    companion object {
        fun checkResult(expected: String, actual: String) {
            val expected2 = if (!expected.startsWith("ct_err:")) expected else {
                expected.replace(Regex("\n *"), "")
            }
            assertEquals(expected2, actual)
        }
    }
}
