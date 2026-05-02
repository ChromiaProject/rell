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
 * may both observe a missing function, both attempt to create it, and one loses the race. PostgreSQL
 * surfaces this as either SQLSTATE `42723` (duplicate_function — the post-parse name check) or `23505`
 * (unique_violation — the catalog index `pg_proc_proname_args_nsp_index` rejecting the duplicate row);
 * which one fires depends on which backend reaches the `pg_proc` insert first. Each `CREATE` is
 * therefore wrapped in a savepoint that tolerates both states and rolls back to the savepoint, leaving
 * the rest of the batch and the outer transaction intact. Any other SQL error propagates.
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
     * Runs [sql] inside a savepoint. If the statement fails because a racing process created the same
     * function between our discovery query and this CREATE — surfaced as SQLSTATE `42723`
     * (duplicate_function) or `23505` on `pg_proc_proname_args_nsp_index` (the catalog unique index) —
     * rolls back to the savepoint and continues. Any other SQL error propagates.
     */
    private fun createFunctionTolerantOfDuplicate(connection: Connection, name: String, sql: String) {
        val savepoint = connection.setSavepoint("rell_create_fn_${name}")
        try {
            connection.createStatement().use { it.execute(sql) }
            connection.releaseSavepoint(savepoint)
        } catch (e: SQLException) {
            if (isDuplicateFunctionRace(e)) {
                connection.rollback(savepoint)
                connection.releaseSavepoint(savepoint)
            } else {
                connection.rollback(savepoint)
                throw e
            }
        }
    }

    private fun isDuplicateFunctionRace(e: SQLException): Boolean = when (e.sqlState) {
        DUPLICATE_FUNCTION_SQLSTATE -> true
        UNIQUE_VIOLATION_SQLSTATE -> e.message?.contains(PG_PROC_UNIQUE_INDEX) == true
        else -> false
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
        // PostgreSQL error codes. See https://www.postgresql.org/docs/current/errcodes-appendix.html.
        // 42723: function with that signature already exists (post-parse name check).
        // 23505: unique_violation; raised when two backends race the pg_proc catalog insert before
        // either reaches the duplicate-function check, identified by the constraint name below.
        private const val DUPLICATE_FUNCTION_SQLSTATE = "42723"
        private const val UNIQUE_VIOLATION_SQLSTATE = "23505"
        private const val PG_PROC_UNIQUE_INDEX = "pg_proc_proname_args_nsp_index"
    }
}
