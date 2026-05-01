/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import net.postchain.rell.base.sql.SqlGen
import net.postchain.rell.base.testutils.SqlTestUtils
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins the cross-process startup race that motivated the savepoint-based duplicate-function
 * tolerance in [RellGlobalStorageInitializer]. Multiple JVMs starting against a shared schema
 * race the `SELECT routine_name` -> `CREATE FUNCTION` window, and one will see SQLSTATE 42723.
 *
 * The single-thread re-run case verifies the discovery path on already-initialized schemas.
 * The concurrent case verifies the savepoint path on a real PostgreSQL race.
 */
class RellGlobalStorageInitializerTest {
    @Test fun reRunOnInitializedSchemaIsNoOp() {
        val (handle, jdbcUrl) = SqlTestUtils.createTempDbUrl()
        handle.use {
            val initializer = RellGlobalStorageInitializer()

            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.autoCommit = false
                initializer.initializeGlobalStorage(conn)
                conn.commit()
            }

            // Re-run: every function exists, the discovery query short-circuits each iteration.
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.autoCommit = false
                initializer.initializeGlobalStorage(conn)
                conn.commit()

                val present = listExistingFunctionNames(conn)
                for (name in SqlGen.RELL_SYS_FUNCTIONS.keys) {
                    assertTrue(name in present, "Expected function '$name' present after re-run")
                }
            }
        }
    }

    @Test fun concurrentStartupOnSharedSchemaSurvivesDuplicateFunctionRace() {
        val (handle, jdbcUrl) = SqlTestUtils.createTempDbUrl()
        handle.use {
            val nodeCount = 4
            val initializer = RellGlobalStorageInitializer()
            val ready = CountDownLatch(nodeCount)
            val start = CountDownLatch(1)
            val firstFailure = AtomicReference<Throwable>(null)

            val pool = Executors.newFixedThreadPool(nodeCount)
            try {
                repeat(nodeCount) {
                    pool.submit {
                        try {
                            DriverManager.getConnection(jdbcUrl).use { conn ->
                                conn.autoCommit = false
                                ready.countDown()
                                start.await()
                                initializer.initializeGlobalStorage(conn)
                                conn.commit()
                            }
                        } catch (t: Throwable) {
                            firstFailure.compareAndSet(null, t)
                        }
                    }
                }

                assertTrue(ready.await(10, TimeUnit.SECONDS), "workers failed to reach the start barrier")
                start.countDown()
                pool.shutdown()
                assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "concurrent startup timed out")
            } finally {
                pool.shutdownNow()
            }

            firstFailure.get()?.let { fail("Concurrent startup raised: ${it::class.simpleName}: ${it.message}") }

            DriverManager.getConnection(jdbcUrl).use { conn ->
                val present = listExistingFunctionNames(conn)
                val missing = SqlGen.RELL_SYS_FUNCTIONS.keys - present
                assertEquals(emptySet(), missing, "Some Rell sys functions were not created by any racer")
            }
        }
    }

    private fun listExistingFunctionNames(connection: java.sql.Connection): Set<String> {
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
