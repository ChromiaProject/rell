/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.testutils

import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseEContext
import net.postchain.base.BaseTxEContext
import net.postchain.base.TxEventSink
import net.postchain.base.data.PostgreSQLDatabaseAccess
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.toList
import net.postchain.gtv.parse.GtvParser
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.data.ExtOpData
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.testutils.*
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.gtx.PostchainBaseUtils
import net.postchain.rell.module.RellPostchainModuleEnvironment
import net.postchain.rell.module.RellPostchainModuleFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RellGtxTester(
    tstCtx: RellTestContext,
    entityDefs: List<String> = listOf(),
    inserts: List<String> = listOf(),
    gtv: Boolean = false,
    chainId: Long = 995511,
): RellBaseTester(tstCtx, entityDefs, inserts, gtv) {
    var wrapRtErrors = true
    val extraModuleConfig = mutableMapOf<String, String>()
    var modules: List<String>? = listOf("")
    var configTemplate: String = getDefaultConfigTemplate()
    var strictGtvConversion: Boolean = false

    init {
        super.chainId = chainId
    }

    override fun initSqlReset(sqlExec: SqlExecutor, moduleCode: String, app: R_App) {
        val bRid = BlockchainRid(blockchainRid.hexStringToByteArray())

        val gtxModule = createGtxModule(moduleCode)

        val dbAccess = PostchainBaseUtils.createDatabaseAccess()
        sqlExec.connection { con ->
            val ctx = BaseEContext(con, chainId, dbAccess)
            dbAccess.initializeBlockchain(ctx, bRid)
            GTXSchemaManager.initializeDB(ctx)
            gtxModule.initializeDB(ctx)
        }
    }

    override fun chkEx(code: String, expected: String) {
        chkQueryEx("query q() $code", "", expected)
    }

    override fun chkFull(code: String, expected: String) {
        chkQueryEx(code, "", expected)
    }

    fun chkQueryEx(code: String, args: String, expected: String) {
        val gtvArgs = strToArgs(args)
        val actual = callQueryEx(code, gtvArgs)
        assertEquals(expected, actual)
    }

    fun chkQueryEx(code: String, args: Map<String, Gtv>, expected: String) {
        val actual = callQueryEx(code, args)
        assertEquals(expected, actual)
    }

    private fun callQueryEx(code: String, args: Map<String, Gtv>): String {
        val moduleCode = moduleCode(code)
        return callQuery0(moduleCode, "q", args)
    }

    fun callQuery(name: String, args: Map<String, Gtv>): String {
        val moduleCode = defsCode()
        return callQuery0(moduleCode, name, args)
    }

    fun chkCallQuery(name: String, args: Map<String, Gtv>, expected: String) {
        val actual = callQuery(name, args)
        assertEquals(expected, actual)
    }

    fun chkCallQuery(name: String, args: String, expected: String) {
        val gtvArgs = strToArgs(args)
        val actual = callQuery(name, gtvArgs)
        assertEquals(expected, actual)
    }

    private fun strToArgs(args: String): Map<String, Gtv> {
        val str = "{$args}"
        val gtv = GtvTestUtils.decodeGtvStr(str)
        return gtv.asDict()
    }

    private fun callQuery0(moduleCode: String, name: String, args: Map<String, Gtv>): String {
        return eval.eval {
            eval.wrapRt { init() }

            val queryGtv = GtvFactory.gtv(args)

            val module = eval.wrapAll { createGtxModule(moduleCode) }

            withEContext(false) { ctx ->
                val res = eval.wrapRt {
                    module.query(ctx, name, queryGtv)
                }
                GtvTestUtils.gtvToStr(res)
            }
        }
    }

    fun chkCallOperation(name: String, args: List<String>, expected: String = "OK") {
        val gtvArgs = args.map { GtvTestUtils.decodeGtvStr(normalizeQuotes(it)) }
        val moduleCode = defsCode()
        val actual = callOperation0(moduleCode, name, gtvArgs)
        assertEquals(expected, actual)
    }

    fun normalizeQuotes(str: String): String {
        return str.replace('\'', '"')
    }

    fun chkOpEx(code: String, args: List<Gtv>, expected: String = "OK") {
        val moduleCode = moduleCode(code)
        val actual = callOperation0(moduleCode, "o", args)
        assertEquals(expected, actual)
    }

    private fun hexToRid(s: String): BlockchainRid = BlockchainRid(CommonUtils.hexToBytes(s))

    private fun callOperation0(moduleCode: String, name: String, args: List<Gtv>): String {
        return eval.eval {
            eval.wrapRt { init() }

            val module = eval.wrapCt { createGtxModule(moduleCode) }

            val dummyEventSink = object : TxEventSink {
                override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
                    TODO("Not yet implemented")
                }
            }

            val res = withEContext(true) { ctx ->
                val blkCtx = BaseBlockEContext(ctx, 0, 0, System.currentTimeMillis(), mapOf(), dummyEventSink)
                val bcRid = hexToRid(blockchainRid)
                val opData = ExtOpData(name, 0, args.toTypedArray(), bcRid, arrayOf(), arrayOf())
                val transactor = module.makeTransactor(opData)

                eval.wrapRt {
                    transactor.checkCorrectness()
                }

                val tx = TransactorTransaction(transactor)
                val txCtx = BaseTxEContext(blkCtx, 0, tx)

                transactor.apply(txCtx)
            }

            check(res)
            "OK"
        }
    }

    private fun <T> withEContext(tx: Boolean, code: (EContext) -> T): T {
        init()
        val res = tstCtx.sqlMgr().execute(tx) { sqlExec ->
            sqlExec.connection { con ->
                val dbAccess = PostchainBaseUtils.createDatabaseAccess()
                val ctx = BaseEContext(con, chainId, dbAccess)
                code(ctx)
            }
        }
        return res
    }

    fun chkUserMistake(code: String, msg: String) {
        val moduleCode = moduleCode(code)

        try {
            createGtxModule(moduleCode)
            fail("Did not throw UserMistake")
        } catch (e: UserMistake) {
            assertTrue(e.message!!.startsWith(msg), "" + e)
        }
    }

    private fun createGtxModule(moduleCode: String): GTXModule {
        val env = RellPostchainModuleEnvironment(
                outPrinter = outPrinter,
                logPrinter = logPrinter,
                wrapCtErrors = false,
                wrapRtErrors = wrapRtErrors,
                forceTypeCheck = true,
                hiddenLib = hiddenLib
        )
        val factory = RellPostchainModuleFactory(env)

        val moduleCfg = getModuleConfig(moduleCode)
        val bcRid = hexToRid(blockchainRid)
        val module = factory.makeModule(moduleCfg, bcRid)
        return module
    }

    private fun getModuleConfig(moduleCode: String): Gtv {
        val sourceCodes = files(moduleCode)
        val modArgs = getModuleArgs()
        val parts = ModuleConfigParts(modules, sourceCodes, modArgs, extraModuleConfig)
        val templateGtv = GtvTestUtils.strToGtv(configTemplate)
        return getModuleConfig(parts, templateGtv)
    }

    private fun getModuleConfig(parts: ModuleConfigParts, template: Gtv): Gtv {
        val v = makeModuleConfigNode(parts, template, true)
        return v ?: GtvFactory.gtv(mapOf())
    }

    private fun makeModuleConfigNode(parts: ModuleConfigParts, tpl: Gtv, root: Boolean): Gtv? {
        return when (tpl.type) {
            GtvType.NULL, GtvType.BYTEARRAY, GtvType.INTEGER, GtvType.BIGINTEGER -> tpl
            GtvType.ARRAY -> GtvFactory.gtv(tpl.asArray().mapNotNull { makeModuleConfigNode(parts, it, false) })
            GtvType.DICT -> {
                val map = tpl.asDict().entries
                        .map { it.key to makeModuleConfigNode(parts, it.value, false) }
                        .filter { it.second != null }
                        .map { it.first to it.second!! }
                        .toMap().toMutableMap()
                if (root) {
                    map.putAll(parts.extraModuleConfig.mapValues { GtvTestUtils.decodeGtvStr(it.value) })
                }
                GtvFactory.gtv(map)
            }
            GtvType.STRING -> {
                val s = tpl.asString()
                when (s) {
                    "{COMPILER_VERSION}" -> GtvFactory.gtv(RellTestUtils.RELL_VER)
                    "{VERSION}" -> GtvFactory.gtv(compatibilityVer?.str() ?: RellTestUtils.RELL_VER)
                    "{MODULES}" -> {
                        if (parts.modules == null) null else GtvFactory.gtv(parts.modules.map { GtvFactory.gtv(it) })
                    }
                    "{SOURCES}" -> GtvFactory.gtv(parts.sourceCodes.mapValues { (_, v) -> GtvString(v) })
                    "{MODULE_ARGS}" -> GtvTestUtils.moduleArgsToGtv(parts.moduleArgs)
                    "{STRICT_GTV_CONVERSION}" ->  GtvFactory.gtv(this.strictGtvConversion)
                    else -> tpl
                }
            }
            else -> throw IllegalArgumentException("type ${tpl.type} not supported by rell")
        }
    }

    private fun getDefaultConfigTemplate(): String {
        return """
            {'gtx':{'rell':{
                'compilerVersion':'{COMPILER_VERSION}',
                'version':'{VERSION}',
                'modules':'{MODULES}',
                'sources':'{SOURCES}',
                'moduleArgs':'{MODULE_ARGS}',
                'strictGtvConversion':'{STRICT_GTV_CONVERSION}'
            }}}
        """.unwrap()
    }

    private class TransactorTransaction(val transactor: Transactor): Transaction {
        override fun apply(ctx: TxEContext): Boolean = transactor.apply(ctx)
        override fun checkCorrectness() { transactor.checkCorrectness() }
        override fun isSpecial(): Boolean = transactor.isSpecial()

        // TODO To properly support following methods, test transactions execution shall be done in a different way.
        override fun getHash(): ByteArray = TODO()
        override fun getRID(): ByteArray = TODO()
        override fun getRawData(): ByteArray = TODO()
    }

    private class ModuleConfigParts(
        val modules: List<String>?,
        val sourceCodes: Map<String, String>,
        val moduleArgs: Map<String, String>,
        val extraModuleConfig: Map<String, String>,
    )
}
