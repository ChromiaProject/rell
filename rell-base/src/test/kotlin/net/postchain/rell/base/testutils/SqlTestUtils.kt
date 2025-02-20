/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import com.google.common.collect.HashMultimap
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.CommonUtils
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

object SqlTestUtils {
    fun createSqlConnection(schema: String? = null): Connection {
        val prop = readDbProperties()
        val url = getDbUrl0(prop, schema)

        val jdbcProperties = Properties()
        jdbcProperties.setProperty("user", prop.user)
        jdbcProperties.setProperty("password", prop.password)
        jdbcProperties.setProperty("binaryTransfer", "false")

        val con = DriverManager.getConnection(url, jdbcProperties)
        var resource: AutoCloseable? = con
        try {
            freeDiskSpace(con)
            resource = null
        } finally {
            resource?.close()
        }

        return con
    }

    fun createSqlExecutor(con: Connection): SqlExecutor {
        return SqlManagerConnection.create(con).createExecutor()
    }

    fun getDbUrl(schema: String? = null): String {
        val prop = readDbProperties()
        var url = getDbUrl0(prop, schema)
        url = appendUrlParam(url, "user", prop.user)
        url = appendUrlParam(url, "password", prop.password)
        url = appendUrlParam(url, "binaryTransfer", "false")
        return url
    }

    private fun getDbUrl0(props: DbConnProps, schema: String?): String {
        var url = props.url
        if (schema != null) {
            url = appendUrlParam(url, "currentSchema", schema)
        }
        return url
    }

    private fun appendUrlParam(url: String, name: String, value: String): String {
        val sep = if ("?" in url) "&" else "?"
        return "$url$sep$name=$value"
    }

    fun readDbProperties(): DbConnProps {
        val config = loadProperties("/rell-db-config.properties")
        val url = System.getenv("POSTCHAIN_DB_URL") ?: config.getProperty("database.url")
        val user = System.getenv("POSTGRES_USER") ?: config.getProperty("database.username")
        val password = System.getenv("POSTGRES_PASSWORD") ?: config.getProperty("database.password")
        return DbConnProps(url, user, password)
    }

    private fun loadProperties(fileName: String): Properties {
        val props = Properties()
        javaClass.getResourceAsStream(fileName).use { ins ->
            props.load(ins)
        }
        return props
    }

    data class DbConnProps(val url: String, val user: String, val password: String)

    private var freeDiskSpace = true

    // When running all tests multiple times in a row, sometimes Postgres starts failing with "no space left on device"
    // error. Executing VACUUM shall fix this.
    // https://www.postgresql.org/docs/current/sql-vacuum.html
    // https://dba.stackexchange.com/questions/37028/vacuum-returning-disk-space-to-operating-system
    private fun freeDiskSpace(con: Connection) {
        if (!freeDiskSpace) return
        freeDiskSpace = false
        con.createStatement().use { stmt ->
            stmt.execute("VACUUM FULL;")
        }
    }

    fun resetRowid(sqlExec: SqlExecutor, chainMapping: Rt_ChainSqlMapping) {
        val table = chainMapping.rowidTable
        sqlExec.execute("""UPDATE "$table" SET last_value = 0;""")
    }

    fun clearTables(sqlExec: SqlExecutor) {
        val tables = SqlUtils.getExistingTables(sqlExec)
        val sql = tables.joinToString("\n") { "TRUNCATE \"$it\" CASCADE;" }
        sqlExec.execute(sql)
    }

    fun createSysAppTables(sqlExec: SqlExecutor) {
        sqlExec.execute("""
            CREATE TABLE IF NOT EXISTS "blockchains"(
                chain_iid BIGINT PRIMARY KEY,
                blockchain_rid BYTEA NOT NULL
            );
        """)
    }

    /** Creates tables that normally shall be created by Postchain, but this project can't call it, so creating
     * own tables for tests (some columns are missing - they are not needed). */
    fun createSysBlockchainTables(sqlExec: SqlExecutor, chainId: Long) {
        val blocksTable = "c$chainId.blocks"
        val transactionsTable = "c$chainId.transactions"

        sqlExec.execute("""
            CREATE TABLE IF NOT EXISTS "$blocksTable"(
                block_iid BIGSERIAL PRIMARY KEY,
                block_height BIGINT NOT NULL,
                block_rid BYTEA,
                timestamp BIGINT,
                UNIQUE (block_rid),
                UNIQUE (block_height)
            );
        """)

        sqlExec.execute("""
            CREATE TABLE IF NOT EXISTS "$transactionsTable"(
                tx_iid BIGSERIAL PRIMARY KEY,
                tx_rid BYTEA NOT NULL,
                tx_data BYTEA NOT NULL,
                tx_hash BYTEA NOT NULL,
                tx_number BIGINT NOT NULL UNIQUE,
                block_iid bigint NOT NULL REFERENCES "$blocksTable"(block_iid),
                UNIQUE (tx_rid)
            );
        """)
    }

    fun mkins(table: String, columns: String, values: String): String {
        val quotedColumns = columns.split(",").joinToString { "\"$it\"" }
        return "INSERT INTO \"$table\"(\"${SqlConstants.ROWID_COLUMN}\",$quotedColumns) VALUES ($values);"
    }

    fun dumpDatabaseEntity(sqlExec: SqlExecutor, chainMapping: Rt_ChainSqlMapping, app: R_App): List<String> {
        val list = mutableListOf<String>()

        for (entity in app.sqlDefs.entities) {
            if (entity.sqlMapping.autoCreateTable()) {
                dumpEntity(sqlExec, chainMapping, entity, list)
            }
        }

        for (obj in app.sqlDefs.objects) {
            dumpEntity(sqlExec, chainMapping, obj.rEntity, list)
        }

        return list.toList()
    }

    private fun dumpEntity(sqlExec: SqlExecutor, chainMapping: Rt_ChainSqlMapping, entity: R_EntityDefinition, list: MutableList<String>) {
        val table = entity.sqlMapping.table(chainMapping)
        val cols = listOf(entity.sqlMapping.rowidColumn()) + entity.attributes.values.map { it.sqlMapping }
        val sql = getTableDumpSql(table, cols, entity.sqlMapping.rowidColumn())
        val rows = dumpSql(sqlExec, sql).map { "${entity.moduleLevelName}($it)" }
        list += rows
    }

    fun dumpSql(sqlExec: SqlExecutor, sql: String): List<String> {
        val list = mutableListOf<String>()
        sqlExec.executeQuery(sql, {}) { rs -> list.add(dumpSqlRecord(rs)) }
        return list
    }

    private fun getTableDumpSql(table: String, columns: List<String>, sortColumn: String?): String {
        val buf = StringBuilder()
        buf.append("SELECT")
        columns.joinTo(buf, ", ") { "\"$it\"" }

        buf.append(" FROM \"${table}\"")
        if (sortColumn != null) {
            buf.append(" ORDER BY \"$sortColumn\"")
        }

        return buf.toString()
    }

    private fun dumpSqlRecord(row: ResultSetRow): String {
        val values = mutableListOf<String>()

        for (idx in 1 .. row.metaData.columnCount) {
            val value = row.getObject(idx)
            val str = if (value is String) {
                value
            } else if (value is ByteArray) {
                "0x" + CommonUtils.bytesToHex(row.getBytes(idx)!!)
            } else if (value is PGobject) {
                value.value
            } else if (value is Int || value is Long) {
                "" + value
            } else if (value is Boolean) {
                "" + value
            } else if (value is BigDecimal) {
                "" + value
            } else if (value == null) {
                "NULL"
            } else {
                throw IllegalStateException(value.javaClass.canonicalName)
            }
            values.add("" + str)
        }

        return values.joinToString(",")
    }

    fun dumpDatabaseTables(sqlMgr: SqlManager): Map<String, List<String>> {
        val res = sqlMgr.access { sqlExec ->
            sqlExec.connection { con ->
                dumpDatabaseTables(con, sqlExec)
            }
        }
        return res
    }

    private fun dumpDatabaseTables(con: Connection, sqlExec: SqlExecutor): Map<String, List<String>> {
        val res = mutableMapOf<String, List<String>>()

        val struct = dumpTablesStructure(con)
        for ((table, attrs) in struct) {
            val columns = attrs.keys.toMutableList()
            val rowid = columns.remove(SqlConstants.ROWID_COLUMN)
            if (rowid) columns.add(0, SqlConstants.ROWID_COLUMN)
            val sql = getTableDumpSql(table, columns, if (rowid) SqlConstants.ROWID_COLUMN else null)
            val rows = dumpSql(sqlExec, sql)
            res[table] = rows
        }

        return res
    }

    fun dumpTablesStructure(con: Connection, all: Boolean = false): Map<String, Map<String, String>> {
        val map = HashMultimap.create<String, Pair<String, String>>()
        val namePattern = if (all) null else "c%.%"
        con.metaData.getColumns(null, con.schema, namePattern, null).use { rs ->
            while (rs.next()) {
                val table = rs.getString(3)
                val column = rs.getString(4)
                val type = rs.getString(6)
                if (all || table.matches(Regex("c\\d+\\..+"))) {
                    map.put(table, Pair(column, type))
                }
            }
        }

        val res = mutableMapOf<String, Map<String, String>>()
        for (table in map.keySet().sorted()) {
            res[table] = map[table].sortedBy { it.first }.toMap()
        }

        return res
    }
}
