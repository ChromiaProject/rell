/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class RellRequestManagerTest {

    @Test
    fun `Task submitted using runRead gets executed`() {
        val requestManager = RellRequestManager()
        val result = requestManager.runRead { 2 + 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun `Task submitted using runWrite gets executed`() {
        val requestManager = RellRequestManager()
        val result = requestManager.runWrite { 2 + 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun `Reads wait for the gate and run once it opens`() {
        val requestManager = RellRequestManager()
        val gate = CompletableFuture<Unit>()
        requestManager.blockReadsUntil(gate)

        val result = requestManager.runRead { 2 + 2 }
        assertThat(result.isDone).isFalse()

        gate.complete(Unit)
        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(4)
    }

    @Test
    fun `Reads fail rather than hang when the gate fails`() {
        val requestManager = RellRequestManager()
        val gate = CompletableFuture<Unit>()
        requestManager.blockReadsUntil(gate)
        val result = requestManager.runRead { 2 + 2 }

        gate.completeExceptionally(IllegalStateException("indexing failed"))

        assertThrows<ExecutionException> { result.get(10, TimeUnit.SECONDS) }
    }
}
