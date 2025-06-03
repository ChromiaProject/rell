/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import org.junit.Test
import kotlin.test.assertEquals

class RellApiGtxUtilsTest {
    @Test fun testExtractDatabaseSchema() {
        chkExtractDatabaseSchema("jdbc:postgresql://localhost/postchain", null)
        chkExtractDatabaseSchema("jdbc:postgresql://localhost/postchain?currentSchema=foobar", "foobar")
        chkExtractDatabaseSchema("jdbc:postgresql://localhost/postchain?currentSchema=foobar&x=y", "foobar")
        chkExtractDatabaseSchema("jdbc:postgresql://localhost/postchain?x=y&currentSchema=foobar", "foobar")

        chkExtractDatabaseSchema("jdbc:postgresql://localhost", null)
        chkExtractDatabaseSchema("jdbc:postgresql://localhost?currentSchema=foobar", "foobar")
        chkExtractDatabaseSchema("jdbc:postgresql://localhost?currentSchema=foobar&x=y", "foobar")
        chkExtractDatabaseSchema("jdbc:postgresql://localhost?x=y&currentSchema=foobar", "foobar")
    }

    private fun chkExtractDatabaseSchema(url: String, expected: String?) {
        val actual = RellApiGtxUtils.extractDatabaseSchema(url)
        assertEquals(expected, actual)
    }
}
