package net.postchain.rell.toolbox.lsp.caching

import io.fury.Fury
import io.fury.config.Language
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.indexer.calculateChecksum
import net.postchain.rell.toolbox.core.indexer.createLocationInfo
import net.postchain.rell.toolbox.core.indexer.sha256
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class RellIndexCachingService {

    private val fury = Fury.builder().withLanguage(Language.JAVA)
        .requireClassRegistration(false)
        .withRefTracking(true)
        .withCodegen(false)
        .buildThreadSafeFury()

    fun getWorkspaceIndexer(workspaceFolderUri: URI): WorkspaceIndexer? {
        val cacheFile = getCacheFile(workspaceFolderUri)
        if (!cacheFile.exists()) {
            return null
        }
        var deserialized: List<SerializableResource>? = null
        try {
            val indexAsBytes = cacheFile.readBytes()
            deserialized = deserialize(indexAsBytes)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to deserialize index cache file: $cacheFile" }
            try {
                cacheFile.delete()
            } catch (e: Throwable) {
                logger.error(e) { "Failed to delete cache file: $cacheFile" }
            }
            return null
        }
        val fileUriResourceMap = toResources(deserialized)
        val indexer = WorkspaceIndexer(workspaceFolderUri)
        indexer.fileUriResourceMap = ConcurrentHashMap(fileUriResourceMap)
        return indexer
    }

    internal fun deserialize(indexAsBytes: ByteArray) = fury.deserialize(indexAsBytes) as List<SerializableResource>

    private fun toResources(serializedResources: List<SerializableResource>) =
        serializedResources.associate {
            val resource = Resource(
                it.parseTree,
                it.moduleInfo,
                it.fileUri,
                it.workspaceUri,
                it.ast,
                it.syntaxErrors,
                it.semanticErrors,
                it.symbolInfos,
                createLocationInfo(it.symbolInfos),
                it.checksum
            )
            it.fileUri to resource
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
            val cacheFile = getCacheFile(indexer.workspaceUri)
            val serializableData = extractSerializableData(indexer)
            try {
                val indexAsBytes = serialize(serializableData)
                cacheFile.writeBytes(indexAsBytes)
            } catch (e: Throwable) {
                logger.error { "Failed to persist workspace index: ${indexer.workspaceUri} ${e.message}" }
            }
        }
    }

    internal fun serialize(serializableData: List<SerializableResource>): ByteArray = fury.serialize(serializableData)

    fun persistOnDiskPeriodically(indexers: Collection<WorkspaceIndexer>, period: Duration) {
        Timer("index-cache-persister").scheduleAtFixedRate(0, period.inWholeMilliseconds) {
            saveWorkspaceIndexers(indexers)
        }
    }

    fun cleanupOldCaches(timeToLive: Duration = CACHE_FILE_TTL) {
        val cacheFolder = getCacheFolder()
        val cacheFiles = cacheFolder.listFiles()
        if (cacheFiles != null) {
            for (cacheFile in cacheFiles) {
                if (cacheFile.extension == "cache") {
                    val lastModified = cacheFile.lastModified()
                    val now = System.currentTimeMillis()
                    val age = now - lastModified
                    if (age > timeToLive.inWholeMilliseconds) {
                        try {
                            cacheFile.delete()
                        } catch (e: Throwable) {
                            logger.error(e) { "Failed to delete cache file: $cacheFile" }
                        }
                    }
                }
            }
        }
    }

    private fun extractSerializableData(indexer: WorkspaceIndexer): List<SerializableResource> {
        val serializableData = indexer.fileUriResourceMap.map { (_, resource) ->
                val checksum = calculateChecksum(resource.fileUri)
                SerializableResource(
                    resource.parseTree,
                    resource.moduleInfo,
                    resource.fileUri,
                    resource.workspaceUri,
                    resource.ast,
                    resource.syntaxErrors,
                    resource.semanticErrors,
                    resource.symbolInfos,
                    checksum)
        }
        return serializableData
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
        } catch (e: Throwable) {
            logger.error { "Failed to invalidate caches: ${e.message}" }
            false
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val RELL_LSP_CACHE_FOLDER_NAME = ".chromia/rell-language-server/cache/"
        private val CACHE_FILE_TTL = 30.days
    }
}