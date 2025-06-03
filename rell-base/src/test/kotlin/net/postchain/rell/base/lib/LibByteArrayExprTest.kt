/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseExprTest
import org.junit.Test

abstract class LibByteArrayExprTest: BaseExprTest() {
    class LibByteArrayExprIpTest: LibByteArrayExprTest()
    class LibByteArrayExprDbTest: LibByteArrayExprTest()

    @Test fun testEmpty() {
        chkExpr("#0.empty()", "boolean[true]", vBytes(""))
        chkExpr("#0.empty()", "boolean[false]", vBytes("00"))
        chkExpr("#0.empty()", "boolean[false]", vBytes("123456789A"))
    }

    @Test fun testSize() {
        chkExpr("#0.size()", "int[0]", vBytes(""))
        chkExpr("#0.size()", "int[1]", vBytes("00"))
        chkExpr("#0.size()", "int[5]", vBytes("123456789A"))
    }

    @Test fun testSub() {
        chkExpr("#0.sub(#1)", "byte_array[0123abcd]", vBytes("0123ABCD"), vInt(0))
        chkExpr("#0.sub(#1)", "byte_array[abcd]", vBytes("0123ABCD"), vInt(2))
        chkExpr("#0.sub(#1)", "byte_array[cd]", vBytes("0123ABCD"), vInt(3))
        chkExpr("#0.sub(#1)", "byte_array[]", vBytes("0123ABCD"), vInt(4))
        chkExpr("#0.sub(#1)", rtErr("fn:byte_array.sub:range:4:5:4"), vBytes("0123ABCD"), vInt(5))
        chkExpr("#0.sub(#1)", rtErr("fn:byte_array.sub:range:4:-1:4"), vBytes("0123ABCD"), vInt(-1))
        chkExpr("#0.sub(#1, #2)", "byte_array[23ab]", vBytes("0123ABCD"), vInt(1), vInt(3))
        chkExpr("#0.sub(#1, #2)", "byte_array[0123abcd]", vBytes("0123ABCD"), vInt(0), vInt(4))
        chkExpr("#0.sub(#1, #2)", rtErr("fn:byte_array.sub:range:4:1:0"), vBytes("0123ABCD"), vInt(1), vInt(0))
        chkExpr("#0.sub(#1, #2)", rtErr("fn:byte_array.sub:range:4:1:5"), vBytes("0123ABCD"), vInt(1), vInt(5))
    }

    @Test fun testToHex() {
        chkExpr("#0.to_hex()", "text[]", vBytes(""))
        chkExpr("#0.to_hex()", "text[deadbeef]", vBytes("DEADBEEF"))
    }

    @Test fun testToBase64() {
        chkExpr("#0.to_base64()", "text[]", vBytes(""))
        chkExpr("#0.to_base64()", "text[AA==]", vBytes("00"))
        chkExpr("#0.to_base64()", "text[AAA=]", vBytes("0000"))
        chkExpr("#0.to_base64()", "text[AAAA]", vBytes("000000"))
        chkExpr("#0.to_base64()", "text[AAAAAA==]", vBytes("00000000"))
        chkExpr("#0.to_base64()", "text[////]", vBytes("FFFFFF"))
        chkExpr("#0.to_base64()", "text[EjRWeA==]", vBytes("12345678"))
        chkExpr("#0.to_base64()", "text[3q2+7w==]", vBytes("deadbeef"))
        chkExpr("#0.to_base64()", "text[yv66vt6tvu8=]", vBytes("cafebabedeadbeef"))
    }
}
