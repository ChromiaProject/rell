/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.common.exception.UserMistake
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.gtx.RellApiRunTests
import net.postchain.rell.base.testutils.RellReplTester
import net.postchain.rell.base.utils.immListOf
import org.junit.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class DatabaseCollationIT {
    @Test
    fun testCollationTestPass() {
        PostgreSQLContainer(
            DockerImageName.parse("postgres:16.6-alpine3.21@sha256:aba1fab94626cf8b0f4549055214239a37e0a690f03f142b7bca05b9ed36c6db")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            start()
        }.use { postgres ->
            val databaseUrlWithUserAndPassword =
                buildDatabaseUrl(postgres.jdbcUrl, postgres.username, postgres.password)
            chkRunTests(databaseUrlWithUserAndPassword)
            chkRunShell(databaseUrlWithUserAndPassword)
        }
    }

    @Test
    fun testCollationTestFail() {
        PostgreSQLContainer(
            DockerImageName.parse("postgres:16.6:c7afedc5c15994625b5be4cb4736c030271b55be0360b78a99c90ec2fbe658b6")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            start()
        }
            .use { postgres ->
                val databaseUrlWithUserAndPassword =
                    buildDatabaseUrl(postgres.jdbcUrl, postgres.username, postgres.password)

                assertTrue {
                    (assertFailsWith<UserMistake> {
                        chkRunTests(databaseUrlWithUserAndPassword)
                    }.message ?: "").contains("Database collation check failed")
                }

                assertTrue {
                    (assertFailsWith<UserMistake> {
                        chkRunShell(databaseUrlWithUserAndPassword)
                    }.message ?: "").contains("Database collation check failed")
                }
            }
    }

    private fun buildDatabaseUrl(databaseUrl: String, user: String, password: String) =
        databaseUrl + (if (databaseUrl.contains('?')) '&' else '?') + "user=$user&password=$password"

    private fun chkRunTests(databaseUrl: String) {
        val compileConfig = RellApiCompile.Config.Builder()
            .build()
        val testConfig = RellApiRunTests.Config.Builder()
            .compileConfig(compileConfig)
            .databaseUrl(databaseUrl)
            .build()
        val res =
            RellApiRunTests.runTests(testConfig, File("../work/testproj/src"), listOf(), listOf("tests.data_test"))
        assertTrue { res.getResults().all { it.res.isOk } }
    }

    private fun chkRunShell(databaseUrl: String) {
        val compileConfig = RellApiCompile.Config.Builder()
            .build()
        val shellConfig = RellApiRunShell.Config.Builder()
            .compileConfig(compileConfig)
            .databaseUrl(databaseUrl)
            .inputChannelFactory(RellReplTester.TestReplInputChannelFactory(immListOf()))
            .build()
        RellApiRunShell.runShell(shellConfig, File("../work/testproj/src"), "repl.company")
    }
}
