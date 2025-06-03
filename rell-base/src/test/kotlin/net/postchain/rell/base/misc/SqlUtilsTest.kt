/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import net.postchain.rell.base.testutils.SqlTestUtils
import org.junit.Test
import kotlin.test.assertEquals

class SqlUtilsTest {
    @Test fun testSchemaDoesNotExist() {
        val schema = "sql_test_schema"

        SqlTestUtils.createSqlConnection().use { con ->
            val s = con.createStatement()
            s.execute("DROP SCHEMA IF EXISTS $schema CASCADE;")
        }

        SqlTestUtils.createSqlConnection().use { con ->
            con.schema = schema
            assertEquals(null, con.schema)

            val s = con.createStatement()
            s.execute("CREATE SCHEMA IF NOT EXISTS $schema;")
            assertEquals(schema, con.schema)

            s.execute("CREATE TABLE foo(id INT NOT NULL);") // Must not fail.
        }
    }

    @Test fun testSchemaDoesNotExist2() {
        val schema = "sql_test_schema"

        SqlTestUtils.createSqlConnection().use { con ->
            val s = con.createStatement()
            s.execute("DROP SCHEMA IF EXISTS $schema CASCADE;")
        }

        SqlTestUtils.createSqlConnection(schema).use { con ->
            assertEquals(null, con.schema)

            val s = con.createStatement()
            s.execute("CREATE SCHEMA IF NOT EXISTS $schema;")
            assertEquals(schema, con.schema)

            s.execute("CREATE TABLE foo(id INT NOT NULL);") // Must not fail.
        }
    }
}
