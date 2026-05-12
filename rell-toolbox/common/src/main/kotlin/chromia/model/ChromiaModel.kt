/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.chromia.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.postchain.common.types.WrappedByteArray
import java.nio.file.Path
import kotlin.io.path.readText

const val DEFAULT_CHROMIA_MODEL_RELL_VERSION = "0.14.5"

data class ChromiaModel(
    val compile: CompileSection = CompileSection(),
    val libs: Map<String, RellLibraryModel> = emptyMap(),
)

data class CompileSection(
    val rellVersion: String? = null,
    val source: Path? = null,
)

data class RellLibraryModel(
    val registry: String? = null,
    val tagOrBranch: String? = null,
    val path: String? = null,
    val insecure: Boolean = false,
    val rid: WrappedByteArray? = null,
    val brid: String? = null,
    val version: String? = null,
) {
    fun format(name: String): String = buildString {
        append("\n  $name:")
        registry?.let { append("\n    registry: $it") }
        tagOrBranch?.let { append("\n    tagOrBranch: $it") }
        version?.let { append("\n    version: $it") }
        path?.let { append("\n    path: $it") }
        if (insecure) append("\n    insecure: true")
        rid?.let { append("\n    rid: x\"$it\"") }
        brid?.let { append("\n    brid: x\"$it\"") }
    }
}

private val yamlMapper = ObjectMapper(YAMLFactory())

fun parseModel(src: Path): ChromiaModel {
    val text = src.readText(Charsets.UTF_8)

    val raw: Map<*, *> = if (text.isBlank()) emptyMap<Any?, Any?>()
    else yamlMapper.readValue(text, Map::class.java) ?: emptyMap()

    val baseDir = src.parent

    return ChromiaModel(
        compile = parseCompile(raw["compile"], baseDir),
        libs = parseLibs(raw["libs"]),
    )
}

private fun parseCompile(node: Any?, baseDir: Path?): CompileSection {
    val map = node as? Map<*, *> ?: return CompileSection()
    val rellVersion = (map["rellVersion"] as? String)?.takeIf { it.isNotBlank() }
    val sourceRaw = (map["source"] as? String)?.takeIf { it.isNotBlank() }
    val source = if (baseDir != null && sourceRaw != null) baseDir.resolve(sourceRaw) else null
    return CompileSection(rellVersion = rellVersion, source = source)
}

private fun parseLibs(node: Any?): Map<String, RellLibraryModel> {
    val map = node as? Map<*, *> ?: return emptyMap()
    val result = LinkedHashMap<String, RellLibraryModel>(map.size)
    for ((rawName, rawValue) in map) {
        val name = rawName as? String ?: continue
        val entry = rawValue as? Map<*, *>
        result[name] = if (entry == null) RellLibraryModel() else RellLibraryModel(
            registry = entry["registry"] as? String,
            tagOrBranch = entry["tagOrBranch"] as? String,
            path = entry["path"] as? String,
            insecure = entry["insecure"] as? Boolean ?: false,
            version = entry["version"] as? String,
        )
    }
    return result
}
