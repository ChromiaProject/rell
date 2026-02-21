/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import com.google.common.collect.HashMultimap
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

object SqlTestUtils {
    /**
     * Creates an SQL connection in the default DB's schema. Not suitable for parallel tests that may create
     * tables with colliding names.
     *
     * May be useful for tests that verify DB metadata or other non-writing operations.
     */
    @Throws(SQLException::class)
    fun createSimpleConnection(path: String = "/rell-db-config.properties"): Connection {
        val prop = readDbProperties(path)
        val con = DriverManager.getConnection(getDbUrlWithSchema(prop), createJdbcProperties(prop))
        return con
    }

    /**
     * Creates an SQL connection along with a temporary schema. The returned connection has `currentSchema` parameter
     * set to this schema. After the returned connection is closed, the schema is dropped.
     *
     * Also, guarantees that all previous test schemas are dropped, and the database is vacuumed.
     */
    @Throws(SQLException::class)
    fun createIsolatedSchemaConnection(): Connection {
        val schema = SqlSchemaUtils.generateSchemaName()
        val prop = readDbProperties().copy(schema = schema)
        val con = DriverManager.getConnection(getDbUrlWithSchema(prop), createJdbcProperties(prop))
        var failed: Connection? = con
        try {
            SqlSchemaUtils.prepareTestSchemaEnvironment(con)
            SqlSchemaUtils.createSchema(con, schema)
            failed = null
        } finally {
            failed?.close() // if preparation failed, close the connection
        }

        return object : Connection by con {
            override fun close() {
                try {
                    SqlSchemaUtils.dropSchema(this, schema)
                } finally {
                    con.close()
                }
            }
        }
    }

    fun createSqlExecutor(con: Connection) = SqlManagerConnection.create(con).createExecutor()

    /**
     * Creates a temporary database URL with an associated schema for testing.
     *
     * The returned pair consists of a closeable handle and the JDBC URL string.
     * The handle **must be closed** after use to ensure the temporary schema is properly dropped and resources are
     * released.
     */
    fun createTempDbUrl(): Pair<AutoCloseable, String> {
        val schema = SqlSchemaUtils.generateSchemaName()
        val prop = readDbProperties().copy(schema = schema)
        val url = StringBuilder(getDbUrlWithSchema(prop))
        appendUrlParam(url, "user", prop.user)
        appendUrlParam(url, "password", prop.password)
        appendUrlParam(url, "binaryTransfer", "false")

        DriverManager.getConnection(url.toString()).use { con ->
            SqlSchemaUtils.prepareTestSchemaEnvironment(con)
            SqlSchemaUtils.createSchema(con, schema)
        }

        val handle = AutoCloseable {
            dropTempDbUrl(prop)
        }

        return handle to url.toString()
    }

    private fun dropTempDbUrl(prop: DbConnProps) {
        val schema = prop.schema ?: return

        DriverManager.getConnection(getDbUrlWithSchema(prop), createJdbcProperties(prop)).use { con ->
            SqlSchemaUtils.dropSchema(con, schema)
        }
    }

    private fun getDbUrlWithSchema(props: DbConnProps): String {
        val url = StringBuilder(props.url)
        if (props.schema != null) {
            appendUrlParam(url, "currentSchema", props.schema)
        }
        return url.toString()
    }

    private fun appendUrlParam(url: StringBuilder, name: String, value: String) {
        url.append(if ("?" in url) "&" else "?")
        url.append(name)
        url.append('=')
        url.append(value)
    }

    private fun readDbProperties(path: String = "/rell-db-config.properties"): DbConnProps {
        val props = Properties()

        SqlTestUtils.javaClass.getResourceAsStream(path).use { ins ->
            props.load(ins)
        }

        val url = System.getenv("POSTCHAIN_DB_URL") ?: props.getProperty("database.url")
        val user = System.getenv("POSTGRES_USER") ?: props.getProperty("database.username")
        val password = System.getenv("POSTGRES_PASSWORD") ?: props.getProperty("database.password")
        val schema = props.getProperty("database.schema")?.let { "${it}_0" }
        return DbConnProps(url, user, password, schema)
    }

    data class DbConnProps(
        val url: String,
        val user: String,
        val password: String,
        val schema: String? = null,
    )

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
        return """INSERT INTO "$table"("${SqlConstants.ROWID_COLUMN}",$quotedColumns) VALUES ($values);"""
    }

    fun dumpDatabaseEntity(sqlExec: SqlExecutor, chainMapping: Rt_ChainSqlMapping, app: R_App): ImmList<String> {
        val list = mutableListOf<String>()

        for (entity in app.sqlDefs.entities) {
            if (entity.sqlMapping.autoCreateTable()) {
                dumpEntity(sqlExec, chainMapping, entity, list)
            }
        }

        for (obj in app.sqlDefs.objects) {
            dumpEntity(sqlExec, chainMapping, obj.rEntity, list)
        }

        return list.toImmList()
    }

    private fun dumpEntity(
        sqlExec: SqlExecutor,
        chainMapping: Rt_ChainSqlMapping,
        entity: R_EntityDefinition,
        list: MutableList<String>,
    ) {
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

    private fun getTableDumpSql(table: String, columns: List<String>, sortColumn: String?): String = buildString {
        append("SELECT")
        columns.joinTo(this, ", ") { "\"$it\"" }

        append(" FROM \"${table}\"")
        if (sortColumn != null) {
            append(" ORDER BY \"$sortColumn\"")
        }
    }

    private fun dumpSqlRecord(row: ResultSetRow): String {
        val values = mutableListOf<String>()

        for (idx in 1..row.metaData.columnCount) {
            val value = row.getObject(idx)
            val str = when (value) {
                is String -> value
                is ByteArray -> "0x" + CommonUtils.bytesToHex(row.getBytes(idx)!!)
                is PGobject -> value.value
                is Int, is Long -> "" + value
                is Boolean -> "" + value
                is BigDecimal -> "" + value
                null -> "NULL"
                else -> error(value.javaClass.canonicalName)
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

    fun dumpTablesStructure(con: Connection, all: Boolean = false): ImmMap<String, ImmMap<String, String>> {
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

        val res = mutableMapOf<String, ImmMap<String, String>>()
        for (table in map.keySet().sorted()) {
            res[table] = map[table].sortedBy { it.first }.toImmMap()
        }

        return res.toImmMap()
    }

    private fun createJdbcProperties(prop: DbConnProps): Properties {
        return Properties().apply {
            setProperty("user", prop.user)
            setProperty("password", prop.password)
            setProperty("binaryTransfer", "false")
        }
    }
}
