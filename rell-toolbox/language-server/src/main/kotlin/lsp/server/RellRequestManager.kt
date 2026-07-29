/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@Suppress("UNUSED_PARAMETER")
internal class RellRequestManager {
    private val logger = KotlinLogging.logger {}
    private val parallelExecutorService = Executors.newCachedThreadPool(RequestManagerThreadFactory())
    private val queue = Executors.newSingleThreadExecutor(RequestManagerThreadFactory())

    /**
     * Reads wait for this before running. Open by default, so a request manager that nobody gates
     * serves reads immediately rather than hanging on a gate that is never opened.
     */
    @Volatile
    private var readGate: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)

    /**
     * Defer reads until [gate] completes. Reads run in parallel with writes, so the server arms this
     * with the initial indexing: without it a request arriving mid-index would answer from a
     * half-built index. Pass a gate that completes on failure too, or reads never run.
     */
    fun blockReadsUntil(gate: CompletableFuture<Unit>) {
        readGate = gate
    }

    @Synchronized
    fun <V> runRead(work: () -> V): CompletableFuture<V> =
        readGate.thenApplyAsync({ work() }, parallelExecutorService).whenComplete(::logThrowable)

    @Synchronized
    fun <V> runWrite(work: () -> V): CompletableFuture<V> =
        CompletableFuture.supplyAsync(work, queue).whenComplete(::logThrowable)

    private fun <V> logThrowable(result: V, exception: Throwable?) {
        if (exception != null) {
            logger.error(exception.cause) { "Error during request" }
        }
    }

    fun shutdown() {
        parallelExecutorService.shutdown()
        queue.shutdown()
    }
}

internal class RequestManagerThreadFactory : ThreadFactory {
    private val defaultFactory = Executors.defaultThreadFactory()

    override fun newThread(runnable: Runnable): Thread = defaultFactory.newThread(runnable).apply {
        this.isDaemon = true
        this.name = "RequestManager-Queue-${threadId()}"
    }
}
