package net.postchain.rell.toolbox.lsp.server


import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory


class RellRequestManager {
    private val logger = KotlinLogging.logger {}
    private val parallelExecutorService = Executors.newCachedThreadPool(RequestManagerThreadFactory())
    private val queue = Executors.newSingleThreadExecutor(RequestManagerThreadFactory())

    @Synchronized
    fun <V> runRead(work: () -> V): CompletableFuture<V> {
        return CompletableFuture.supplyAsync(work, parallelExecutorService).whenComplete(::logThrowable)
    }

    @Synchronized
    fun <V> runWrite(work: () -> V): CompletableFuture<V> {
        return CompletableFuture.supplyAsync(work, queue).whenComplete(::logThrowable)
    }

    private fun <V> logThrowable(result: V, exception: Throwable?) {
        if (exception != null) {
            logger.error(exception.cause) { "Error during request" }
        }
    }
}

class RequestManagerThreadFactory : ThreadFactory {
    private val defaultFactory = Executors.defaultThreadFactory()

    override fun newThread(runnable: Runnable): Thread {
        val thread = defaultFactory.newThread(runnable)
        thread.isDaemon = true
        thread.name = "RequestManager-Queue-${thread.id}"
        return thread
    }
}