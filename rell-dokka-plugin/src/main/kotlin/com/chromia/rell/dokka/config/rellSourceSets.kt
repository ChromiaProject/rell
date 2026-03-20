package com.chromia.rell.dokka.config

import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.SourceLinkDefinitionImpl
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeLines

fun rellSourceSets(projectRoot: File, includes: List<File>, sourceLinks: Set<SourceLinkDefinitionImpl>) = listOf(
        createDappMainSourceSet(projectRoot, includes.toSet(), sourceLinks),
        createDappTestSourceSet(projectRoot, includes.toSet(), sourceLinks),
)

private fun transformModuleFile(input: File): File {
    return with(createTempFile(prefix = "rell-dokka-plugin", suffix = "module-docs")) {
        writeLines(
                input.readLines().map {
                    when {
                        it.startsWith("# Dapp") -> "# Module" + it.substringAfter("# Dapp")
                        it.startsWith("# Module") -> "# Package" + it.substringAfter("# Module")
                        else -> it
                    }
                }
        )
        toFile()
    }
}

fun createDappTestSourceSet(
        projectRoot: File,
        includes: Set<File>,
        sourceLinks: Set<SourceLinkDefinitionImpl>,
): DokkaSourceSetImpl {
    return DokkaSourceSetImpl(
            sourceRoots = setOf(projectRoot),
            sourceSetID = DokkaSourceSetID("test", "dapp"),
            displayName = "test",
            analysisPlatform = Platform.js,
            includes = includes.map { transformModuleFile(it) }.toSet(),
            externalDocumentationLinks = setOf(systemLibExternalDocumentationLink),
            dependentSourceSets = setOf(DokkaSourceSetID("main", "dapp")),
            sourceLinks = sourceLinks,
    )
}

fun createDappMainSourceSet(
        projectRoot: File,
        includes: Set<File>,
        sourceLinks: Set<SourceLinkDefinitionImpl>,
): DokkaSourceSetImpl {
    return DokkaSourceSetImpl(
            sourceRoots = setOf(projectRoot),
            sourceSetID = DokkaSourceSetID("main", "dapp"),
            displayName = "dapp",
            analysisPlatform = Platform.wasm,
            includes = includes.map { transformModuleFile(it) }.toSet(),
            externalDocumentationLinks = setOf(systemLibExternalDocumentationLink),
            sourceLinks = sourceLinks,
    )
}