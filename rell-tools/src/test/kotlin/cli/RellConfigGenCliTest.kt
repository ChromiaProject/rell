/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RellConfigGenCliTest {
    private fun checkRellcfg(command: String) {
        val result = CliTestUtils.runCommand(command)
        assertEquals(0, result.exitCode, "Non-zero exit code.\nstderr: ${result.stderr}")
        assertEquals("", result.stderr)
        assertTrue(
            result.stdout.startsWith("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""),
            "Output does not start with XML header:\n${result.stdout.take(200)}"
        )

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(result.stdout.byteInputStream())
        assertEquals("dict", doc.documentElement.nodeName)
    }

    @Test
    fun testStair() {
        checkRellcfg("rellcfg.sh -d work/testproj/src stair")
    }

    @Test
    fun testMod() {
        checkRellcfg("rellcfg.sh -d work/testproj/src mod")
    }

    @Test
    fun testModComplexFoo() {
        checkRellcfg("rellcfg.sh -d work/testproj/src mod.complex.foo")
    }
}
