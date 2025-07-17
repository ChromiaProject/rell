/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class LibRellTestOtherTest: BaseRellTest() {
    init {
        tst.testLib = true
    }

    @Test fun testKeypairType() {
        chkEx("{ val x: rell.test.keypair; return _type_of(x); }", "text[rell.test.keypair]")

        chk("rell.test.keypair()", "ct_err:attr_missing:[rell.test.keypair]:pub,priv")
        chk("rell.test.keypair(x'', x'')", "ct_err:attr_implic_multi:0:pub,priv")
        chk("rell.test.keypair(pub = x'11', priv = x'22')", "rell.test.keypair[pub=byte_array[11],priv=byte_array[22]]")

        chk("""rell.test.keypair.from_gtv(gtv.from_json('["11","22"]'))""",
                "rell.test.keypair[pub=byte_array[11],priv=byte_array[22]]")
    }

    @Test fun testPredefinedKeypairs() {
        chkKeypair("bob", "034f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa", "1111111111111111111111111111111111111111111111111111111111111111")
        chkKeypair("alice", "02466d7fcae563e5cb09a0d1870bb580344804617879a14949cf22285f1bae3f27", "2222222222222222222222222222222222222222222222222222222222222222")
        chkKeypair("trudy", "023c72addb4fdf09af94f0c94d7fe92a386a7e70cf8a1d85916386bb2535c7b1b1", "3333333333333333333333333333333333333333333333333333333333333333")
        chkKeypair("charlie", "032c0b7cf95324a07d05398b240174dc0c2be444d96b159aa6c7f7b1e668680991", "4444444444444444444444444444444444444444444444444444444444444444")
        chkKeypair("dave", "029ac20335eb38768d2052be1dbbc3c8f6178407458e51e6b4ad22f1d91758895b", "5555555555555555555555555555555555555555555555555555555555555555")
        chkKeypair("eve", "035ab4689e400a4a160cf01cd44730845a54768df8547dcdf073d964f109f18c30", "6666666666666666666666666666666666666666666666666666666666666666")
        chkKeypair("frank", "037962d45b38e8bcf82fa8efa8432a01f20c9a53e24c7d3f11df197cb8e70926da", "7777777777777777777777777777777777777777777777777777777777777777")
        chkKeypair("grace", "021617d38ed8d8657da4d4761e8057bc396ea9e4b9d29776d4be096016dbd2509b", "8888888888888888888888888888888888888888888888888888888888888888")
        chkKeypair("heidi", "028985087b1818714f67e494a076ca0284c060fabc5d2ba66885b4ac60f801d3f5", "9999999999999999999999999999999999999999999999999999999999999999")
    }

    private fun chkKeypair(name: String, pubkey: String, privKey: String) {
        val pubkeyArray = "byte_array[$pubkey]"
        val privkeyArray = "byte_array[$privKey]"
        chk("rell.test.privkeys.$name", privkeyArray)
        chk("rell.test.pubkeys.$name", pubkeyArray)
        chk("rell.test.keypairs.$name.pub", pubkeyArray)
        chk("rell.test.keypairs.$name.priv", privkeyArray)
        chk("rell.test.keypairs.$name", "rell.test.keypair[pub=$pubkeyArray,priv=$privkeyArray]")
    }

    @Test fun testPredefinedKeypairsTypes() {
        val bobPriv = "byte_array[1111111111111111111111111111111111111111111111111111111111111111]"
        val bobPub = "byte_array[034f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa]"

        chk("_type_of(rell.test.keypair(priv = x'11', pub = x'22'))", "text[rell.test.keypair]")
        chk("_type_of(rell.test.keypairs.bob)", "text[rell.test.keypair]")

        chkEx("{ val x: rell.test.keypair = rell.test.keypairs.bob; return x; }",
                "rell.test.keypair[pub=$bobPub,priv=$bobPriv]")

        chkEx("{ val x: rell.test.keypair = rell.test.keypair(priv = x'11', pub = x'22'); return x; }",
                "rell.test.keypair[pub=byte_array[22],priv=byte_array[11]]")
    }

    @Test fun testNoTestLib() {
        tst.testLib = false
        chk("rell.test.block()", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.tx()", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.op()", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.keypairs.bob", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.privkeys.bob", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.pubkeys.bob", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.keypair()", "ct_err:unknown_name:[rell:rell]:test")
        chk("rell.test.assert_equals(0,0)", "ct_err:unknown_name:[rell:rell]:test")
        chk("assert_equals(0,0)", "ct_err:unknown_name:assert_equals")
    }

    @Test fun testBlockchainSignerKeypair() {
        chk("_type_of(rell.test.BLOCKCHAIN_SIGNER_KEYPAIR)", "text[rell.test.keypair]")
        chk("rell.test.BLOCKCHAIN_SIGNER_KEYPAIR.priv", "byte_array[4242424242424242424242424242424242424242424242424242424242424242]")
        chk("rell.test.BLOCKCHAIN_SIGNER_KEYPAIR.pub", "byte_array[0324653eac434488002cc06bbfb7f10fe18991e35f9fe4302dbea6d2353dc0ab1c]")
        chk("crypto.privkey_to_pubkey(rell.test.BLOCKCHAIN_SIGNER_KEYPAIR.priv, true)",
            "byte_array[0324653eac434488002cc06bbfb7f10fe18991e35f9fe4302dbea6d2353dc0ab1c]")
    }
}
