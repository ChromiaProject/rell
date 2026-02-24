/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.rell.gtx.testutils.BaseGtxTest
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

private const val joinTimeoutMs = 30_000L

class GtxCancellationTest: BaseGtxTest() {
    @Test fun testSqlCancellationExceptionNotSuppressedOrWrapped() {
        def("""
            function f(): unit {
                _test.sleep(10000);
            }

            query q_direct(): integer {
                f();
                return 0;
            }

            query q_try(): boolean {
                return try_call(f(*));
            }
        """.trimIndent())

        tstCtx.sqlMgr().access { sqlExec ->
            sqlExec.execute("SET statement_timeout = 1000")
        }

        try {
            try {
                tst.callQuery("q_direct", mapOf())
                fail("Expected SQLException")
            } catch (e: SQLException) {
                assertEquals("57014", e.sqlState)
            }

            try {
                tst.callQuery("q_try", mapOf())
                fail("Expected SQLException")
            } catch (e: SQLException) {
                assertEquals("57014", e.sqlState)
            }
        } finally {
            tstCtx.sqlMgr().access { sqlExec ->
                sqlExec.execute("SET statement_timeout = 0")
            }
        }
    }

    @Test fun testInterruptBeforeSqlExecutionIsPropagated() {
        def("""
            entity foo { x: integer; }
            query warmup() = 0;
            query q_interrupt_before_sql() = foo @* {};
        """.trimIndent())

        val err = AtomicReference<Throwable?>()

        val t = Thread {
            // Warm up context (initializes logging/DB) before setting the interrupt flag
            tst.callQuery("warmup", mapOf())
            Thread.currentThread().interrupt() // set the interrupted flag before starting SQL
            try {
                tst.callQuery("q_interrupt_before_sql", mapOf())
            } catch (e: Throwable) {
                err.set(e)
            }
        }

        t.start()
        t.join(joinTimeoutMs)

        if (t.isAlive) {
            t.interrupt()
            fail("Query thread did not stop after interruption before SQL execution")
        }

        val e = assertNotNull(err.get(), "Expected exception")
        assertIs<InterruptedException>(e)
    }

    @Test fun testThreadInterruptPropagatesThroughTryCall() {
        def("""
            query warmup() = 0;
            function busy(): unit {
                while (true) {}
            }

            query q(): boolean {
                return try_call(busy(*));
            }
        """.trimIndent())

        val err = AtomicReference<Throwable?>()

        val t = Thread {
            // Warm up context before triggering interruption
            tst.callQuery("warmup", mapOf())
            Thread.currentThread().interrupt()
            try {
                tst.callQuery("q", mapOf())
            } catch (e: Throwable) {
                err.set(e)
            }
        }

        t.start()
        t.join(joinTimeoutMs)

        if (t.isAlive) {
            t.interrupt()
            fail("Query thread did not stop after interruption")
        }

        val e = assertNotNull(err.get(), "Expected exception")
        assertIs<InterruptedException>(e)
    }
}
