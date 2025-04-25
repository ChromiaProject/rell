package net.postchain.rell.toolbox.lsp.server

import org.eclipse.lsp4j.FileEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FileEventsProcessor(
    private val fileEventsBatcher: FileEventsBatcher,
    private val eventProcessor: (List<FileEvent>) -> Unit,
) {
    private val processorScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        val thread = Thread(r, "file-events-processor")
        thread.isDaemon = true
        thread
    }

    init {
        processorScheduler.scheduleWithFixedDelay(this::processEvents, 100, 100, TimeUnit.MILLISECONDS)
    }

    private fun processEvents() {
        val changes = fileEventsBatcher.nextChanges()
        if (changes.isNotEmpty()) {
            eventProcessor(changes)
        }
    }

    fun shutdown() {
        processorScheduler.shutdown()
    }
}
