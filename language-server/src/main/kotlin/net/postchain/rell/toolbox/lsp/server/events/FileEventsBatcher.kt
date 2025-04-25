package net.postchain.rell.toolbox.lsp.server

import org.eclipse.lsp4j.FileEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileEventsBatcher(
    private val batchTimeoutMs: Long = 2000,
) {
    private val pendingChanges = ConcurrentLinkedQueue<FileEvent>()
    private val batchedChanges = ConcurrentLinkedQueue<FileEvent>()
    private val batchLock = ReentrantLock()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        val thread = Thread(r, "file-events-batcher")
        thread.isDaemon = true
        thread
    }
    private var scheduledFlush = false

    fun addChanges(changes: List<FileEvent>) {
        if (changes.isEmpty()) {
            return
        }

        pendingChanges.addAll(changes)

        batchLock.withLock {
            if (!scheduledFlush) {
                scheduledFlush = true
                scheduler.schedule(this::flushPendingChanges, batchTimeoutMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun nextChanges(): List<FileEvent> {
        val result = mutableListOf<FileEvent>()
        var event = batchedChanges.poll()
        while (event != null) {
            result.add(event)
            event = batchedChanges.poll()
        }
        return result
    }

    private fun flushPendingChanges() {
        batchLock.withLock {
            scheduledFlush = false

            var event = pendingChanges.poll()
            while (event != null) {
                batchedChanges.add(event)
                event = pendingChanges.poll()
            }
        }
    }

    fun shutdown() {
        scheduler.shutdown()
    }
}
