/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.caching

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.sha256
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

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
                cacheFile.deleteIfExists()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete cache file: $cacheFile" }
            }
            null
        }
    }

    internal fun getCacheFile(workspaceFolderUri: URI): Path {
        val path = workspaceFolderUri.path.toString()
        val hash = sha256(path)
        val fileName = "index-$hash.cache"
        return getCacheFolder() / fileName
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

    fun persistOnDiskPeriodically(indexers: Collection<WorkspaceIndexer>, period: Duration): ScheduledFuture<*> =
        scheduledExecutorService.scheduleAtFixedRate(
            /* command = */
            {
                saveWorkspaceIndexers(indexers)
            },
            /* initialDelay = */ 0,
            /* period = */ period.inWholeMilliseconds,
            /* unit = */ TimeUnit.MILLISECONDS,
        )

    fun shutdown() {
        scheduledExecutorService.shutdown()
    }

    fun cleanupOldCaches(timeToLive: Duration = CACHE_FILE_TTL) {
        getCacheFolder().useDirectoryEntries { cacheFiles ->
            for (cacheFile in cacheFiles) {
                deleteOldCacheFile(cacheFile, timeToLive)
            }
        }
    }

    private fun deleteOldCacheFile(cacheFile: Path, timeToLive: Duration) {
        if (cacheFile.extension != "cache") return

        val age = (System.currentTimeMillis() - cacheFile.getLastModifiedTime().toMillis()).milliseconds
        if (age <= timeToLive) return

        try {
            cacheFile.deleteIfExists()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete cache file: $cacheFile" }
        }
    }

    fun getCacheFolder(): Path {
        val xdgCache = System.getenv("XDG_CACHE_HOME")?.let(::Path)
        val fallbackCache = Path(System.getProperty("user.home")) / ".cache"
        val defaultCache = xdgCache ?: fallbackCache
        val lspCacheFolder = defaultCache / RELL_LSP_CACHE_FOLDER_NAME

        if (!lspCacheFolder.exists()) {
            lspCacheFolder.createDirectories()
        }

        return lspCacheFolder
    }

    fun getOldCacheFolder() = Path(System.getProperty("user.home")) / OLD_RELL_LSP_CACHE_FOLDER_NAME

    @OptIn(ExperimentalPathApi::class) fun cleanOldCacheFolder() {
        getOldCacheFolder()
            .takeIf { it.exists() }
            ?.deleteRecursively()
    }

    fun invalidateCaches(): Boolean = try {
        getCacheFolder().toFile().deleteRecursively()
    } catch (e: Exception) {
        logger.warn { "Failed to invalidate caches: ${e.message}" }
        false
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val RELL_LSP_CACHE_FOLDER_NAME = "chromia/rell-language-server/"
        private const val OLD_RELL_LSP_CACHE_FOLDER_NAME = ".chromia/rell-language-server/"
        private val CACHE_FILE_TTL = 30.days
    }
}
