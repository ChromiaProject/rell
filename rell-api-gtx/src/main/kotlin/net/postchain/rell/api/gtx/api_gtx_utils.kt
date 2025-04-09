/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatabaseAccessFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.sql.*
import net.postchain.rell.gtx.PostchainBaseUtils
import org.apache.http.client.utils.URLEncodedUtils
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

object RellApiGtxUtils {
    fun extractDatabaseSchema(url: String): String? {
        val uri = URI(url)
        check(uri.scheme == "jdbc") { "Invalid scheme: '${uri.scheme}'" }

        val uri2 = URI(uri.schemeSpecificPart)
        val query = uri2.query
        val pairs = URLEncodedUtils.parse(query, Charsets.UTF_8)

        for (pair in pairs) {
            if (pair.name == "currentSchema") {
                return pair.value
            }
        }

        return null
    }

    fun prepareSchema(con: Connection, schema: String) {
        con.createStatement().use { stmt ->
            stmt.execute("""CREATE SCHEMA IF NOT EXISTS "$schema";""")
        }
    }

    fun createDatabaseAccess(): DatabaseAccess {
        return DatabaseAccessFactory.createDatabaseAccessWithDefaultDriver()
    }

    fun <T> runWithSqlManager(
        dbUrl: String?,
        sqlLog: Boolean,
        sqlErrorLog: Boolean,
        sqlInterceptor: SqlInterceptor? = null,
        code: (SqlManager) -> T,
    ): T {
        return if (dbUrl != null) {
            val schema = extractDatabaseSchema(dbUrl)
            val jdbcProperties = Properties()
            jdbcProperties.setProperty("binaryTransfer", "false")
            DriverManager.getConnection(dbUrl, jdbcProperties).use { con ->
                runWithSqlConnection(con, schema, sqlLog, sqlErrorLog, sqlInterceptor, code)
            }
        } else {
            code(NoConnSqlManager)
        }
    }

    private fun <T> runWithSqlConnection(
        con: Connection,
        schema: String?,
        logSql: Boolean,
        logSqlErrors: Boolean,
        sqlInterceptor: SqlInterceptor?,
        code: (SqlManager) -> T,
    ): T {
        con.autoCommit = true
        createDatabaseAccess().checkCollation(con, suppressError = false)

        if (schema != null) {
            prepareSchema(con, schema)
        }

        val sqlInterceptor2 = Rt_SqlManagerUtils.wrapSqlInterceptor(sqlInterceptor, logSqlErrors)
        var sqlCon = SqlManagerConnection.create(con, logSql)
        sqlCon = InterceptingSqlManagerConnection.wrap(sqlCon, sqlInterceptor2)
        val sqlMgr = ConnectionSqlManager(sqlCon)
        return code(sqlMgr)
    }

    fun genBlockchainConfigTemplateNoRell(pubKey: ByteArray, compileConfig: RellApiCompile.Config): Gtv {
        return gtv(
            "blockstrategy" to gtv("name" to gtv("net.postchain.base.BaseBlockBuildingStrategy")),
            "configurationfactory" to gtv("net.postchain.gtx.GTXBlockchainConfigurationFactory"),
            "signers" to gtv(listOf(gtv(pubKey))),
            "gtx" to gtv(
                "modules" to gtv(
                        buildList {
                            addAll(compileConfig.additionalGtxModules.map { gtv(it) })
                            add(gtv("net.postchain.rell.module.RellPostchainModuleFactory"))
                            add(gtv("net.postchain.gtx.StandardOpsGTXModule"))
                        }
                ),
            ),
            "features" to gtv(
                "merkle_hash_version" to gtv(2),
            ),
        )
    }
}
