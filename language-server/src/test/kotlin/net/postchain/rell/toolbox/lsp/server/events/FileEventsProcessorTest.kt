package net.postchain.rell.toolbox.lsp.server.events

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import net.postchain.rell.toolbox.lsp.server.FileEventsBatcher
import net.postchain.rell.toolbox.lsp.server.FileEventsProcessor

class FileEventsProcessorTest {
    private lateinit var batcher: FileEventsBatcher
    private lateinit var processor: FileEventsProcessor
    private val processedEvents = CopyOnWriteArrayList<List<FileEvent>>()

    @BeforeEach
    fun setUp() {
        batcher = FileEventsBatcher(batchTimeoutMs = 100)
        processor = FileEventsProcessor(batcher) { events ->
            processedEvents.add(events)
        }
    }

    @AfterEach
    fun tearDown() {
        processor.shutdown()
        batcher.shutdown()
        processedEvents.clear()
    }

    @Test
    fun `processor handles events from batcher`() {
        val event1 = FileEvent(URI("file:///test1.rell").toString(), FileChangeType.Created)
        val event2 = FileEvent(URI("file:///test2.rell").toString(), FileChangeType.Changed)

        batcher.addChanges(listOf(event1, event2))

        TimeUnit.MILLISECONDS.sleep(300)

        assertThat(processedEvents).isEqualTo(listOf(listOf(event1, event2)))
    }

    @Test
    fun `processor handles multiple batches`() {
        val event1 = FileEvent(URI("file:///test1.rell").toString(), FileChangeType.Created)
        val event2 = FileEvent(URI("file:///test2.rell").toString(), FileChangeType.Changed)
        val event3 = FileEvent(URI("file:///test3.rell").toString(), FileChangeType.Deleted)

        batcher.addChanges(listOf(event1))
        TimeUnit.MILLISECONDS.sleep(300)

        batcher.addChanges(listOf(event2, event3))
        TimeUnit.MILLISECONDS.sleep(300)

        assertThat(processedEvents).isEqualTo(
            listOf(
                listOf(event1),
                listOf(event2, event3)
            )
        )
    }

    @Test
    fun `processor ignores empty batches`() {
        TimeUnit.MILLISECONDS.sleep(300)

        assertThat(processedEvents).isEqualTo(emptyList<List<FileEvent>>())
    }
}
