/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import java.math.BigInteger

/** Shared test data fixtures used across modules. */
object TestFixtures {
    val BLOCK_INSERTS_333 = RellTestContext.BlockBuilder(333)
        .block(111, 222, "DEADBEEF", 1500000000000)
        .tx(444, 111, "FADE", "EDAF", "1234")
        .list()

    val BLOCK_INSERTS_CURRENT = RellTestContext.BlockBuilder(333)
        .block(101, 10, "DEADBEEF", 1500000000000)
        .block(102, 20, null, null)
        .tx(201, 101, "CEED", "FEED", "4321")
        .tx(202, 102, "FADE", "EDAF", "1234")
        .list()

    /** Decimal overflow limit used in GTV tests. */
    val DECIMAL_LIMIT: BigInteger = BigInteger.TEN.pow(131072)
}

/** Helper for GTV from_json tests. */
fun chkFromGtv(tst: RellCodeTester, gtv: String, expr: String, expected: String) {
    val gtv2 = gtv.replace('\'', '"')
    val code = """{ val g = gtv.from_json('$gtv2'); return $expr; }"""
    tst.chkEx(code, expected)
}
