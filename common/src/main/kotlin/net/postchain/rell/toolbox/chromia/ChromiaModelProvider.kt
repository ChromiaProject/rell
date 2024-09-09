package net.postchain.rell.toolbox.chromia

import com.chromia.cli.model.ChromiaModel
import com.chromia.cli.model.parseModel
import java.io.File
import java.net.URI

class ChromiaModelProvider(private val workspaceRootUri: URI?) {

    private var chromiaModelCache: ChromiaModel? = null

    fun resolveIgnoreReportingUris(sourceDirUri: URI): Set<URI> {
        try {
            val model = getChromiaModel() ?: return emptySet()
            return model.libs.keys.map { libName -> sourceDirUri.resolve("lib/$libName/") }.toSet()
        } catch (@Suppress("SwallowedException") e: Exception) {
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
        return try {
            parseModel(chromiaModelFile)
        } catch (@Suppress("SwallowedException") e: Exception) {
            null
        }
    }

    fun updateChromiaModel(workspaceRootUri: URI?, chromiaModel: ChromiaModel?) {
        if (workspaceRootUri == null) return
        chromiaModelCache = chromiaModel
    }

    private fun findChromiaModelFile(workspaceRootUri: URI?): File? {
        if (workspaceRootUri == null) return null
        val workspaceFolder = File(workspaceRootUri)

        val directPath = workspaceFolder.resolve("chromia.yml")
        val rellPath = workspaceFolder.resolve("rell/chromia.yml")

        return when {
            directPath.isFile() -> directPath
            rellPath.isFile() -> rellPath
            else -> null
        }
    }

    companion object {
        const val DEFAULT_CHROMIA_MODEL_FILENAME = "chromia.yml"
        const val DEFAULT_CHROMIA_MODEL_RELL_VERSION = "0.13.14"
    }
}
