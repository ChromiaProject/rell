/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.gtx.testutils.BaseGtxTest
import org.junit.Test

class GtxMerkleHashTest: BaseGtxTest() {
    @Test fun testHashDefault() {
        chkV1()
    }

    @Test fun testHashV1() {
        tst.extraModuleConfig["features"] = "{'merkle_hash_version':1}"
        chkV1()
    }

    @Test fun testHashV2() {
        tst.extraModuleConfig["features"] = "{'merkle_hash_version':2}"
        chkV2()
    }

    @Test fun testHashVersionControl() {
        tst.extraModuleConfig["features"] = "{'merkle_hash_version':2}"
        chkV2()

        tst.compatibilityVer("0.14.5")
        chkV2()

        tst.compatibilityVer("0.14.3")
        chkV1()
    }

    private fun chkV1() {
        chk("list<text>().hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
        chk("map<text,text>().hash()", "'300B4292A3591228725E6E2E20BE3AB63A6A99CC695E925C6C20A90C570A5E71'")

        chk("[123].hash()", "'341FDF9993EA5847FB8AD1BA7F92CD3C0FB2932E2AB1DEE5FCBCBD7D995D0AA3'")
        chk("[[123]].hash()", "'341FDF9993EA5847FB8AD1BA7F92CD3C0FB2932E2AB1DEE5FCBCBD7D995D0AA3'")
        chk("[[[123]]].hash()", "'341FDF9993EA5847FB8AD1BA7F92CD3C0FB2932E2AB1DEE5FCBCBD7D995D0AA3'")

        chk("gtv.from_json('[]').hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
        chk("gtv.from_json('[[]]').hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
        chk("gtv.from_json('[{}]').hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
    }

    private fun chkV2() {
        chk("list<text>().hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
        chk("map<text,text>().hash()", "'300B4292A3591228725E6E2E20BE3AB63A6A99CC695E925C6C20A90C570A5E71'")

        chk("[123].hash()", "'341FDF9993EA5847FB8AD1BA7F92CD3C0FB2932E2AB1DEE5FCBCBD7D995D0AA3'")
        chk("[[123]].hash()", "'036F58E462A180CD60F30030CDCE670CCE4A403284757A4DB00F53661134CE65'")
        chk("[[[123]]].hash()", "'7FB9337E6BCF36EB4F7D69FF34609C9022468A735FB7A8D2FA8EB08795C0B40B'")

        chk("gtv.from_json('[]').hash()", "'46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5'")
        chk("gtv.from_json('[[]]').hash()", "'B27D13915E478770D8CBAAF72D2C92F67A17250B2C40C9A7B36C3E996AE5FAD7'")
        chk("gtv.from_json('[{}]').hash()", "'5AC6C92DFFE0A0DEFA0581023E84C3D344A42D4FF90FC2A3AF0D40DBF8D7A622'")
    }
}
