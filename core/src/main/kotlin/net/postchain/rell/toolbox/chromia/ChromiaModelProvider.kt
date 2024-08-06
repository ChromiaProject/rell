package net.postchain.rell.toolbox.chromia

import com.chromia.cli.model.ChromiaModel
import com.chromia.cli.model.parseModel
import java.io.File
import java.net.URI

class ChromiaModelProvider {

    fun resolveIgnoreReportingUris(workspaceRootUri: URI?, sourceDirUri: URI): Set<URI> {
        if (workspaceRootUri == null) return emptySet()
        try {
            val model = getChromiaModel(workspaceRootUri) ?: return emptySet()
            return model.libs.keys.map { libName -> sourceDirUri.resolve("lib/$libName/") }.toSet()
        } catch (e: Exception) {
            return emptySet()
        }
    }

    private fun getChromiaModel(workspaceUri: URI): ChromiaModel? {
        val modelFile = findChromiaModelFile(workspaceUri) ?: return null
        return parseModel(modelFile)
    }

    private fun findChromiaModelFile(workspaceUri: URI): File? {
        val workspaceFolder = File(workspaceUri)

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
    }
}
