package net.postchain.rell.toolbox.lsp.caching

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import net.postchain.rell.toolbox.core.indexer.RellResourceFactory
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class SourceFile(val filePath: String, val fileContent: String)

class RellIndexCachingServiceTest {

    lateinit var indexSerializer: RellIndexSerializer
    lateinit var cachingService: RellIndexCachingService
    lateinit var workspaceFolderUri: URI
    lateinit var dummyWorkspaceIndexer: WorkspaceIndexer
    lateinit var cacheFile: File

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        indexSerializer = spyk<RellIndexSerializer>()
        cachingService = spyk(RellIndexCachingService(indexSerializer))
        workspaceFolderUri = Files.createDirectories(tempDir.resolve("src")).toUri()
        val sourceFiles = listOf(
            SourceFile("dummy.rell", "module; function dummy() {}"),
            SourceFile("dummy2.rell", "module; function dummy2() {}")
        )
        dummyWorkspaceIndexer = createDummyWorkspaceIndexer(workspaceFolderUri, sourceFiles)
        cacheFile = tempDir.resolve("dummy.cache").toFile()
        every { cachingService.getCacheFile(workspaceFolderUri) } returns cacheFile
    }

    @Test
    fun `Should return null when cache file does not exist`() {
        val workspaceFolderUri = URI("file:///notExistingWorkspaceFolder")
        val result = cachingService.getWorkspaceIndexer(workspaceFolderUri)
        assertThat(result).isNull()
    }

    @Test
    fun `Should return WorkspaceIndexer when cache file exists`() {
        cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer))
        val result = cachingService.getWorkspaceIndexer(workspaceFolderUri)
        assertThat(result).isNotNull()
    }

    @Test
    fun `Should delete cache file when deserialization fails`() {
        every { indexSerializer.deserializeAsResourceMap(any())} throws IOException()
        cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer))

        assertThat(cacheFile.exists()).isTrue()
        val result = cachingService.getWorkspaceIndexer(workspaceFolderUri)
        assertThat(cacheFile.exists()).isFalse()

        assertThat(result).isNull()
    }

    @Test
    fun `Should return null when reading from cache file fails`() {
        withMockedStatic("kotlin.io.FilesKt__FileReadWriteKt") {
            val mockFile = mockk<File>()
            every { cachingService.getCacheFile(workspaceFolderUri) } returns mockFile
            every { mockFile.exists() } returns true
            every { mockFile.readBytes() } throws IOException()

            val result = cachingService.getWorkspaceIndexer(workspaceFolderUri)
            assertThat(result).isNull()
        }
    }

    private fun withMockedStatic(staticName: String, block: () -> Unit) {
        mockkStatic(staticName)
        block()
        unmockkStatic(staticName)
    }

    @Test
    fun `Should not throw when writing to disk fails`() {
        withMockedStatic("kotlin.io.FilesKt__FileReadWriteKt") {
            val mockFile = mockk<File>()
            every { cachingService.getCacheFile(workspaceFolderUri) } returns mockFile
            every { indexSerializer.serializeAsBytes(any()) } returns byteArrayOf(1, 2, 3)
            every { mockFile.writeBytes(any()) } throws IOException()
            cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer))
        }
    }

    @Test
    fun `Should persist index caches`() {
        cachingService.persistOnDiskPeriodically(listOf(dummyWorkspaceIndexer), 1.minutes)

        Thread.sleep(100)

        verify(exactly = 1) { cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer)) }
    }

    @Test
    fun `Should cleanup old caches`() {
        cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer))
        every { cachingService.getCacheFolder() } returns cacheFile.parentFile
        assertThat(cacheFile.exists()).isTrue()
        Thread.sleep(100)
        cachingService.cleanupOldCaches(1.milliseconds)
        assertThat(cacheFile.exists()).isFalse()
    }

    @Test
    fun `Should invalidate caches`() {
        cachingService.saveWorkspaceIndexers(listOf(dummyWorkspaceIndexer))
        every { cachingService.getCacheFolder() } returns cacheFile.parentFile
        assertThat(cacheFile.exists()).isTrue()
        val result = cachingService.invalidateCaches()
        assertThat(cacheFile.exists()).isFalse()
        assertThat(result).isTrue()
    }

    @Test
    fun `Should skip persisting indexers with single files`() {
        val singleFileIndexer = createDummyWorkspaceIndexer(workspaceFolderUri, listOf(SourceFile("dummy.rell", "module; function dummy() {}")))
        every { cachingService.getCacheFolder() } returns cacheFile.parentFile

        cachingService.saveWorkspaceIndexers(listOf(singleFileIndexer))

        assertThat(cacheFile.exists()).isFalse()
    }

    private fun createDummyWorkspaceIndexer(workspaceFolderUri: URI, sourceFiles: List<SourceFile>): WorkspaceIndexer {
        val resourceFactory = RellResourceFactory(workspaceFolderUri, AntlrRellParser())
        val indexer = WorkspaceIndexer(workspaceFolderUri)

        sourceFiles.forEach {
            val fileUri = workspaceFolderUri.resolve(it.filePath)
            File(fileUri).writeText(it.fileContent)
            indexer.fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri)
        }

        return indexer
    }
}
