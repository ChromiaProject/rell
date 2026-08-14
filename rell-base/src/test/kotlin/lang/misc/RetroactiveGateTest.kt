/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.RellVersions
import kotlin.test.Test

/**
 * Tests for the retroactive feature gates: version checks added to constructs which had shipped without one, so
 * code could use them while declaring an older language version.
 *
 * The gate must not reject code produced by a compiler which predates it. A node recompiles every historical
 * blockchain configuration when it replays a chain, so failing such a configuration now would break a running
 * blockchain. `gtx.rell.compilerVersion` records which compiler produced a configuration and is what tells the two
 * cases apart. See [RellVersions.RETROACTIVE_GATES_VERSION] and `C_FeatureRestrictions.makeRetroactive`.
 */
class RetroactiveGateTest: BaseRellTest(false) {
    /** Uses a collection operator (0.14.16) while declaring 0.14.15. */
    private val badCode = "function f() = [1, 2] + [3];"

    @Test fun testGateFiresWhenCompiledNow() {
        // No compiler version: the code is being compiled right now, so it is checked.
        tst.compatibilityVer("0.14.15")
        chkCompile(badCode, "ct_err:version:feature:binop_collection:0.14.16:0.14.15")
    }

    @Test fun testGateFiresForCurrentCompiler() {
        tst.compatibilityVer("0.14.15")
        tst.compilerVer(RellVersions.VERSION_STR)
        chkCompile(badCode, "ct_err:version:feature:binop_collection:0.14.16:0.14.15")
    }

    @Test fun testGateSuppressedForOlderCompiler() {
        // Compiled before the gate existed: it must still compile, or an existing chain would break.
        tst.compatibilityVer("0.14.15")
        tst.compilerVer("0.16.6")
        chkCompile(badCode, "OK")
    }

    @Test fun testGateSuppressedForOldestCompiler() {
        tst.compatibilityVer("0.14.15")
        tst.compilerVer(RellVersions.MIN_COMPILER_VERSION.str())
        chkCompile(badCode, "OK")
    }

    @Test fun testSuppressionDoesNotAffectOldGates() {
        // A gate which shipped together with its feature applies whatever compiler produced the code.
        tst.compatibilityVer("0.13.9")
        tst.compilerVer("0.16.6")
        chkCompile("function f(x: integer?) = x == 123;", "ct_err:version:feature:binop_nullable_eq_value:0.13.10:0.13.9")
    }

    @Test fun testSuppressionDoesNotAffectLibrary() {
        // Library members are version-controlled independently of the retroactive gates.
        tst.compatibilityVer("0.14.15")
        tst.compilerVer("0.16.6")
        chkCompile("function f() = [1].add_all_copy([2]);",
            "ct_err:version:lib:FUNCTION:[rell:list.add_all_copy]:0.14.16:0.14.15")
    }

    @Test fun testNoCompatibilityVersionMeansNoCheck() {
        // Configurations older than 0.13.11 carry no version at all and are exempt from every gate.
        tst.compatibilityVer(null)
        chkCompile(badCode, "OK")
    }

    @Test fun testCurrentVersionAlwaysCompiles() {
        tst.compatibilityVer(RellVersions.VERSION_STR)
        chkCompile(badCode, "OK")
    }
}
