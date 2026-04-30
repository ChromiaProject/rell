/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import net.postchain.core.GlobalStorageInitializer
import net.postchain.rell.base.runtime.RawSqlStatement
import net.postchain.rell.base.sql.RawSqlAccess
import net.postchain.rell.base.sql.SqlGen
import java.sql.Connection

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
 */
class RellGlobalStorageInitializer : GlobalStorageInitializer {

    @OptIn(RawSqlAccess::class)
    override fun initializeGlobalStorage(connection: Connection) {
        val existingFunctions = getExistingFunctions(connection)

        for ((name, stmt) in SqlGen.RELL_SYS_FUNCTIONS) {
            if (name !in existingFunctions) {
                // RELL_SYS_FUNCTIONS values are all CREATE FUNCTION RawSqlStatements; we run them via plain
                // JDBC here because GlobalStorageInitializer hands us a Connection, not Rell's SqlExecutor.
                val sql = (stmt as RawSqlStatement).sql
                connection.createStatement().use { it.execute(sql) }
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
}
