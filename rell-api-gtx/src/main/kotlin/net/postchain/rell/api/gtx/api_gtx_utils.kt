/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.sql.ConnectionSqlManager
import net.postchain.rell.base.sql.NoConnSqlManager
import net.postchain.rell.base.sql.SqlManager
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

    fun <T> runWithSqlManager(
        dbUrl: String?,
        dbProperties: String?,
        sqlLog: Boolean,
        sqlErrorLog: Boolean,
        code: (SqlManager) -> T,
    ): T {
        return if (dbUrl != null) {
            val schema = extractDatabaseSchema(dbUrl)
            val jdbcProperties = Properties()
            jdbcProperties.setProperty("binaryTransfer", "false")
            DriverManager.getConnection(dbUrl, jdbcProperties).use { con ->
                con.autoCommit = true
                PostchainBaseUtils.createDatabaseAccess().checkCollation(con, suppressError = false)
                val sqlMgr = ConnectionSqlManager(con, sqlLog)
                runWithSqlManager(schema, sqlMgr, sqlErrorLog, code)
            }
        } else if (dbProperties != null) {
            val appCfg = AppConfig.fromPropertiesFile(dbProperties)
            val storage = StorageBuilder.buildStorage(appCfg)
            val sqlMgr = PostchainStorageSqlManager(storage, sqlLog)
            runWithSqlManager(appCfg.databaseSchema, sqlMgr, sqlErrorLog, code)
        } else {
            code(NoConnSqlManager)
        }
    }

    private fun <T> runWithSqlManager(
        schema: String?,
        sqlMgr: SqlManager,
        logSqlErrors: Boolean,
        code: (SqlManager) -> T,
    ): T {
        val sqlMgr2 = Rt_SqlManagerUtils.makeSqlManager(sqlMgr, logSqlErrors)
        if (schema != null) {
            sqlMgr2.transaction { sqlExec ->
                sqlExec.connection { con ->
                    prepareSchema(con, schema)
                }
            }
        }
        return code(sqlMgr2)
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
