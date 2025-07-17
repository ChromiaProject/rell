/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.rell.base.lib.LibBlockTransactionTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.gtx.testutils.BaseGtxTest
import kotlin.test.Test

class GtxExternalTest: BaseGtxTest() {
    private val depBcRid = RellTestUtils.strToRidHex("beefdead").toLowerCase()

    @Test fun testUnknownChain() {
        def("@external('foo') namespace {}")
        tst.wrapRtErrors = false
        chk("123", "rt_err:external_chain_unknown:foo")
    }

    @Test fun testUnknownChain2() {
        def("@external('foo') namespace {}")
        tst.wrapRtErrors = false
        tst.extraModuleConfig["dependencies"] = "[['foo','$depBcRid']]"
        chk("123", "rt_err:external_chain_no_rid:foo:$depBcRid")
    }

    @Test fun testUnknownChain3() {
        tstCtx.blockchain(333, "beefdead")

        run {
            val t = RellCodeTester(tstCtx)
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.init()
        }

        def("@external('foo') namespace {}")
        tst.wrapRtErrors = false
        tst.extraModuleConfig["dependencies"] = "[['foo','$depBcRid']]"
        chk("123", "123")
    }

    @Test fun testUnknownExternalEntity() {
        tstCtx.blockchain(333, "beefdead")

        run {
            val t = RellCodeTester(tstCtx)
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.init()
        }

        def("@external('foo') namespace { @log entity user {} }")
        tst.wrapRtErrors = false
        tst.extraModuleConfig["dependencies"] = "[['foo','$depBcRid']]"
        chk("123", "rt_err:external_meta_no_entity:foo:user")
    }

    @Test fun testExternalEntityOK() {
        tstCtx.blockchain(333, "beefdead")

        run {
            val t = RellCodeTester(tstCtx)
            t.def("@log entity user { name; }")
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.insert("c333.user", "name,transaction", "15,'Bob',444")
            t.init()
        }

        def("@external('foo') namespace { @log entity user { name; } }")
        tst.wrapRtErrors = false
        tst.extraModuleConfig["dependencies"] = "[['foo','$depBcRid']]"
        chk("_strict_str(user @{} ( _=user, _=.name ))", "'([foo]:user[15],text[Bob])'")
    }
}
