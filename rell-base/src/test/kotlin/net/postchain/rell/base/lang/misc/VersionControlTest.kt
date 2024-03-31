/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

/** Version control tests for cases with no other suitable test class. */
class VersionControlTest: BaseRellTest(false) {
    @Test fun testListSetMapNames() {
        chkReservedName("list")
        chkReservedName("set")
        chkReservedName("map")
    }

    private fun chkReservedName(name: String) {
        chkVerCt("function f($name: integer) {}", "0.11.0", "VER:name:$name")
        chkVerCt("struct data { $name: integer; }", "0.11.0", "VER:name:$name")
        chkVerCt("entity data { $name: integer; }", "0.11.0", "VER:name:$name")
        chkVerCt("namespace ns { struct $name {} }", "0.11.0", "VER:name:$name")
        chkVerCt("namespace ns { function $name() {} }", "0.11.0", "VER:name:$name")
        chkVerCt("namespace ns { operation $name() {} }", "0.11.0", "VER:name:$name")
    }

    @Test fun testGtvBigInteger() {
        val gtv = "gtv.from_bytes(x'a60302017b')"
        chk(gtv, "gtv[123L]")

        chkVerRtExpr("integer.from_gtv($gtv)", "0.11.0", "gtv_err:type:[integer]:INTEGER:BIGINTEGER", "int[123]")
        chkVerRtExpr("decimal.from_gtv($gtv)", "0.11.0", "gtv_err:type:[decimal]:STRING:BIGINTEGER", "dec[123]")
    }
}
