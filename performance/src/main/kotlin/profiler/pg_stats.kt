/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.profiler

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import kotlin.io.path.*

/**
 * JDBC snapshots of pg_stat_user_tables / pg_stat_user_indexes diffed before/after the
 * workload, so the report only shows tables and indexes that actually moved. Sizes are absolute.
 */
internal object PgStats {
    private const val JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/postchain"
    private const val USER = "postchain"
    private const val PASSWORD = "postchain"

    private val INT_COLS = setOf(
        "seq_scan", "seq_tup_read", "idx_scan", "idx_tup_fetch",
        "n_tup_ins", "n_tup_upd", "n_tup_del", "idx_tup_read",
    )

    private val mapper = jacksonObjectMapper()

    private data class Snapshot(val name: String, val sql: String, val numericColumns: Set<String>)

    private val SNAPSHOTS = listOf(
        Snapshot(
            "pg-table-stats",
            """
            SELECT schemaname, relname, seq_scan, seq_tup_read, idx_scan,
                   idx_tup_fetch, n_tup_ins, n_tup_upd, n_tup_del, n_live_tup
              FROM pg_stat_user_tables
              ORDER BY seq_tup_read + coalesce(idx_tup_fetch,0) DESC
            """.trimIndent(),
            setOf("seq_scan", "seq_tup_read", "idx_scan", "idx_tup_fetch",
                "n_tup_ins", "n_tup_upd", "n_tup_del", "n_live_tup"),
        ),
        Snapshot(
            "pg-index-stats",
            """
            SELECT schemaname, indexrelname, idx_scan, idx_tup_read,
                   idx_tup_fetch, pg_relation_size(indexrelid) as index_size_bytes
              FROM pg_stat_user_indexes
              ORDER BY idx_scan DESC
            """.trimIndent(),
            setOf("idx_scan", "idx_tup_read", "idx_tup_fetch", "index_size_bytes"),
        ),
        Snapshot(
            "pg-sizes",
            """
            SELECT schemaname, relname,
                   pg_total_relation_size(relid) as total_bytes,
                   pg_table_size(relid) as table_bytes,
                   pg_indexes_size(relid) as indexes_bytes
              FROM pg_stat_user_tables
              ORDER BY pg_total_relation_size(relid) DESC
            """.trimIndent(),
            setOf("total_bytes", "table_bytes", "indexes_bytes"),
        ),
    )

    fun isReady(): Boolean = try {
        connect(timeoutSec = 2).use { true }
    } catch (_: Exception) {
        false
    }

    fun collect(runDir: Path, suffix: String) {
        try {
            connect().use { conn ->
                for (snap in SNAPSHOTS) {
                    val rows = readQuery(conn, snap)
                    mapper.writeValue(runDir.resolve("${snap.name}$suffix.json").outputStream(), rows)
                }
            }
        } catch (_: Exception) {
            for (snap in SNAPSHOTS) {
                val out = runDir.resolve("${snap.name}$suffix.json")
                if (!out.exists())
                    out.writeText("[]")
            }
        }
    }

    /**
     * Diff before/after pairs into `<name>.json`, dropping inactive rows. `pg-sizes` is
     * absolute and filtered to schemas with activity, so leftover test schemas in a shared
     * dev DB don't dominate.
     */
    fun diff(runDir: Path) {
        val activeSchemas = HashSet<String>()
        for (snap in SNAPSHOTS) {
            val name = snap.name
            val before = runDir.resolve("$name-before.json")
            val after = runDir.resolve("$name-after.json")
            val out = runDir.resolve("$name.json")

            val beforeJson = readArrayOrEmpty(before)
            val afterJson = readArrayOrEmpty(after)

            if (name == "pg-sizes") {
                val filtered = afterJson
                    .filter { it.path("schemaname").asText("") in activeSchemas }
                    .sortedByDescending { it.path("total_bytes").asLong(0) }
                    .take(30)
                writeArray(out, filtered)
                before.deleteIfExists()
                after.deleteIfExists()
                continue
            }

            val keyCol = if ("index" in name) "indexrelname" else "relname"
            val beforeMap = beforeJson.associateBy { row ->
                row.path("schemaname").asText("") to row.path(keyCol).asText("")
            }

            val result = ArrayList<ObjectNode>()
            for (row in afterJson) {
                val rowObj = row.deepCopy<ObjectNode>()
                val key = row.path("schemaname").asText("") to row.path(keyCol).asText("")
                val prev = beforeMap[key]
                var hasDelta = false
                for (col in INT_COLS) {
                    if (rowObj.has(col) && !rowObj.get(col).isNull) {
                        val newVal = rowObj.get(col).asLong(0)
                        val oldVal = prev?.path(col)?.asLong(0) ?: 0
                        val delta = newVal - oldVal
                        rowObj.put(col, delta)
                        if (delta > 0) hasDelta = true
                    }
                }
                if (hasDelta) {
                    result += rowObj
                    activeSchemas += rowObj.path("schemaname").asText("")
                }
            }
            result.sortByDescending { row -> INT_COLS.sumOf { col -> row.path(col).asLong(0) } }
            writeArray(out, result.take(30))

            before.deleteIfExists()
            after.deleteIfExists()
        }
    }

    private fun connect(timeoutSec: Int = 10): Connection {
        val props = Properties().apply {
            setProperty("user", USER)
            setProperty("password", PASSWORD)
            setProperty("loginTimeout", timeoutSec.toString())
            setProperty("connectTimeout", timeoutSec.toString())
            setProperty("socketTimeout", timeoutSec.toString())
        }
        return DriverManager.getConnection(JDBC_URL, props)
    }

    private fun readQuery(conn: Connection, snap: Snapshot): ArrayNode {
        val nf = JsonNodeFactory.instance
        val out = nf.arrayNode()
        conn.createStatement().use { stmt ->
            stmt.executeQuery(snap.sql).use { rs ->
                val md = rs.metaData
                while (rs.next()) {
                    val obj = nf.objectNode()
                    for (i in 1..md.columnCount) {
                        val key = md.getColumnLabel(i)
                        if (key in snap.numericColumns) {
                            val v = rs.getLong(i)
                            if (rs.wasNull()) obj.putNull(key) else obj.put(key, v)
                        } else {
                            val s = rs.getString(i)
                            if (s == null) obj.putNull(key) else obj.put(key, s)
                        }
                    }
                    out.add(obj)
                }
            }
        }
        return out
    }

    private fun readArrayOrEmpty(path: Path): List<JsonNode> = try {
        if (path.exists()) {
            (mapper.readTree(path.inputStream()) as? ArrayNode)?.toList() ?: emptyList()
        } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeArray(path: Path, rows: List<JsonNode>) {
        val nf = JsonNodeFactory.instance
        val arr = nf.arrayNode()
        rows.forEach { arr.add(it) }
        mapper.writeValue(path.outputStream(), arr)
    }
}
