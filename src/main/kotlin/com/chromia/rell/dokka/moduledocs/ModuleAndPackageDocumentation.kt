package com.chromia.rell.dokka.moduledocs

import org.jetbrains.dokka.model.doc.DocumentationNode
import java.io.File

/**
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/subprojects/analysis-kotlin-symbols/src/main/kotlin/org/jetbrains/dokka/analysis/kotlin/symbols/kdoc/moduledocs/ModuleAndPackageDocumentation.kt
 */
internal data class ModuleAndPackageDocumentation(
        val name: String,
        val classifier: Classifier,
        val documentation: DocumentationNode
) {
    enum class Classifier { Module, Package }
}
internal abstract class ModuleAndPackageDocumentationSource {
    abstract val sourceDescription: String
    abstract val documentation: String
    override fun toString(): String = sourceDescription
}

internal data class ModuleAndPackageDocumentationFragment(
        val name: String,
        val classifier: ModuleAndPackageDocumentation.Classifier,
        val documentation: String,
        val source: ModuleAndPackageDocumentationSource
)
internal data class ModuleAndPackageDocumentationFile(private val file: File) : ModuleAndPackageDocumentationSource() {
    override val sourceDescription: String = file.path
    override val documentation: String by lazy(LazyThreadSafetyMode.PUBLICATION) { file.readText() }
}