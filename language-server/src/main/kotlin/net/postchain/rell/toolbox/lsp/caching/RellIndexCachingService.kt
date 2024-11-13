package net.postchain.rell.toolbox.lsp.caching

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.sha256
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class RellIndexCachingService(val indexSerializer: RellIndexSerializer) {

    private val scheduledExecutorService = Executors.newScheduledThreadPool(1, IndexPersisterThreadFactory())

    fun getWorkspaceIndexer(workspaceFolderUri: URI): WorkspaceIndexer? {
        val cacheFile = getCacheFile(workspaceFolderUri)
        if (!cacheFile.exists()) {
            return null
        }

        return try {
            val indexAsBytes = cacheFile.readBytes()
            indexSerializer.deserializeAsWorkspaceIndexer(indexAsBytes)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to deserialize index cache file: $cacheFile" }
            try {
                cacheFile.delete()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete cache file: $cacheFile" }
            }
            null
        }
    }

    internal fun getCacheFile(workspaceFolderUri: URI): File {
        val path = workspaceFolderUri.path.toString()
        val hash = sha256(path)
        val fileName = "index-$hash.cache"
        return getCacheFolder().resolve(fileName)
    }

    internal fun saveWorkspaceIndexers(indexers: Collection<WorkspaceIndexer>) {
        for (indexer in indexers) {
            if (indexer.fileUriResourceMap.size <= 1) {
                continue
            }

            try {
                val cacheFile = getCacheFile(indexer.workspaceUri)
                val indexAsBytes = indexSerializer.serializeAsBytes(indexer)
                cacheFile.writeBytes(indexAsBytes)
            } catch (e: Exception) {
                logger.warn { "Failed to persist workspace index: ${indexer.workspaceUri} ${e.message}" }
            }
        }
    }

    fun persistOnDiskPeriodically(indexers: Collection<WorkspaceIndexer>, period: Duration) {
        scheduledExecutorService.scheduleAtFixedRate({
            saveWorkspaceIndexers(indexers)
        }, 0, period.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        scheduledExecutorService.shutdown()
    }

    fun cleanupOldCaches(timeToLive: Duration = CACHE_FILE_TTL) {
        val cacheFolder = getCacheFolder()
        val cacheFiles = cacheFolder.listFiles()
        cacheFiles?.forEach { cacheFile ->
            deleteOldCacheFile(cacheFile, timeToLive)
        }
    }

    private fun deleteOldCacheFile(cacheFile: File, timeToLive: Duration) {
        if (cacheFile.extension == "cache") {
            val lastModified = cacheFile.lastModified()
            val now = System.currentTimeMillis()
            val age = now - lastModified
            if (age > timeToLive.inWholeMilliseconds) {
                try {
                    cacheFile.delete()
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to delete cache file: $cacheFile" }
                }
            }
        }
    }

    fun getCacheFolder(): File {
        val userHomeFolder = File(System.getProperty("user.home"))
        val lspCacheFolder = userHomeFolder.resolve(RELL_LSP_CACHE_FOLDER_NAME)
        if (!lspCacheFolder.exists()) {
            Files.createDirectories(lspCacheFolder.toPath())
        }
        return lspCacheFolder
    }

    fun invalidateCaches(): Boolean {
        return try {
            getCacheFolder().deleteRecursively()
        } catch (e: Exception) {
            logger.warn { "Failed to invalidate caches: ${e.message}" }
            false
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val RELL_LSP_CACHE_FOLDER_NAME = ".chromia/rell-language-server/cache/"
        private val CACHE_FILE_TTL = 30.days
    }
}
