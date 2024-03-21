package com.chromia.rell.dokka.config

import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.Platform
import java.io.File
import kotlin.io.path.writeLines

fun rellSourceSets(projectRoot: File, includes: List<File>) = listOf(DokkaSourceSetImpl(
        sourceRoots = setOf(projectRoot),
        sourceSetID = DokkaSourceSetID("main", "dapp"),
        displayName = "dapp",
        analysisPlatform = Platform.wasm,
        includes = includes.map { tranformModuleFile(it) }.toSet(),
        externalDocumentationLinks = setOf(systemLibExternalDocumentationLink)
))

private fun tranformModuleFile(input: File): File {
    return with(kotlin.io.path.createTempFile(prefix = "rell-dokka-plugin", suffix = "module-docs")) {
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
