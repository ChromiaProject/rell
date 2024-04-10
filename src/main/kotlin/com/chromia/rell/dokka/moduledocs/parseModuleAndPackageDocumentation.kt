
/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package com.chromia.rell.dokka.moduledocs

/**
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/subprojects/analysis-kotlin-symbols/src/main/kotlin/org/jetbrains/dokka/analysis/kotlin/symbols/kdoc/moduledocs/parseModuleAndPackageDocumentation.kt
 */
internal fun parseModuleAndPackageDocumentation(
        context: ModuleAndPackageDocumentationParsingContext,
        fragment: ModuleAndPackageDocumentationFragment
): ModuleAndPackageDocumentation {
    return ModuleAndPackageDocumentation(
            name = fragment.name,
            classifier = fragment.classifier,
            documentation = context.parse(fragment)
    )
}