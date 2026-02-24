/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger

@Suppress("SqlSourceToSinkFlow")
internal object SqlSchemaUtils {
    private val schemaCounter = AtomicInteger(0)
    private const val TESTSCHEMA_PREFIX = "rell"
    private val pid = ProcessHandle.current().pid()

    private fun extractTestClassName(): String? {
        val stackTrace = Thread.currentThread().stackTrace
        val rellFrames = stackTrace.filter { it.className.startsWith("net.postchain.rell.") }
        val testFrame = rellFrames.findLast { it.className.substringAfterLast('.').endsWith("Test") }
            ?: rellFrames.lastOrNull()
        return testFrame?.className?.substringAfterLast('.')
    }

    fun generateSchemaName(): String {
        val testClassName = extractTestClassName()
        val id = schemaCounter.incrementAndGet()
        val name = buildString {
            append(TESTSCHEMA_PREFIX)
            append('_')
            append(pid)
            if (testClassName != null) {
                append('_')
                append(testClassName.lowercase())
            }
            append('_')
            append(id)
        }
        check(name.length <= 63) {
            "Test schema name '$name' is too long, must be 63 characters or less"
        }
        return name
    }

    @Throws(SQLException::class)
    fun getAllTestSchemaNames(con: Connection): List<String> {
        val prefix = "${TESTSCHEMA_PREFIX}_${pid}_"
        val schemas = mutableListOf<String>()
        con.createStatement().use { st ->
            st.executeQuery("SELECT nspname FROM pg_namespace WHERE nspname LIKE '${prefix}%'").use { rs ->
                while (rs.next()) {
                    schemas += rs.getString(1)
                }
            }
        }
        return schemas
    }

    private var testSchemasEnvironmentReady = false
    private val testSchemaPreparationLock = Any()

    /**
     * Prepares the environment for temporary test schemas.
     *
     * Drops leftover test schemas for the current PID only — safe for parallel test execution
     * across multiple subproject JVMs (each has its own PID and schema namespace).
     *
     * The operation is performed only once per test run, guarded by a JVM-level lock.
     */
    @Throws(SQLException::class)
    fun prepareTestSchemaEnvironment(con: Connection) = synchronized(testSchemaPreparationLock) {
        if (!testSchemasEnvironmentReady) {
            val schemas = getAllTestSchemaNames(con)
            con.createStatement().use { st ->
                for (nsp in schemas) {
                    st.execute("DROP SCHEMA IF EXISTS \"$nsp\" CASCADE;")
                }
            }

            CheckDbCleannessListener.enabled.set(true)
            testSchemasEnvironmentReady = true
        }
    }

    @Throws(SQLException::class)
    fun createSchema(con: Connection, name: String) {
        con.prepareStatement("CREATE SCHEMA IF NOT EXISTS $name;").use { s ->
            s.execute()
        }
    }

    @Throws(SQLException::class)
    fun dropSchema(con: Connection, name: String) {
        con.prepareStatement("DROP SCHEMA IF EXISTS $name CASCADE;").use { s ->
            s.execute()
        }
    }
}
