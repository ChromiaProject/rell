/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import net.postchain.core.GlobalStorageInitializer
import net.postchain.rell.base.runtime.RawSqlStatement
import net.postchain.rell.base.sql.RawSqlAccess
import net.postchain.rell.base.sql.SqlGen
import java.sql.Connection
import java.sql.SQLException

/**
 * Creates Rell system SQL functions (from [SqlGen.RELL_SYS_FUNCTIONS]) at node startup, in a committed
 * transaction, before any blockchain starts. This avoids the deadlock that previously occurred when
 * multiple blockchains concurrently tried to create these global functions inside their own uncommitted
 * write transactions during [net.postchain.rell.base.sql.SqlInit] initialization.
 *
 * Discovered via [java.util.ServiceLoader] by [net.postchain.PostchainNode].
 *
 * The per-chain DB initialization in [RellPostchainModule.initializeDB] still handles chain-specific
 * structures (tables, meta, per-chain rowid function), but no longer creates the global system functions.
 *
 * **Concurrent startup safety.** A node-level `SELECT routine_name` followed by `CREATE FUNCTION` is a
 * TOCTOU window: two JVMs (multi-replica deployments, parallel testcontainers, k8s rolling restarts)
 * may both observe a missing function, both attempt to create it, and one will fail with PostgreSQL
 * SQLSTATE `42723` (duplicate_function). Each `CREATE` is therefore wrapped in a savepoint that
 * tolerates `42723` and rolls back to the savepoint, leaving the rest of the batch and the outer
 * transaction intact. Any other SQL error propagates.
 */
class RellGlobalStorageInitializer : GlobalStorageInitializer {

    @OptIn(RawSqlAccess::class)
    override fun initializeGlobalStorage(connection: Connection) {
        // Savepoint-based duplicate-tolerance below requires a real transaction; setSavepoint() throws
        // under autoCommit=true. Postchain hands us a non-autocommit Connection in production, but pin
        // it explicitly so a future framework change fails loudly here rather than at the first race.
        check(!connection.autoCommit) {
            "RellGlobalStorageInitializer requires a transactional Connection (autoCommit=false)"
        }
        val existingFunctions = getExistingFunctions(connection)

        for ((name, stmt) in SqlGen.RELL_SYS_FUNCTIONS) {
            if (name in existingFunctions) continue
            // RELL_SYS_FUNCTIONS values are all CREATE FUNCTION RawSqlStatements; we run them via plain
            // JDBC here because GlobalStorageInitializer hands us a Connection, not Rell's SqlExecutor.
            val sql = (stmt as RawSqlStatement).sql
            createFunctionTolerantOfDuplicate(connection, name, sql)
        }
    }

    /**
     * Runs [sql] inside a savepoint. If the statement fails with SQLSTATE `42723` (the function was
     * created by a racing process between our discovery query and this CREATE), rolls back to the
     * savepoint and continues. Any other SQL error propagates.
     */
    private fun createFunctionTolerantOfDuplicate(connection: Connection, name: String, sql: String) {
        val savepoint = connection.setSavepoint("rell_create_fn_${name}")
        try {
            connection.createStatement().use { it.execute(sql) }
            connection.releaseSavepoint(savepoint)
        } catch (e: SQLException) {
            if (e.sqlState == DUPLICATE_FUNCTION_SQLSTATE) {
                connection.rollback(savepoint)
                connection.releaseSavepoint(savepoint)
            } else {
                connection.rollback(savepoint)
                throw e
            }
        }
    }

    private fun getExistingFunctions(connection: Connection): Set<String> {
        // Equivalent of SqlUtils.getExistingFunctions() but operating on a plain Connection
        // rather than Rell's SqlExecutor, since GlobalStorageInitializer runs before any chain context exists.
        val sql = "SELECT routine_name FROM information_schema.routines " +
                "WHERE routine_catalog = CURRENT_DATABASE() AND routine_schema = CURRENT_SCHEMA()"
        return buildSet {
            connection.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) add(rs.getString(1))
                }
            }
        }
    }

    companion object {
        // PostgreSQL: function with that signature already exists. See https://www.postgresql.org/docs/current/errcodes-appendix.html.
        private const val DUPLICATE_FUNCTION_SQLSTATE = "42723"
    }
}
