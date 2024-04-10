
/*
 * Copyright 2014-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package com.chromia.rell.dokka.moduledocs

import com.chromia.rell.dokka.doc.RellMarkdownParser
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.utilities.DokkaLogger

/**
 * Copied from https://github.com/Kotlin/dokka/blob/1.9.10/subprojects/analysis-kotlin-symbols/src/main/kotlin/org/jetbrains/dokka/analysis/kotlin/symbols/kdoc/moduledocs/ModuleAndPackageDocumentationParsingContext.kt
 */
internal fun interface ModuleAndPackageDocumentationParsingContext {
    fun markdownParserFor(fragment: ModuleAndPackageDocumentationFragment, location: String): RellMarkdownParser
}

internal fun ModuleAndPackageDocumentationParsingContext.parse(
        fragment: ModuleAndPackageDocumentationFragment
): DocumentationNode {
    return markdownParserFor(fragment, fragment.source.sourceDescription).parse(fragment.documentation)
}

// Modified function
internal fun ModuleAndPackageDocumentationParsingContext(
        logger: DokkaLogger,
        sourceSet: DokkaConfiguration.DokkaSourceSet? = null
) = ModuleAndPackageDocumentationParsingContext { fragment, sourceLocation ->
    RellMarkdownParser(sourceLocation)
}