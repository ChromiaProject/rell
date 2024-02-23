/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.common.exception.UserMistake
import net.postchain.rell.gtx.testutils.BaseGtxTest
import org.apache.commons.lang3.StringUtils
import org.junit.Test
import kotlin.test.assertEquals

class GtxConfigTest: BaseGtxTest() {
    @Test fun testLegacyOK() {
        chkLegacy("v0.10", "OK")
    }

    @Test fun testLegacyWrongVersionFormat() {
        val msg = "ERR:Invalid source code key: <KEY>_%s; use '<KEY>' and 'version' instead"
        chkLegacy("v0.10.0", msg.format("v0.10.0"))
        chkLegacy("v0.10.4", msg.format("v0.10.4"))
        chkLegacy("v0.010", msg.format("v0.010"))
        chkLegacy("v0.100", msg.format("v0.100"))
        chkLegacy("v00.10", msg.format("v00.10"))
        chkLegacy("v0", msg.format("v0"))
        chkLegacy("v1", msg.format("v1"))
        chkLegacy("v7XYZ", msg.format("v7XYZ"))
        chkLegacy("vXYZ", "ERR:Source code not specified in the configuration")
        chkLegacy("0.10", "ERR:Source code not specified in the configuration")
    }

    @Test fun testLegacyVersionOutOfRange() {
        val msg = "ERR:Invalid source code key: <KEY>_%s; use '<KEY>' and 'version' instead"
        chkLegacy("v0.8", msg.format("v0.8"))
        chkLegacy("v0.9", msg.format("v0.9"))
        chkLegacy("v0.11", msg.format("v0.11"))
        chkLegacy("v0.12", msg.format("v0.12"))
        chkLegacy("v1.0", msg.format("v1.0"))
    }

    @Test fun testLegacyMultipleSources() {
        chkConfig("'sources_v0.10':SOURCES,'files_v0.10':FILES",
                "ERR:Multiple source code nodes specified in the configuration: files_v0.10, sources_v0.10")
    }

    @Test fun testLegacyAndNewSources() {
        val msg = "Multiple source code nodes specified in the configuration"
        chkConfig("'sources_v0.10':SOURCES,'sources':SOURCES,'version':'0.10.4'", "ERR:$msg: sources, sources_v0.10")
        chkConfig("'files_v0.10':FILES,'files':FILES,'version':'0.10.4'", "ERR:$msg: files, files_v0.10")
        chkConfig("'sources_v0.10':SOURCES,'files':FILES,'version':'0.10.4'", "ERR:$msg: files, sources_v0.10")
        chkConfig("'files_v0.10':FILES,'sources':SOURCES,'version':'0.10.4'", "ERR:$msg: files_v0.10, sources")
    }

    @Test fun testLegacyAndNewVersion() {
        val msg = "ERR:Keys '%s' and 'version' cannot be specified together"
        chkConfig("'sources_v0.10':SOURCES,'version':'0.10.0'", msg.format("sources_v0.10"))
        chkConfig("'files_v0.10':FILES,'version':'0.10.0'", msg.format("files_v0.10"))
        chkConfig("'sources_v0.10':SOURCES,'version':'0.10.4'", msg.format("sources_v0.10"))
        chkConfig("'files_v0.10':FILES,'version':'0.10.4'", msg.format("files_v0.10"))
    }

    @Test fun testSourcesWithoutVersion() {
        val msg = "ERR:Configuration key '%s' is specified, but 'version' is missing"
        chkConfig("'sources':SOURCES", msg.format("sources"))
        chkConfig("'files':FILES", msg.format("files"))
    }

    @Test fun testSourcesMultiple() {
        chkConfig("'sources':SOURCES,'files':FILES,'version':'0.10.4'",
                "ERR:Multiple source code nodes specified in the configuration: files, sources")
    }

    @Test fun testSourcesOK() {
        chkVersion("0.10.0", "OK")
        chkVersion("0.10.1", "OK")
        chkVersion("0.10.2", "OK")
        chkVersion("0.10.3", "OK")
        chkVersion("0.10.4", "OK")
        chkVersion("0.10.5", "OK")
        chkVersion("0.10.6", "OK")
        chkVersion("0.10.7", "OK")
        chkVersion("0.10.8", "OK")
        chkVersion("0.10.9", "OK")
        chkVersion("0.10.10", "OK")
        chkVersion("0.10.11", "OK")
        chkVersion("0.11.0", "OK")
        chkVersion("0.12.0", "OK")
        chkVersion("0.13.0", "OK")
        chkVersion("0.13.1", "OK")
        chkVersion("0.13.2", "OK")
        chkVersion("0.13.3", "OK")
        chkVersion("0.13.4", "OK")
        chkVersion("0.13.5", "OK")
        chkVersion("0.13.6", "OK")
        chkVersion("0.13.7", "OK")
        chkVersion("0.13.8", "OK")
        chkVersion("0.13.9", "OK")
    }

    @Test fun testSourcesWithVersionOutOfRange() {
        val msg = "ERR:Unsupported language version"
        chkVersion("0.9.0", "$msg: 0.9.0")
        chkVersion("0.9.1", "$msg: 0.9.1")
        chkVersion("0.9.999", "$msg: 0.9.999")
        chkVersion("0.10.12", "$msg: 0.10.12")
        chkVersion("0.11.1", "$msg: 0.11.1")
        chkVersion("0.12.1", "$msg: 0.12.1")
        chkVersion("0.13.10", "$msg: 0.13.10")
        chkVersion("0.14.1", "$msg: 0.14.1")
        chkVersion("0.15.0", "$msg: 0.15.0")
        chkVersion("1.0.0", "$msg: 1.0.0")
    }

    @Test fun testSourcesWithBadVersion() {
        val msg = "ERR:Invalid language version"
        chkVersion("0", "$msg: 0")
        chkVersion("1", "$msg: 1")
        chkVersion("hello", "$msg: hello")
        chkVersion("0.10", "$msg: 0.10")
        chkVersion("0,10,0", "$msg: 0,10,0")
        chkVersion("0.10.0.0", "$msg: 0.10.0.0")
        chkVersion("00.10.0", "$msg: 00.10.0")
        chkVersion("0.10.00", "$msg: 0.10.00")
        chkVersion("0.010.0", "$msg: 0.010.0")
        chkVersion("v0.10.0", "$msg: v0.10.0")
    }

    private fun chkLegacy(version: String, expected: String) {
        chkConfig("sources", "'sources_$version':SOURCES", expected)
        chkConfig("files", "'files_$version':FILES", expected)
    }

    private fun chkVersion(version: String, expected: String) {
        chkConfig("sources", "'sources':SOURCES,'version':'$version'", expected)
        chkConfig("files", "'files':FILES,'version':'$version'", expected)
    }

    private fun chkConfig(sources: String, expected: String) {
        chkConfig("?", sources, expected)
    }

    private fun chkConfig(key: String, sources: String, expected: String) {
        val actual = runConfig(sources)
        val expected2 = expected.replace("<KEY>", key)
        assertEquals(expected2, actual)
    }

    private fun runConfig(sources: String): String {
        val s = sources
                .replace("SOURCES", "{'a.rell':'query q() = 42;'}")
                .replace("FILES", "{'a.rell':'classpath:/net/postchain/rell/base/42.rell'}")
        tst.configTemplate = "{'gtx':{'rell':{'modules':'{MODULES}','moduleArgs':'{MODULE_ARGS}',$s}}}"

        try {
            val res = tst.callQuery("q", mapOf())
            return if (res == "42") "OK" else "OK:$res"
        } catch (e: UserMistake) {
            //e.printStackTrace()
            val msg = StringUtils.removeStart(e.message ?: "", "Module initialization failed: ")
            return "ERR:$msg"
        }
    }
}
