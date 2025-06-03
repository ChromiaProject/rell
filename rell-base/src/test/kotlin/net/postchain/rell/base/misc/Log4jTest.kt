/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import mu.KLogging
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertEquals

class Log4jTest {
    // Log4j2 vulnerability CVE-2021-44228
    // https://www.lunasec.io/docs/blog/log4j-zero-day/
    @Test(timeout = 10000) fun testVulnerabilityJndi() {
        val l = mutableListOf<String>()
        ServerSocket(0).use { ss ->
            val port = ss.localPort
            // In older versions of Log4j2 (before 2.15.0) next line hangs trying to connect to the URL.
            logger.info("\${jndi:ldap://127.0.0.1:$port/foo}")
            l.add("OK")
        }
        assertEquals(listOf("OK"), l)
    }

    // Log4j2 vulnerability similar to CVE-2021-44228
    @Test fun testVulnerabilityRecursion() {
        // Next line does not fail in vulnerable versions of Log4j2 (before 2.15.0), but prints an exception
        // with message "Infinite loop in property interpolation...".
        logger.info("\${\${::-\${::-\$\${::-j}}}}")
    }

    private companion object : KLogging()
}
