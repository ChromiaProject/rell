package net.postchain.rell.toolbox.lsp.server.events

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.TimeUnit

class FileEventsBatcherTest {
    private lateinit var batcher: FileEventsBatcher

    @BeforeEach
    fun setUp() {
        batcher = FileEventsBatcher(batchTimeoutMs = 100)
    }

    @AfterEach
    fun tearDown() {
        batcher.shutdown()
    }

    @Test
    fun `empty changes return empty list`() {
        val changes = batcher.nextChanges()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `changes are batched and returned after timeout`() {
        val event1 = FileEvent(URI("file:///test1.rell").toString(), FileChangeType.Created)
        val event2 = FileEvent(URI("file:///test2.rell").toString(), FileChangeType.Created)

        batcher.addChanges(listOf(event1, event2))

        assertThat(batcher.nextChanges()).isEmpty()

        TimeUnit.MILLISECONDS.sleep(150)

        val batchedChanges = batcher.nextChanges()
        assertThat(batchedChanges).isEqualTo(listOf(event1, event2))

        assertThat(batcher.nextChanges()).isEmpty()
    }

    @Test
    fun `multiple batches are handled correctly`() {
        val event1 = FileEvent(URI("file:///test1.rell").toString(), FileChangeType.Created)
        val event2 = FileEvent(URI("file:///test2.rell").toString(), FileChangeType.Created)
        val event3 = FileEvent(URI("file:///test3.rell").toString(), FileChangeType.Created)

        batcher.addChanges(listOf(event1))
        TimeUnit.MILLISECONDS.sleep(150)

        batcher.addChanges(listOf(event2, event3))

        val firstBatch = batcher.nextChanges()
        assertThat(firstBatch).isEqualTo(listOf(event1))

        TimeUnit.MILLISECONDS.sleep(150)

        val secondBatch = batcher.nextChanges()
        assertThat(secondBatch).isEqualTo(listOf(event2, event3))
    }
}
