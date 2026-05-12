/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.chromia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.chromia.model.ChromiaModel
import net.postchain.rell.toolbox.chromia.model.parseModel
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

class ChromiaModelProvider(private val workspaceRootUri: URI?) {

    private var chromiaModelCache: ChromiaModel? = null

    fun resolveIgnoreReportingUris(sourceDirUri: URI): Set<URI> {
        try {
            val model = getChromiaModel() ?: return emptySet()
            return model.libs.keys.map { libName -> sourceDirUri.resolve("lib/$libName/") }.toSet()
        } catch (_: Exception) {
            // Chromia model file might have syntax errors causing exception.
            // In this case, we set resolve ignore reporting URIs to empty set.
            return emptySet()
        }
    }

    fun getRellLanguageVersion(): String {
        val model = getChromiaModel()
        return model?.compile?.rellVersion ?: DEFAULT_CHROMIA_MODEL_RELL_VERSION
    }

    fun getChromiaModel(): ChromiaModel? {
        chromiaModelCache = chromiaModelCache ?: loadChromiaModel()
        return chromiaModelCache
    }

    fun loadChromiaModel(): ChromiaModel? {
        if (workspaceRootUri == null) return null
        val chromiaModelFile = findChromiaModelFile(workspaceRootUri) ?: return null
        return loadChromiaModelFromFile(chromiaModelFile)
    }

    fun updateChromiaModel(workspaceRootUri: URI?, chromiaModel: ChromiaModel?) {
        if (workspaceRootUri == null) return
        chromiaModelCache = chromiaModel
    }

    private fun findChromiaModelFile(workspaceRootUri: URI?): Path? {
        if (workspaceRootUri == null) return null
        val workspaceFolder = workspaceRootUri.toPath()

        val directPath = workspaceFolder / "chromia.yml"
        val rellPath = workspaceFolder / "rell/chromia.yml"

        return when {
            directPath.isRegularFile() -> directPath
            rellPath.isRegularFile() -> rellPath
            else -> null
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        const val DEFAULT_CHROMIA_MODEL_FILENAME = "chromia.yml"
        const val DEFAULT_CHROMIA_MODEL_RELL_VERSION = "0.14.5"

        fun loadChromiaModelFromFile(chromiaModelFile: Path): ChromiaModel? {
            return try {
                parseModel(chromiaModelFile)
            } catch (@Suppress("SwallowedException") e: Exception) {
                logger.warn(e) { "Failed to parse Chromia model file: $chromiaModelFile" }
                null
            }
        }
    }
}
