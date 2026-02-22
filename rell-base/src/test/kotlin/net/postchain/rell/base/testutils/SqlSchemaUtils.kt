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

    /** Advisory lock key used to coordinate VACUUM FULL across parallel test processes. */
    private const val VACUUM_LOCK_KEY = 0x52454C4CL // "RELL"

    /**
     * Prepares the environment for temporary test schemas.
     *
     * Tries to acquire a PostgreSQL advisory lock (non-blocking):
     * - If acquired: drops **all** leftover test schemas (from any PID) and runs `VACUUM FULL`
     *   to reclaim disk space — important for tight storage limits (e.g., in-memory Docker volumes).
     * - If not acquired: another process is already vacuuming, so only drops schemas for the current PID.
     *
     * The operation is performed only once per test run, guarded by a JVM-level lock.
     */
    @Throws(SQLException::class)
    fun prepareTestSchemaEnvironment(con: Connection) = synchronized(testSchemaPreparationLock) {
        if (!testSchemasEnvironmentReady) {
            val gotLock = con.createStatement().use { st ->
                st.executeQuery("SELECT pg_try_advisory_lock($VACUUM_LOCK_KEY)").use { rs ->
                    rs.next() && rs.getBoolean(1)
                }
            }

            con.createStatement().use { st ->
                if (gotLock) {
                    // Got the lock, drop all test schemas (any PID) and vacuum.
                    val allSchemas = mutableListOf<String>()
                    st.executeQuery(
                        "SELECT nspname FROM pg_namespace WHERE nspname LIKE '${TESTSCHEMA_PREFIX}_%'"
                    ).use { rs ->
                        while (rs.next()) {
                            allSchemas += rs.getString(1)
                        }
                    }
                    for (nsp in allSchemas) {
                        st.execute("DROP SCHEMA IF EXISTS \"$nsp\" CASCADE;")
                    }
                    st.execute("VACUUM FULL;")
                    st.execute("SELECT pg_advisory_unlock($VACUUM_LOCK_KEY)")
                } else {
                    // Another process is vacuuming — only drop schemas for our PID.
                    val schemas = getAllTestSchemaNames(con)
                    for (nsp in schemas) {
                        st.execute("DROP SCHEMA IF EXISTS \"$nsp\" CASCADE;")
                    }
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
