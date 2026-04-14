
/*
 * Copyright 2014-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package com.chromia.rell.dokka.moduledocs

import org.jetbrains.dokka.model.doc.DocumentationNode

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