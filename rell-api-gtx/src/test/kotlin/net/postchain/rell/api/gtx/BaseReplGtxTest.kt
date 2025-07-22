package net.postchain.rell.api.gtx

import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.BaseRellTest

internal abstract class BaseReplGtxTest: BaseRellTest(useSql = true) {
    final override fun getProjExt() = PostchainRellTestProjExt

    /** To be invoked in each test before running any transactions. */
    protected fun initChain() {
        val chainId = 0L
        val chainRid = "DeadBeef".repeat(8)
        tst.replModule = ""
        tst.chainId = chainId
        tst.blockchainRid = chainRid
        tstCtx.blockchain(chainId, chainRid)
    }
}